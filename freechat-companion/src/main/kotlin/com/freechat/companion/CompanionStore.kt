package com.freechat.companion

import com.freechat.data.AppJson
import com.freechat.data.healed
import com.freechat.model.Conversation
import com.freechat.model.MemoryEntry
import com.freechat.model.Message
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * 同步库读写（/var/lib/freechat/freechat.db，与 Node 服务共用；WAL 多进程安全）。
 *
 * **写语义必须与 FreeChatServer/src/sync.js 的 putObject 一字不差**（rev+1 / users.seq 自增
 * 发新游标 / last_write=touchWrite / size_raw 配额 / gzip 存储）——客户端下一轮 changes 拉到、
 * 409 合并、看板计数全靠这套语义。改这里之前先读 sync.js 对应段。
 *
 * 线上格式即模型 JSON（见 Wire.kt 类注释）：msgs=Message 数组（剥本地路径）、mems=MemoryEntry 数组、
 * conv=Conversation JSON、pcset=键值对象（PerConvBridge 白名单键）。
 */
class CompanionStore(dbPath: String) : AutoCloseable {

    private val conn: Connection = DriverManager.getConnection("jdbc:sqlite:$dbPath").apply {
        autoCommit = false
        createStatement().use { it.execute("PRAGMA busy_timeout = 5000") }
        createStatement().use { it.execute("PRAGMA journal_mode = WAL") }
    }

