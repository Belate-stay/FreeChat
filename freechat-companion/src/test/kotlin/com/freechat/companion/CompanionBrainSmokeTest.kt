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
                st.execute("CREATE TABLE users (id TEXT PRIMARY KEY, seq INTEGER DEFAULT 0, bytes_used INTEGER DEFAULT 0, last_write INTEGER DEFAULT 0)")
                st.execute("""CREATE TABLE objects (
                    user_id TEXT, kind TEXT, id TEXT, rev INTEGER DEFAULT 0,
                    updated_at INTEGER DEFAULT 0, seq INTEGER DEFAULT 0,
                    deleted INTEGER DEFAULT 0, size_raw INTEGER DEFAULT 0, data BLOB,
                    PRIMARY KEY (user_id, kind, id))""")
                st.execute("INSERT INTO users (id, seq) VALUES ('u1', 10)")
            }
            val conv = Conversation(
                id = "conv-1", title = "测试对话", mode = ChatMode.COMPANION,
                characterProfile = profile
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
        override suspend fun complete(messages: List<Map<String, Any?>>, temperature: Double, extras: Map<String, Any?>): String {
            calls++
            requests += messages
            // 按提示词内容分流：深度推演 → 四行推演；摘录器 → JSON 草稿；其余（生成）→ 回复
            val prompt = messages.joinToString("\n") { it["content"]?.toString().orEmpty() }
            return when {
                prompt.contains("只做推演") ->
                    "1. 只是闲话\n2. 无\n3. 正常应对\n4. 无"
                prompt.contains("记忆摘录器") || prompt.contains("记忆归纳器") ->
                    """{"summary":"用户说下周要一起去看展","kind":"plot","date":"2026-10-11","keywords":["看展","约定"],"mood":"开心","atmosphere":"聊得很热络","warmth":1}"""
                else -> "[情绪:开心]\n好呀\n晚点聊"
            }
        }
    }

    /** 挂起生成（测过期闸门用）：第一枪挂在 [gate] 上，被取消/释放才继续 */
    private class HangingCompleter(val gate: kotlinx.coroutines.CompletableDeferred<Unit>) : ChatCompleter {
        var calls = 0
        override suspend fun complete(messages: List<Map<String, Any?>>, temperature: Double, extras: Map<String, Any?>): String {
            calls++
            val prompt = messages.joinToString("\n") { it["content"]?.toString().orEmpty() }
            if (prompt.contains("记忆摘录器") || prompt.contains("记忆归纳器") || prompt.contains("只做推演"))
                return """{"summary":"s","kind":"plot","date":"","keywords":[],"mood":"","atmosphere":"","warmth":0}"""
            gate.await()
            return "[情绪:开心]\n好呀\n晚点聊"
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
}
