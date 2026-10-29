package com.freechat.companion

import com.freechat.data.AppJson
import com.freechat.model.CharacterProfile
import com.freechat.model.Conversation
import com.freechat.model.ChatMode
import com.freechat.model.Message
import com.freechat.model.Role
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.sql.DriverManager
import java.util.zip.GZIPOutputStream

/**
 * M2 大脑烟测：临时 SQLite（服务端 DDL）+ 假模型，验证
 * 一轮生成 = 用户消息落库 → 共享机制出回复 → 分条落库 → 记忆摘录/氛围写回，
 * 且写语义（rev 单调 +1 / users.seq 发新游标 / last_write）与 sync.js 一致。
 */
class CompanionBrainSmokeTest {

    private fun gzip(text: String): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(text.toByteArray(Charsets.UTF_8)) }
        return out.toByteArray()
    }

    private fun setupDb(dir: File, profile: CharacterProfile = CharacterProfile(name = "旅人", dialogueMode = 0)): String {
        val path = File(dir, "test.db").absolutePath
        val c = DriverManager.getConnection("jdbc:sqlite:$path")
        try {
            c.createStatement().use { st ->
                st.execute("PRAGMA journal_mode = WAL")
                st.execute("CREATE TABLE users (id TEXT PRIMARY KEY, username TEXT DEFAULT '', seq INTEGER DEFAULT 0, bytes_used INTEGER DEFAULT 0, last_write INTEGER DEFAULT 0)")
                st.execute("""CREATE TABLE ai_usage (
                    id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER DEFAULT 0, day TEXT DEFAULT '',
                    user_id TEXT DEFAULT '', username TEXT DEFAULT '', install_id TEXT DEFAULT '',
                    model TEXT DEFAULT '', upstream TEXT DEFAULT '', path TEXT DEFAULT '',
                    status INTEGER DEFAULT 0, prompt_tokens INTEGER DEFAULT 0, completion_tokens INTEGER DEFAULT 0,
                    ms INTEGER DEFAULT 0, ip TEXT DEFAULT '', note TEXT DEFAULT '')""")
                st.execute("""CREATE TABLE objects (
                    user_id TEXT, kind TEXT, id TEXT, rev INTEGER DEFAULT 0,
                    updated_at INTEGER DEFAULT 0, seq INTEGER DEFAULT 0,
                    deleted INTEGER DEFAULT 0, size_raw INTEGER DEFAULT 0, data BLOB,
                    PRIMARY KEY (user_id, kind, id))""")
                st.execute("INSERT INTO users (id, seq) VALUES ('u1', 10)")
            }
            val conv = Conversation(
                id = "conv-1", title = "测试对话", mode = ChatMode.COMPANION,
                // Fake-completer tests must never issue a real external search.
                characterProfile = profile.copy(enableWebSearch = profile.enableWebSearch ?: false)
            )
            c.prepareStatement("INSERT INTO objects (user_id, kind, id, rev, seq, size_raw, data) VALUES ('u1','conv','conv-1', 3, 10, 100, ?)").use { ps ->
                ps.setBytes(1, gzip(AppJson.gson.toJson(conv)))
                ps.executeUpdate()
            }
            c.prepareStatement("INSERT INTO objects (user_id, kind, id, rev, seq, size_raw, data) VALUES ('u1','msgs','conv-1', 5, 10, 50, ?)").use { ps ->
                ps.setBytes(1, gzip("[]"))
                ps.executeUpdate()
            }
        } finally {
            c.close()
        }
        return path
    }

    private class FakeCompleter : ChatCompleter {
        var calls = 0
        val requests = mutableListOf<List<Map<String, Any?>>>()
        override suspend fun complete(messages: List<Map<String, Any?>>, temperature: Double, extras: Map<String, Any?>): ChatResult {
            calls++
            requests += messages
            // 按提示词内容分流：深度推演 → 四行推演；摘录器 → JSON 草稿；其余（生成）→ 回复
            val prompt = messages.joinToString("\n") { it["content"]?.toString().orEmpty() }
            val text = when {
                prompt.contains("只做推演") ->
                    "1. 只是闲话\n2. 无\n3. 正常应对\n4. 无"
                prompt.contains("记忆摘录器") || prompt.contains("记忆归纳器") ->
                    """{"summary":"用户说下周要一起去看展","kind":"plot","date":"2026-10-11","keywords":["看展","约定"],"mood":"开心","atmosphere":"聊得很热络","warmth":1}"""
                else -> "[情绪:开心]\n好呀\n晚点聊"
            }
            return ChatResult(text, upstreamModel = "up-fake", ms = 5, promptTokens = 100, completionTokens = 20)
        }
    }

    /** 挂起生成（测过期闸门用）：第一枪挂在 [gate] 上，被取消/释放才继续 */
    private class HangingCompleter(val gate: kotlinx.coroutines.CompletableDeferred<Unit>) : ChatCompleter {
        var calls = 0
        override suspend fun complete(messages: List<Map<String, Any?>>, temperature: Double, extras: Map<String, Any?>): ChatResult {
            calls++
            val prompt = messages.joinToString("\n") { it["content"]?.toString().orEmpty() }
            if (prompt.contains("记忆摘录器") || prompt.contains("记忆归纳器") || prompt.contains("只做推演"))
                return ChatResult("""{"summary":"s","kind":"plot","date":"","keywords":[],"mood":"","atmosphere":"","warmth":0}""")
            gate.await()
            return ChatResult("[情绪:开心]\n好呀\n晚点聊")
        }
    }

    private fun boxRaw(db: String, kind: String = "msgs"): String =
        DriverManager.getConnection("jdbc:sqlite:$db").use { c ->
            c.createStatement().use { st ->
                st.executeQuery("SELECT data FROM objects WHERE kind='$kind' AND id='conv-1'").use { rs ->
                    if (rs.next()) java.util.zip.GZIPInputStream(rs.getBinaryStream(1)).readBytes().toString(Charsets.UTF_8) else ""
                }
            }
        }

    @Test
    fun oneRoundWritesMessagesMemoryAndAtmosphereWithSyncSemantics() {
        val dir = createTempDir(prefix = "companion-smoke")
        try {
            val db = setupDb(dir)
            val store = CompanionStore(db)
            val fake = FakeCompleter()
            val brain = CompanionBrain(store, fake)

            val result = runBlocking { brain.reply("conv-1", "下周一起去看展吧") }
            assertFalse(result.slept)
            assertEquals(listOf("好呀", "晚点聊"), result.segments)
            assertEquals("开心", result.emotion)
            assertEquals(1, fake.calls)                              // 回复返回时只打了生成这一枪
            // 1.0.99.3 提速：摘录改后台异步（与 App 的 summarizeAndRemember 同语义），
            // 回复返回时摘录可能还没落库 —— 轮询等它写完再验（记忆照写，只是不挡回复）
            runBlocking {
                val deadline = System.currentTimeMillis() + 5000
                while (System.currentTimeMillis() < deadline) {
                    val ready = DriverManager.getConnection("jdbc:sqlite:$db").use { c ->
                        c.createStatement().use { st ->
                            val mems = st.executeQuery("SELECT 1 FROM objects WHERE kind='mems' AND id='conv-1'").use { it.next() }
                            val mood = st.executeQuery("SELECT data FROM objects WHERE kind='pcset' AND id='conv-1'").use {
                                if (it.next()) java.util.zip.GZIPInputStream(it.getBinaryStream(1)).readBytes()
                                    .toString(Charsets.UTF_8).contains("lastMood") else false
                            }
                            mems && mood
                        }
                    }
                    if (ready) break
                    kotlinx.coroutines.delay(20)
                }
            }
            assertEquals(2, fake.calls)                              // 回复 + 摘录

            DriverManager.getConnection("jdbc:sqlite:$db").use { c ->
                c.createStatement().use { st ->
                    // msgs 箱：用户 1 + 回复 2 = 3 条；rev 5 → 7（两次写各 +1）；seq 前进 2
                    st.executeQuery("SELECT rev, seq, data FROM objects WHERE kind='msgs' AND id='conv-1'").use { rs ->
                        assertTrue(rs.next())
                        assertEquals(7L, rs.getLong(1))
                        assertEquals(12L, rs.getLong(2))
                        val raw = java.util.zip.GZIPInputStream(rs.getBinaryStream(3)).readBytes().toString(Charsets.UTF_8)
                        assertTrue(raw.contains("下周一起去看展吧"))
                        assertTrue(raw.contains("好呀"))
                        assertTrue(raw.contains("晚点聊"))
                    }
                    // mems 箱：摘录写回
                    st.executeQuery("SELECT rev, data FROM objects WHERE kind='mems' AND id='conv-1'").use { rs ->
                        assertTrue(rs.next())
                        assertEquals(1L, rs.getLong(1))
                        val raw = java.util.zip.GZIPInputStream(rs.getBinaryStream(2)).readBytes().toString(Charsets.UTF_8)
                        assertTrue(raw.contains("下周要一起去看展"))
                    }
                    // pcset：氛围快照
                    st.executeQuery("SELECT data FROM objects WHERE kind='pcset' AND id='conv-1'").use { rs ->
                        assertTrue(rs.next())
                        val raw = java.util.zip.GZIPInputStream(rs.getBinaryStream(1)).readBytes().toString(Charsets.UTF_8)
                        assertTrue(raw.contains("聊得很热络"))
                        assertTrue(raw.contains("lastMood"))
                    }
                    // users：seq 游标 + last_write + 配额（本轮 4 次对象写：用户消息/回复/记忆/氛围 各发一格游标）
                    st.executeQuery("SELECT seq, last_write, bytes_used FROM users WHERE id='u1'").use { rs ->
                        assertTrue(rs.next())
                        assertEquals(14L, rs.getLong(1))
                        assertTrue(rs.getLong(2) > 0)
                    }
                    // 账本（2026-10-22 微信也要进账本）：生成 + 记忆摘录各记一行；model 恒「微信通道」id
                    st.executeQuery("SELECT note, model, upstream, prompt_tokens, completion_tokens FROM ai_usage ORDER BY id").use { rs ->
                        assertTrue(rs.next())
                        assertEquals("brain", rs.getString(1))
                        assertEquals("wechat", rs.getString(2))
                        assertEquals("up-fake", rs.getString(3))
                        assertEquals(100, rs.getInt(4))
                        assertEquals(20, rs.getInt(5))
                        assertTrue(rs.next())
                        assertEquals("brain,memory", rs.getString(1))
                        assertFalse(rs.next())
                    }
                }
            }
            store.close()
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun skipUserWriteLeavesUserMessagesToTheAppAndReturnsIds() {
        val dir = createTempDir(prefix = "companion-smoke3")
        try {
            val db = setupDb(dir)
            val store = CompanionStore(db)
            val fake = FakeCompleter()
            val brain = CompanionBrain(store, fake)
            // M4 收口：App 自管用户消息（skipUserWrite），大脑只写回复并返回消息 id
            val r = runBlocking { brain.reply("conv-1", "今晚吃什么", userMessageId = "app-u1", skipUserWrite = true) }
            assertEquals(2, r.segments.size)
            assertEquals(2, r.messageIds.size)
            DriverManager.getConnection("jdbc:sqlite:$db").use { c ->
                c.createStatement().use { st ->
                    st.executeQuery("SELECT data FROM objects WHERE kind='msgs' AND id='conv-1'").use { rs ->
                        assertTrue(rs.next())
                        val raw = java.util.zip.GZIPInputStream(rs.getBinaryStream(1)).readBytes().toString(Charsets.UTF_8)
                        assertFalse("skipUserWrite 不该写用户消息", raw.contains("今晚吃什么"))
                        assertTrue(raw.contains("好呀"))                      // 回复照写
                        assertTrue(raw.contains(r.messageIds[0]))            // 返回的 id 就是箱里的 id
                    }
                }
            }
            // 指定 userMessageId 幂等：箱里已有该 id 时不重复写
            val r2 = runBlocking { brain.reply("conv-1", "今晚吃什么", userMessageId = "app-u2", skipUserWrite = false) }
            assertEquals(2, r2.segments.size)
            val r3 = runBlocking { brain.reply("conv-1", "今晚吃什么", userMessageId = "app-u2", skipUserWrite = false) }
            assertEquals(2, r3.segments.size)
            DriverManager.getConnection("jdbc:sqlite:$db").use { c ->
                c.createStatement().use { st ->
                    st.executeQuery("SELECT data FROM objects WHERE kind='msgs' AND id='conv-1'").use { rs ->
                        assertTrue(rs.next())
                        val raw = java.util.zip.GZIPInputStream(rs.getBinaryStream(1)).readBytes().toString(Charsets.UTF_8)
                        assertEquals("app-u2 只该出现一次", 1, Regex("\"app-u2\"").findAll(raw).count())
                    }
                }
            }
            store.close()
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun missingConversationIsRejected() {
        val dir = createTempDir(prefix = "companion-smoke2")
        try {
            val store = CompanionStore(setupDb(dir))
            val brain = CompanionBrain(store, FakeCompleter())
            try {
                runBlocking { brain.reply("nope-conv", "你好") }
                fail("should throw")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message!!.contains("not found"))
            }
            // 非法 id 直接被白名单挡在门外（Main 层400）
            assertFalse(store.safeId("x'; DROP TABLE objects;--"))
            store.close()
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun batchMessagesWriteOneEachAndTriggerMergePrompt() {
        val dir = createTempDir(prefix = "companion-smoke-batch")
        try {
            val db = setupDb(dir)
            val store = CompanionStore(db)
            val fake = FakeCompleter()
            val brain = CompanionBrain(store, fake)
            // 1.0.99.4 缓冲批：逐条幂等落库（重走窗口不写双份），多条合一带序号、batchSize 进提示词
            val r = runBlocking {
                brain.reply(
                    "conv-1", batchId = "b-1",
                    messages = listOf(
                        CompanionBrain.UserMsg("wx_11", "第一句"),
                        CompanionBrain.UserMsg("wx_12", "第二句")
                    )
                )
            }
            assertEquals(2, r.segments.size)
            val raw = boxRaw(db)
            assertTrue(raw.contains("wx_11") && raw.contains("wx_12"))
            assertTrue("多条带序号", raw.contains("1. 第一句") || raw.contains("第一句"))
            val gen = fake.requests.first()
            val prompt = gen.joinToString("\n") { it["content"]?.toString().orEmpty() }
            assertTrue("多条合一提示词该生效（batchSize=2）", prompt.contains("一口气发了 2 条消息"))
            // 幂等重放：同样两条 id 再来一遍，用户消息不写双份
            runBlocking {
                brain.reply(
                    "conv-1", batchId = "b-1b",
                    messages = listOf(
                        CompanionBrain.UserMsg("wx_11", "第一句"),
                        CompanionBrain.UserMsg("wx_12", "第二句")
                    )
                )
            }
            assertEquals("wx_11 只该出现一次", 1, Regex("\"wx_11\"").findAll(boxRaw(db)).count())
            store.close()
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun cancelBeforePersistDiscardsTheWholeRound() {
        val dir = createTempDir(prefix = "companion-smoke-cancel1")
        try {
            val db = setupDb(dir)
            val store = CompanionStore(db)
            val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
            val hanging = HangingCompleter(gate)
            val brain = CompanionBrain(store, hanging)
            runBlocking {
                val job = async {
                    runCatching { brain.reply("conv-1", "你好", batchId = "b-cancel") }
                }
                while (hanging.calls == 0) kotlinx.coroutines.delay(5)   // 等生成真的挂上了
                brain.cancel("b-cancel")
                gate.complete(Unit)
                runCatching { job.await() }   // 被作废的这轮以取消收场
                kotlinx.coroutines.delay(50)   // 摘录后台也不能补写
            }
            val raw = boxRaw(db)
            assertFalse("弃写：半截回复不许进箱", raw.contains("好呀"))
            store.close()
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun cancelAfterRespondRemovesPersistedReplies() {
        val dir = createTempDir(prefix = "companion-smoke-cancel2")
        try {
            val db = setupDb(dir)
            val store = CompanionStore(db)
            val brain = CompanionBrain(store, FakeCompleter())
            val r = runBlocking { brain.reply("conv-1", "你好", batchId = "b-late") }
            assertTrue(boxRaw(db).contains("好呀"))
            // 通道层定案前出岔（发出去之前断了）：已落库的清掉半截，不留孤儿回复
            runBlocking { assertTrue(brain.cancel("b-late")) }
            val raw = boxRaw(db)
            assertFalse("孤儿回复该被清掉", raw.contains("好呀"))
            assertTrue(raw.contains("你好"))          // 用户消息是真消息，照留
            store.close()
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun writeBoxRejectsStaleRev() {
        val dir = createTempDir(prefix = "companion-smoke-rev1")
        try {
            val store = CompanionStore(setupDb(dir))
            val objs = store.load("conv-1")!!
            val rev = objs.revs["msgs"] ?: 0L
            store.writeBox(objs.userId, "msgs", "conv-1", rev, "[]", System.currentTimeMillis())
            try {
                // 拿旧 rev 再写 = 覆盖别人刚写的 —— 必须像 putObject 一样拒掉
                store.writeBox(objs.userId, "msgs", "conv-1", rev, "[]", System.currentTimeMillis())
                fail("stale rev should conflict")
            } catch (_: CompanionStore.RevConflict) {
                // 预期：调用方重读合并再写
            }
            store.close()
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun concurrentAppPushSurvivesBrainReply() {
        val dir = createTempDir(prefix = "companion-smoke-rev2")
        try {
            val db = setupDb(dir)
            val store = CompanionStore(db)
            val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
            val hanging = HangingCompleter(gate)
            val brain = CompanionBrain(store, hanging)
            runBlocking {
                val job = async { runCatching { brain.reply("conv-1", "在吗", userMessageId = "wx_a") } }
                while (hanging.calls == 0) kotlinx.coroutines.delay(5)
                // 「App」在生成期间往同一 msgs 箱推了一条（rev 会往前走）
                val objs = store.load("conv-1")!!
                store.writeBox(objs.userId, "msgs", "conv-1", objs.revs["msgs"] ?: 0L,
                    store.messagesToWire(objs.messages + Message(
                        id = "app-1", role = Role.USER, content = "App侧消息",
                        timestamp = System.currentTimeMillis(), mode = ChatMode.COMPANION)),
                    System.currentTimeMillis())
                gate.complete(Unit)
                runCatching { job.await() }
            }
            val raw = boxRaw(db)
            assertTrue("App 并发推的不许被盖掉", raw.contains("App侧消息"))
            assertTrue("大脑回复照常追加", raw.contains("好呀"))
            assertTrue(raw.contains("在吗"))
            store.close()
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun deepThinkingWithHighQualityRunsDeepPrepPass() {
        val dir = createTempDir(prefix = "companion-smoke-prep")
        try {
            val db = setupDb(dir, CharacterProfile(
                name = "旅人", dialogueMode = 0, deepThinking = true, highQualityMemory = true
            ))
            val store = CompanionStore(db)
            val fake = FakeCompleter()
            val brain = CompanionBrain(store, fake)
            val r = runBlocking { brain.reply("conv-1", "下周一起去看展吧") }
            assertEquals(listOf("好呀", "晚点聊"), r.segments)
            // 第一趟=推演（不回话），第二趟=生成，且带上了推演批注（界面永远看不到的内部话）
            assertTrue(fake.requests.size >= 2)
            val prep = fake.requests[0].joinToString("\n") { it["content"]?.toString().orEmpty() }
            val gen = fake.requests[1].joinToString("\n") { it["content"]?.toString().orEmpty() }
            assertTrue("第一趟该是推演", prep.contains("只做推演"))
            assertTrue("第二趟该带内部批注", gen.contains("你心里已经过了一遍"))
            store.close()
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun writtenMeaningSurvivesMoodAndHasOneDeliveryDelayPerMessage() {
        val dir = createTempDir(prefix = "companion-lossless")
        try {
            val store = CompanionStore(setupDb(dir))
            val brain = CompanionBrain(store, object : ChatCompleter {
                override suspend fun complete(messages: List<Map<String, Any?>>, temperature: Double, extras: Map<String, Any?>) =
                    if (messages.first()["content"].toString().let { it.contains("记忆摘录器") || it.contains("记忆归纳器") }) ChatResult("{}")
                    else ChatResult("[情绪:难过]\n我已经出门了\n地铁停运，改坐公交\n所以会晚十分钟，不是让你一直等")
            })
            val out = runBlocking { brain.reply("conv-1", "到哪了", batchId = "lossless") }
            for (line in listOf("我已经出门了", "地铁停运，改坐公交", "所以会晚十分钟，不是让你一直等")) {
                assertTrue("Lost: $line", out.segments.any { line in it })
                assertTrue(store.load("conv-1")!!.messages.any { line in it.content })
            }
            assertEquals(out.segments.size, out.messageIds.size)
            val wire = AppJson.gson.toJsonTree(out).asJsonObject
            val delays = wire.getAsJsonArray("delaysMs")
            assertNotNull("Brain must return the shared delivery plan", delays)
            assertEquals(out.segments.size, delays.size())
            assertTrue(delays.all { it.asLong in 0L..3000L })
            runBlocking { brain.cancel("lossless") }
            store.close()
        } finally { dir.deleteRecursively() }
    }

    @Test
    fun deliberateSilenceIsNotRetriedButAnEmptyModelResponseIs() {
        for (intentional in listOf(true, false)) {
            val dir = createTempDir(prefix = "companion-silence")
            try {
                val store = CompanionStore(setupDb(dir))
                var generationCalls = 0
                val brain = CompanionBrain(store, object : ChatCompleter {
                    override suspend fun complete(messages: List<Map<String, Any?>>, temperature: Double, extras: Map<String, Any?>): ChatResult {
                        if (messages.first()["content"].toString().let { it.contains("记忆摘录器") || it.contains("记忆归纳器") }) return ChatResult("{}")
                        generationCalls++
                        return ChatResult(if (intentional) "[情绪:生气]\n[回应:不回复]" else if (generationCalls == 1) "" else "[情绪:平静]\n我听到了")
                    }
                })
                val out = runBlocking { brain.reply("conv-1", "这事晚点说", batchId = "silence") }
                if (intentional) {
                    assertTrue(out.silence)
                    assertTrue(out.segments.isEmpty())
                    assertEquals(1, generationCalls)
                    assertFalse(store.load("conv-1")!!.messages.any { it.role == Role.ASSISTANT })
                } else {
                    assertFalse(out.silence)
                    assertEquals(listOf("我听到了"), out.segments)
                    assertEquals(2, generationCalls)
                }
                runBlocking { brain.cancel("silence") }
                store.close()
            } finally { dir.deleteRecursively() }
        }
    }

    @Test
    fun autoMemoryOffDoesNotEvenStartExtractionOrWriteAtmosphere() {
        val dir = createTempDir(prefix = "companion-no-memory")
        try {
            val db = setupDb(dir)
            val store = CompanionStore(db)
            val state = store.load("conv-1")!!
            store.writeBox(state.userId, "settings", "global", 0, """{"autoSummarizeMemory":false}""", 1)
            val called = kotlinx.coroutines.CompletableDeferred<Unit>()
            val brain = CompanionBrain(store, object : ChatCompleter {
                override suspend fun complete(messages: List<Map<String, Any?>>, temperature: Double, extras: Map<String, Any?>): ChatResult {
                    val prompt = messages.first()["content"].toString()
                    if (prompt.contains("记忆摘录器") || prompt.contains("记忆归纳器")) {
                        called.complete(Unit)
                        return ChatResult("""{"summary":"不能写","mood":"开心","atmosphere":"不能写"}""")
                    }
                    return ChatResult("[情绪:平静]\n收到了")
                }
            })
            runBlocking {
                brain.reply("conv-1", "不要记忆", batchId = "no-memory")
                assertNull("Disabled memory still starts extraction", kotlinx.coroutines.withTimeoutOrNull(200) { called.await() })
            }
            runBlocking { brain.cancel("no-memory") }
            assertEquals("", boxRaw(db, "mems"))
            assertFalse(boxRaw(db, "pcset").contains("不能写"))
            store.close()
        } finally { dir.deleteRecursively() }
    }

    @Test
    fun storeFiltersTombstonedSourcesBeforeRecall() {
        val dir = createTempDir(prefix = "companion-recall-delete")
        try {
            val store = CompanionStore(setupDb(dir))
            val state = store.load("conv-1")!!
            val msgs = listOf(Message(id = "gone", role = Role.USER, content = "已删除私密事实", timestamp = 1))
            store.writeBox(state.userId, "msgs", "conv-1", 5, store.messagesToWire(msgs), 1)
            store.writeBox(state.userId, "mems", "conv-1", 0, """[{"summary":"已删除私密事实","sourceMessageIds":["gone"]}]""", 1)
            store.writeBox(state.userId, "pcset", "conv-1", 0, """{"lastMood":"生气","atmosphere":"已删除的氛围","atmosphereSourceMessageIds":["gone"]}""", 1)
            store.writeBox(state.userId, "conv", "conv-1", 3,
                AppJson.gson.toJson(state.conv.copy(deletedMessageIds = listOf("gone"))), 1)
            val filtered = store.load("conv-1")!!
            assertTrue(filtered.messages.isEmpty())
            assertTrue(filtered.memories.isEmpty())
            assertFalse(filtered.pcset.toString().contains("已删除的氛围"))
            store.close()
        } finally { dir.deleteRecursively() }
    }

    @Test
    fun deletingAnUnsentReplyAlsoDeletesItsDerivedMemoryAndAtmosphere() {
        val dir = createTempDir(prefix = "companion-cancel-memory")
        try {
            val db = setupDb(dir)
            val store = CompanionStore(db)
            val brain = CompanionBrain(store, object : ChatCompleter {
                override suspend fun complete(messages: List<Map<String, Any?>>, temperature: Double, extras: Map<String, Any?>) =
                    if (messages.first()["content"].toString().let { it.contains("记忆摘录器") || it.contains("记忆归纳器") }) ChatResult("{}")
                    else ChatResult("[情绪:平静]\n我八点到")
            })
            val out = runBlocking { brain.reply("conv-1", "明早见", batchId = "cancel-derived") }
            // Install already-completed derivations, then exercise the real cancellation path.
            val state = store.load("conv-1")!!
            store.writeBox(state.userId, "mems", "conv-1", state.revs["mems"] ?: 0,
                """[{"summary":"我八点到","sourceMessageIds":["${out.messageIds.first()}"]}]""", 1)
            store.writeBox(state.userId, "pcset", "conv-1", state.revs["pcset"] ?: 0,
                """{"atmosphere":"约好八点","atmosphereSourceMessageIds":["${out.messageIds.first()}"]}""", 1)
            runBlocking { brain.cancel("cancel-derived") }
            assertFalse(boxRaw(db).contains("我八点到"))
            assertFalse(boxRaw(db, "mems").contains("我八点到"))
            assertFalse(boxRaw(db, "pcset").contains("约好八点"))
            store.close()
        } finally { dir.deleteRecursively() }
    }

    @Test
    fun derivedWriteRejectsAChangedParentOrMemorySwitchSnapshotAtomically() {
        val dir = createTempDir(prefix = "companion-derived-guard")
        try {
            val store = CompanionStore(setupDb(dir))
            val old = store.load("conv-1")!!
            store.writeBox(old.userId, "conv", "conv-1", 3,
                AppJson.gson.toJson(old.conv.copy(deletedMessageIds = listOf("deleted-source"))), 1)
            assertTrue(runCatching {
                store.writeBox(old.userId, "mems", "conv-1", 0, "[]", 1, expectedConvRev = 3)
            }.exceptionOrNull() is CompanionStore.RevConflict)
            val current = store.load("conv-1")!!
            store.writeBox(old.userId, "pcset", "conv-1", 0, """{"autoSummarizeMemory":false}""", 1)
            assertTrue(runCatching {
                store.writeBox(old.userId, "mems", "conv-1", 0, "[]", 1,
                    expectedConvRev = current.revs["conv"], expectedPcsetRev = 0)
            }.exceptionOrNull() is CompanionStore.RevConflict)
            store.close()
        } finally { dir.deleteRecursively() }
    }

    @Test
    fun optionalNativeSearchNeverSearchesOrdinarySpeechAndOnlyUsesValidatedIntent() {
        for (needsSearch in listOf(false, true)) {
            val dir = createTempDir(prefix = "companion-search-intent")
            try {
                val store = CompanionStore(setupDb(dir, CharacterProfile(name = "沈岚", enableWebSearch = true)))
                store.writeBox("u1", "settings", "global", 0, """{"autoSummarizeMemory":false}""", 1)
                var searches = 0
                var calls = 0
                val brain = CompanionBrain(store, object : ChatCompleter {
                    override suspend fun complete(messages: List<Map<String, Any?>>, temperature: Double, extras: Map<String, Any?>): ChatResult {
                        calls++
                        if (calls == 1) assertTrue(extras.containsKey("tools"))
                        if (needsSearch && calls == 1) return ChatResult("", toolCalls = listOf(mapOf("id" to "q1", "type" to "function",
                            "function" to mapOf("name" to "search_web", "arguments" to """{"queries":["杭州今天预警"]}"""))))
                        if (needsSearch) {
                            assertTrue(messages.any { it["role"] == "tool" && it["content"].toString().contains("fixture-evidence") })
                            assertFalse(extras.containsKey("tools"))
                        }
                        return ChatResult("[情绪:平静]\n听到了")
                    }
                }, search = { intent ->
                    searches++
                    assertEquals(listOf("杭州今天预警"), intent.queries)
                    "fixture-evidence"
                })
                runBlocking { brain.reply("conv-1", if (needsSearch) "查一下杭州今天有没有预警" else "昨天汇报被打断了，有点烦") }
                assertEquals(if (needsSearch) 1 else 0, searches)
                assertEquals(if (needsSearch) 2 else 1, calls)
                store.close()
            } finally { dir.deleteRecursively() }
        }
    }

    @Test fun emptyReplyRetryReusesSearchEvidenceWithoutAnotherSearch() {
        val dir = createTempDir(prefix = "companion-search-retry")
        try {
            val store = CompanionStore(setupDb(dir, CharacterProfile(name = "沈岚", enableWebSearch = true)))
            store.writeBox("u1", "settings", "global", 0, """{"autoSummarizeMemory":false}""", 1)
            var calls = 0
            var searches = 0
            val brain = CompanionBrain(store, object : ChatCompleter {
                override suspend fun complete(messages: List<Map<String, Any?>>, temperature: Double, extras: Map<String, Any?>): ChatResult {
                    calls++
                    if (calls == 1) return ChatResult("", toolCalls = listOf(mapOf("id" to "q1", "type" to "function",
                        "function" to mapOf("name" to "search_web", "arguments" to """{"queries":["杭州今天预警"]}"""))))
                    assertTrue(messages.any { it["content"].toString().contains("fixture-evidence") })
                    assertFalse(extras.containsKey("tools"))
                    return ChatResult(if (calls == 2) "" else "[情绪:平静]\n查到了")
                }
            }, search = { searches++; "fixture-evidence" })
            val result = runBlocking { brain.reply("conv-1", "查杭州今天的预警", batchId = "search-retry") }
            assertEquals(listOf("查到了"), result.segments)
            assertEquals(3, calls)
            assertEquals(1, searches)
            runBlocking { brain.cancel("search-retry") }
            store.close()
        } finally { dir.deleteRecursively() }
    }

    @Test fun persistenceRetriesMustRevalidateTheSameRoundMaterials() {
        val dir = createTempDir(prefix = "companion-persist-materials")
        try {
            val store = CompanionStore(setupDb(dir))
            val original = store.load("conv-1")!!
            val changed = original.copy(conv = original.conv.copy(deletedMessageIds = listOf("source-deleted")))
            assertTrue(original.hasSameMemoryInputsAs(original))
            assertFalse(changed.hasSameMemoryInputsAs(original))
            val off = original.copy(globalSettings = com.google.gson.JsonParser.parseString("""{"autoSummarizeMemory":false}""").asJsonObject)
            assertFalse(off.hasSameMemoryInputsAs(original))
            val source = java.io.File("src/main/kotlin/com/freechat/companion/CompanionBrain.kt").readText()
            assertTrue(source.contains("persistMessages(convId, replyMsgs, wechatBinding, ::ensureRoundMaterialActive)"))
            assertTrue(source.contains("persistMessages(convId, listOf(ellipsis), wechatBinding, ::ensureRoundMaterialActive)"))
            val retry = source.substringAfter("private suspend fun persistMessages(")
            assertTrue(retry.indexOf("snapshotGuard?.invoke(fresh)") in 1 until retry.indexOf("store.writeBox("))
            store.close()
        } finally { dir.deleteRecursively() }
    }

    @Test fun globalMemoryOffAppliesEvenWhenCompanionHasALegacyOverride() {
        val dir = createTempDir(prefix = "companion-global-memory")
        try {
            val store = CompanionStore(setupDb(dir))
            store.writeBox("u1", "settings", "global", 0, """{"autoSummarizeMemory":false}""", 1)
            store.writeBox("u1", "pcset", "conv-1", 0, """{"autoSummarizeMemory":true}""", 1)
            val calls = java.util.concurrent.atomic.AtomicInteger()
            val brain = CompanionBrain(store, object : ChatCompleter {
                override suspend fun complete(messages: List<Map<String, Any?>>, temperature: Double, extras: Map<String, Any?>): ChatResult {
                    if (messages.first()["content"].toString().let { it.contains("记忆摘录器") || it.contains("记忆归纳器") }) calls.incrementAndGet()
                    return ChatResult("[情绪:平静]\n好")
                }
            })
            runBlocking { brain.reply("conv-1", "不要写记忆", batchId = "global-off"); kotlinx.coroutines.delay(150) }
            assertEquals(0, calls.get())
            runBlocking { brain.cancel("global-off") }
            store.close()
        } finally { dir.deleteRecursively() }
    }

    @Test fun deletingOldMemoryDuringDeepPrepCancelsBeforeTheNextGenerationCall() {
        val dir = createTempDir(prefix = "companion-deep-delete")
        try {
            val store = CompanionStore(setupDb(dir, CharacterProfile(name = "沈岚", deepThinking = true, highQualityMemory = true)))
            store.writeBox("u1", "mems", "conv-1", 0, """[{"id":"old","summary":"已删除私密事实","keywords":[],"kind":"plot","sourceMessageIds":["old-source"]}]""", 1)
            val entered = kotlinx.coroutines.CompletableDeferred<Unit>()
            val release = kotlinx.coroutines.CompletableDeferred<Unit>()
            var generationCalls = 0
            val brain = CompanionBrain(store, object : ChatCompleter {
                override suspend fun complete(messages: List<Map<String, Any?>>, temperature: Double, extras: Map<String, Any?>): ChatResult {
                    if (messages.last()["content"].toString().contains("只做推演")) {
                        entered.complete(Unit); release.await()
                        return ChatResult("1. 私密事实\n2. 无\n3. 接话\n4. 无")
                    }
                    generationCalls++
                    return ChatResult("[情绪:平静]\n不能发出的旧消息")
                }
            })
            runBlocking {
                val job = async { runCatching { brain.reply("conv-1", "接着聊") } }
                kotlinx.coroutines.withTimeout(2000) { entered.await() }
                val current = store.load("conv-1")!!
                store.writeBox("u1", "conv", "conv-1", current.revs["conv"]!!,
                    AppJson.gson.toJson(current.conv.copy(deletedMessageIds = listOf("old-source"))), 1)
                release.complete(Unit)
                assertTrue(job.await().exceptionOrNull() is kotlinx.coroutines.CancellationException)
            }
            assertEquals(0, generationCalls)
            assertFalse(store.load("conv-1")!!.messages.any { it.role == Role.ASSISTANT })
            store.close()
        } finally { dir.deleteRecursively() }
    }

    @Test fun lateOldExtractionCannotOvertakeANewerCancellationOrAtmosphere() {
        val dir = createTempDir(prefix = "companion-memory-order")
        try {
            val store = CompanionStore(setupDb(dir))
            val firstEntered = kotlinx.coroutines.CompletableDeferred<Unit>()
            val releaseFirst = kotlinx.coroutines.CompletableDeferred<Unit>()
            val memoryCalls = java.util.concurrent.atomic.AtomicInteger()
            val brain = CompanionBrain(store, object : ChatCompleter {
                override suspend fun complete(messages: List<Map<String, Any?>>, temperature: Double, extras: Map<String, Any?>): ChatResult {
                    val system = messages.first()["content"].toString()
                    if (system.contains("记忆摘录器") || system.contains("记忆归纳器")) {
                        val first = memoryCalls.incrementAndGet() == 1
                        if (first) { firstEntered.complete(Unit); releaseFirst.await() }
                        return ChatResult(if (first) """{"roleKnowledge":"约好八点见","openLoops":"约定未完成","mood":"开心","atmosphere":"约好见面"}"""
                            else """{"roleKnowledge":"已取消见面","openLoops":"约定已取消","mood":"平静","atmosphere":"取消后平静"}""")
                    }
                    return ChatResult("[情绪:平静]\n收到了")
                }
            })
            runBlocking {
                brain.reply("conv-1", "明早八点见", batchId = "older")
                kotlinx.coroutines.withTimeout(2000) { firstEntered.await() }
                brain.reply("conv-1", "刚刚的约定取消", batchId = "newer")
                kotlinx.coroutines.withTimeout(2000) {
                    while (store.load("conv-1")!!.memories.none { it.openLoops.contains("已取消") }) kotlinx.coroutines.delay(10)
                }
                releaseFirst.complete(Unit)
                kotlinx.coroutines.withTimeout(2000) {
                    while (store.load("conv-1")!!.memories.size < 2) kotlinx.coroutines.delay(10)
                }
            }
            val state = store.load("conv-1")!!
            assertEquals("约定已取消", state.memories.maxBy { it.timestamp }.openLoops)
            assertEquals("取消后平静", state.pcset.get("atmosphere").asString)
            runBlocking { brain.cancel("older"); brain.cancel("newer") }
            store.close()
        } finally { dir.deleteRecursively() }
    }
}