    private fun gunzip(bytes: ByteArray): String =
        GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes().toString(Charsets.UTF_8) }

    private fun gzip(text: String): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(text.toByteArray(Charsets.UTF_8)) }
        return out.toByteArray()
    }

    /** id 白名单：对话 id 是客户端生成的 UUID/短串，只放行安全字符（拼进 SQL 前必须过这道） */
    fun safeId(id: String): Boolean = id.isNotEmpty() && id.length <= 64 && id.all { it.isLetterOrDigit() || it in "-_" }

    data class ConvObjects(
        val userId: String,
        val conv: Conversation,
        val messages: List<Message>,
        val memories: List<MemoryEntry>,
        val pcset: JsonObject,
        val revs: Map<String, Long>   // kind → 当前 rev（写回时 +1）
    )

    /** 按对话 id 找到整套对象（conv 必须存在且未删；其它箱允许缺省=空）。[convId] 须先过 [safeId] */
    @Synchronized
    fun load(convId: String): ConvObjects? {
        try {
            val revs = mutableMapOf<String, Long>()
            var userId: String? = null

            fun rawOf(kind: String): String? {
                val u = userId ?: return null
                conn.prepareStatement("SELECT data, rev FROM objects WHERE user_id=? AND kind=? AND id=? AND deleted=0").use { ps ->
                    ps.setString(1, u); ps.setString(2, kind); ps.setString(3, convId)
                    ps.executeQuery().use { r ->
                        if (!r.next()) return null
                        revs[kind] = r.getLong(2)
                        return gunzip(r.getBytes(1))
                    }
                }
            }

            conn.prepareStatement("SELECT user_id FROM objects WHERE kind='conv' AND id=? AND deleted=0").use { ps ->
                ps.setString(1, convId)
                ps.executeQuery().use { r -> if (r.next()) userId = r.getString(1) else return null }
            }
            val u = userId ?: return null
            val convRaw = rawOf("conv") ?: return null
            val conv = runCatching { AppJson.gson.fromJson(convRaw, Conversation::class.java) }.getOrNull()?.healed() ?: return null

            val msgType = object : TypeToken<List<Message>>() {}.type
            val memType = object : TypeToken<List<MemoryEntry>>() {}.type
            val messages = runCatching {
                AppJson.gson.fromJson<List<Message>>(rawOf("msgs") ?: "[]", msgType).orEmpty().map { it.healed() }
            }.getOrDefault(emptyList())
            val memories = runCatching {
                AppJson.gson.fromJson<List<MemoryEntry>>(rawOf("mems") ?: "[]", memType).orEmpty().map { it.healed() }
            }.getOrDefault(emptyList())
            val pcset = runCatching { JsonParser.parseString(rawOf("pcset") ?: "{}").asJsonObject }.getOrDefault(JsonObject())
            return ConvObjects(u, conv, messages, memories, pcset, revs)
        } finally {
            // ★ 必须收掉读快照（1.0.99.4 修 SQLITE_BUSY_SNAPSHOT）：autoCommit=false 下 SELECT 会
            // 开着一个读事务，一直挂到后面的写才升级 —— 期间 Node 侧（App 同步/推图上云）一提交，
            // 写侧就被 WAL 快照冲突顶死（「brain 500 / database is locked」）。读完即收，写另起新事务。
            conn.rollback()
        }
    }

    /** rev 冲突：这箱在 load 之后被别人（Node/putObject）写过了 —— 调用方重读合并再写，不许盲写覆盖 */
    class RevConflict : Exception("object rev conflict")

    /**
     * 写回一箱（rev+1 / 发新 seq / touchWrite / 配额差额），与 sync.js putObject 同语义。返回新 rev。
     *
     * 1.0.99.4b 两道防线：
     *  · **rev 校验**（putObject 的 409 语义，之前漏了）：UPDATE 带 `AND rev=?`，行被人动过就抛
     *    [RevConflict]，调用方重读合并 —— 否则生成期间 App 推过的数据会被静默覆盖、还会撞出同 rev 不同内容；
     *  · **BUSY 重试**：BUSY_SNAPSHOT 不是 busy_timeout 能救的（同一事务的快照已过期，等多久都没用），
     *    只能回滚重来。Node 侧此刻写库很密（图片上云/同步），这是常态不是异常。
     */
    @Synchronized
    fun writeBox(userId: String, kind: String, id: String, baseRev: Long, jsonText: String, updatedAt: Long): Long {
        val gz = gzip(jsonText)
        val rawSize = jsonText.toByteArray(Charsets.UTF_8).size.toLong()
        var lastError: Exception? = null
        repeat(5) { attempt ->
            try {
                conn.rollback()   // 收掉任何残留事务，保证这一把是全新事务
                var newSeq = 0L
                conn.prepareStatement("SELECT seq FROM users WHERE id=?").use { ps ->
                    ps.setString(1, userId)
                    ps.executeQuery().use { if (it.next()) newSeq = it.getLong(1) + 1 }
                }
                val now = System.currentTimeMillis()
                var oldSize = 0L
                var exists = false
                conn.prepareStatement("SELECT size_raw, rev FROM objects WHERE user_id=? AND kind=? AND id=?").use { ps ->
                    ps.setString(1, userId); ps.setString(2, kind); ps.setString(3, id)
                    ps.executeQuery().use { r ->
                        if (r.next()) { exists = true; oldSize = r.getLong(1); if (r.getLong(2) != baseRev) throw RevConflict() }
                    }
                }
                if (exists) {
                    conn.prepareStatement(
                        "UPDATE objects SET rev=?, data=?, updated_at=?, size_raw=?, seq=?, deleted=0 WHERE user_id=? AND kind=? AND id=? AND rev=?"
                    ).use { ps ->
                        ps.setLong(1, baseRev + 1)
                        ps.setBytes(2, gz)
                        ps.setLong(3, updatedAt)
                        ps.setLong(4, rawSize)
                        ps.setLong(5, newSeq)
                        ps.setString(6, userId); ps.setString(7, kind); ps.setString(8, id)
                        ps.setLong(9, baseRev)
                        if (ps.executeUpdate() == 0) throw RevConflict()
                    }
                } else {
                    conn.prepareStatement(
                        "INSERT INTO objects (user_id, kind, id, rev, updated_at, seq, deleted, size_raw, data) VALUES (?,?,?,?,?,?,0,?,?)"
                    ).use { ps ->
                        ps.setString(1, userId); ps.setString(2, kind); ps.setString(3, id)
                        ps.setLong(4, 1)
                        ps.setLong(5, updatedAt)
                        ps.setLong(6, newSeq)
                        ps.setLong(7, rawSize)
                        ps.setBytes(8, gz)
                        ps.executeUpdate()
                    }
                }
                conn.prepareStatement("UPDATE users SET seq=?, last_write=?, bytes_used = bytes_used + ? WHERE id=?").use { ps ->
                    ps.setLong(1, newSeq)
                    ps.setLong(2, now)
                    ps.setLong(3, rawSize - oldSize)
                    ps.setString(4, userId)
                    ps.executeUpdate()
                }
                conn.commit()
                return baseRev + 1
            } catch (e: RevConflict) {
                try { conn.rollback() } catch (_: Exception) { }
                throw e                       // 冲突要新内容重写，这里不空转
            } catch (e: Exception) {
                try { conn.rollback() } catch (_: Exception) { }
                if (e.message?.contains("SQLITE_CONSTRAINT") == true) throw RevConflict()   // 并发插入撞主键
                lastError = e
                val busy = e.message?.contains("SQLITE_BUSY") == true ||
                    e.message?.contains("database is locked") == true
                if (!busy) throw e
                Thread.sleep(50L * (attempt + 1))
            }
        }
        throw lastError ?: IllegalStateException("writeBox failed")
    }

    /**
     * msgs 线上格式：剥掉设备本地字段（与 Wire.msgsToWire 同规则）——
     * 生成的回复没有本地图；这道主要防「用户消息里带了本地图路径」被二次扩散。
     *
     * 1.0.99.3 图片上云：`img:<hash>` 引用**必须原样保留** —— 本方法是整箱重写，
     * 把引用剥掉等于把用户消息里的图从云端抹掉。引用是内容寻址占位，不是本机路径。
     */
    fun messagesToWire(messages: List<Message>): String {
        val arr = JsonArray()
        for (m in messages) {
            val obj = AppJson.gson.toJsonTree(m).asJsonObject
            obj.add("imagePaths", JsonArray().apply {
                m.imagePaths.filter { it.startsWith("img:") }.forEach { add(it) }
            })
            obj.add("imageUrls", JsonArray().apply {
                m.imageUrls.filter {
                    it.startsWith("http://") || it.startsWith("https://") || it.startsWith("img:")
                }.forEach { add(it) }
            })
            obj.addProperty("attachmentPath", null as String?)
            obj.addProperty("quotedImagePath", null as String?)
            arr.add(obj)
        }
        return arr.toString()
    }

    companion object {
        fun newMessageId(): String = UUID.randomUUID().toString()
    }

    override fun close() {
        conn.close()
    }
}
