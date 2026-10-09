package com.freechat.sync

import com.freechat.data.LocalStore
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import java.io.File

/**
 * 1.0.99.3 图片上云：内容寻址的图片对象（sync kind=`img`，id=内容指纹）。
 *
 * 设计（用户拍板：全部图片同步 / 压缩后上传 / 128MB 配额够用）：
 *  · **引用而非路径** —— 消息/角色卡里的本地图在 wire 上写成 `img:<hash>` 占位：
 *    路径出不了这台设备，但「是哪张图」出得去（与头像 avatarHash 同一哲学）。
 *  · **hash = 原图内容指纹**（[Wire.hashOf]）；上传载荷 = **压缩版**（[ImageCodec]：
 *    长边 2048px JPEG ~85%，约 200–500KB）。本机看原图不受影响，云端存压缩版 ——
 *    128MB/人 够存近千张。
 *  · 缓存文件名 `img_<hash>.jpg` **自带指纹**：[refForLocal] 反推引用不用重算，
 *    各设备对同一张图永远给出同一引用，重复上传天然去重（内容寻址幂等）。
 *  · 收端把下载载荷 [materialize] 成缓存文件，消息里的引用就地解成缓存路径；
 *    首发设备的 [Wire.msgsFromWire] 按「同 id 保留本机路径」规则继续看自己的原图。
 *  · 删除引用归零的对象走 SyncEngine 的 IMG 分支推墓碑（「文件没了就删」同款语义）。
 */
object ImageSync {
    const val REF_PREFIX = "img:"
    const val KIND = "img"

    fun isRef(entry: String): Boolean = entry.startsWith(REF_PREFIX)

    fun refOf(hash: String): String = REF_PREFIX + hash

    fun hashOfRef(ref: String): String = ref.removePrefix(REF_PREFIX)

    /** 缓存文件名自带内容指纹（见类注释） */
    fun cacheFile(hash: String): File = File(LocalStore.filesDir(), "img_$hash.jpg")

    fun cachePathIfPresent(hash: String): String? =
        cacheFile(hash).takeIf { it.isFile && it.length() > 0 }?.absolutePath

    /** 本地图/已是引用的条目 → wire 引用（纯读文件算指纹，不压缩不写盘 —— 可单测） */
    fun refForLocal(path: String): String? {
        if (path.isBlank()) return null
        if (isRef(path)) return path
        val file = File(path)
        if (!file.isFile) return null
        hashFromCacheName(file.name)?.let { return refOf(it) }
        val bytes = runCatching { file.readBytes() }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return null
        return refOf(Wire.hashOf(bytes))
    }

    /**
     * 原图、img:<hash> 和压缩缓存是同一张图的不同来源，不能按路径字符串取并集。
     * 第一次出现的身份决定顺序；有原图时用原图，否则用已到达的缓存，再留缺图占位。
     * 只读，不压缩/写盘。原图算指纹会读文件，UI 调用方必须放在 IO dispatcher。
     */
    fun canonicalEntries(entries: List<String>): List<String> {
        data class Candidate(val source: String, val priority: Int)
        val selected = linkedMapOf<String, Candidate>()
        for (entry in entries.distinct()) {
            val http = entry.startsWith("http://") || entry.startsWith("https://")
            val data = entry.startsWith("data:image")
            val file = if (http || data || isRef(entry)) null else File(entry)
            // 缓存指纹来自原图，不是 JPEG 载荷；文件尚未到达时也能识别同一身份。
            val cacheHash = file?.name?.let(::hashFromCacheName)
            val reference = when {
                isRef(entry) -> entry
                http -> null
                data -> dataUriBytes(entry)?.let { refOf(Wire.hashOf(it)) }
                cacheHash != null -> refOf(cacheHash)
                else -> refForLocal(entry)
            }
            val source = if (reference != null && (isRef(entry) || cacheHash != null)) {
                cachePathIfPresent(hashOfRef(reference)) ?: entry
            } else entry
            val readable = file?.let { runCatching { it.isFile && it.length() > 0 }.getOrDefault(false) } == true
            val priority = when {
                readable && cacheHash == null -> 3 // 首发设备继续看本机原图。
                source != entry || (readable && cacheHash != null) || (data && reference != null) -> 2
                http -> 1
                else -> 0
            }
            val identity = reference?.let { "ref:$it" } ?: "entry:$entry"
            val previous = selected[identity]
            if (previous == null || priority > previous.priority) selected[identity] = Candidate(source, priority)
        }
        return selected.values.map { it.source }
    }

    /** `img_<16位十六进制>.jpg` → 指纹；不是缓存文件名返回 null */
    fun hashFromCacheName(name: String): String? {
        if (!name.startsWith("img_") || !name.endsWith(".jpg")) return null
        val hash = name.removePrefix("img_").removeSuffix(".jpg")
        return hash.takeIf { it.length == 16 && it.all { c -> c in "0123456789abcdef" } }
    }

    /** data:image/...;base64,xxx → wire 引用（载荷落缓存） */
    fun ensureCachedFromDataUri(uri: String): String? {
        val bytes = dataUriBytes(uri) ?: return null
        return writeCache(Wire.hashOf(bytes), bytes)
    }

    private fun dataUriBytes(uri: String): ByteArray? {
        if (!uri.startsWith("data:image")) return null
        val comma = uri.indexOf(',')
        if (comma < 0) return null
        return runCatching {
            java.util.Base64.getDecoder().decode(uri.substring(comma + 1).filterNot { it.isWhitespace() })
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    /** 本地文件 → wire 引用（压缩载荷落缓存） */
    fun ensureCachedFromFile(path: String): String? {
        if (path.isBlank()) return null
        if (isRef(path)) return path
        val file = File(path)
        if (!file.isFile) return null
        hashFromCacheName(file.name)?.let { h -> return refOf(h) }   // 缓存文件天然已就绪
        val raw = runCatching { file.readBytes() }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return null
        val hash = Wire.hashOf(raw)
        // 缓存已有（同图二次推送）→ 不重复压缩
        cachePathIfPresent(hash)?.let { return refOf(hash) }
        /* G2 根治（1.2.3+）：压不动**不再丢格**。
           原逻辑 `压缩 ?: 原字节≤500KB ?: return null` —— GIF/怪格式解码失败、
           原图又大于 500KB 时整个图片槽位从 wire 上消失：本机看着好好的，
           其他端和云端永远不知道有过这张图。现在压不动就推原字节；
           只有真的大到塞不进服务器单对象上限（4MB，base64 后 ~5.4MB ≪ 16MB）
           才放弃 —— 消息里仍留本机路径，本机照常显示，别处也不会凭空蒸发。 */
        val payload = ImageCodec.compressForUpload(file)
            ?: raw.takeIf { it.size <= MAX_RAW_PAYLOAD }
            ?: return null
        return writeCache(hash, payload)
    }

    /** 压不动时的原字节兜底上限（再大就真塞不进单对象了） */
    private const val MAX_RAW_PAYLOAD = 4 * 1024 * 1024

    /** 通用入口：文件路径 / data: URI / 已有引用 → 引用 */
    fun ensureCached(entry: String): String? = when {
        entry.isBlank() -> null
        isRef(entry) -> entry
        entry.startsWith("data:image") -> ensureCachedFromDataUri(entry)
        else -> ensureCachedFromFile(entry)
    }

    /** 收端下载载荷落缓存 → 本地路径 */
    fun materialize(hash: String, payload: ByteArray): String? {
        if (payload.isEmpty()) return null
        val path = writeCache(hash, payload)?.let { cacheFile(hash).absolutePath } ?: return null
        healReferences(hash, path)
        return path
    }

    /**
     * 乱序自愈：消息批次可能先于图片对象到达（分页/推送时序都可能），
     * 那时解不开的 `img:<hash>` 引用原样留在消息里。图片落缓存后这里把
     * 各消息文件里的引用**就地替换**成缓存路径，对话列表里的外貌图引用同理。
     *
     * 1.0.99.4b 两道收紧：①只替换**带引号的 JSON 字面量** `"img:<hash>"`——
     * 裸 replace 会把消息正文里恰好写着 `img:<16hex>` 的字面文本也换掉；
     * ②读改写走 [LocalStore.transformText] 整段持锁，不给并发写插队丢更新。
     */
    fun healReferences(hash: String, path: String) {
        val ref = refOf(hash)
        val targets = LocalStore.messageConvIds().map { LocalStore.messagesFile(it) } +
            listOf(LocalStore.conversationsFile())
        for (file in targets) {
            LocalStore.transformText(file) { text ->
                if (!text.contains(ref)) text else text.replace("\"$ref\"", "\"$path\"")
            }
        }
    }

    /** 该引用对应的本地可显示路径：缓存在用缓存，缓存缺失就用原条目（http 等）兜底 */
    fun resolveToPath(ref: String): String {
        val hash = hashOfRef(ref)
        return cachePathIfPresent(hash) ?: ref
    }

    /**
     * 这张图在**本机**还有没有人引用（1.2.3+ 墓碑护栏）。
     *
     * 未解引用的 `"img:<hash>"`（刚收下来还没自愈的）和已自愈的缓存路径
     * `img_<hash>.jpg` 都数；身份去重后只留下本机原图时，也按图片字段算内容指纹。
     *
     * 为什么需要它：图片是**内容寻址**的，同一张图可能同时被多条消息、
     * 多个对话、甚至**别的设备**引用。以前「缓存文件没了就推墓碑」和
     * 「收到墓碑就删缓存」都是闭着眼删 —— 另一台设备刚发的同图、
     * 或另一条对话里的同图会被连坐删瞎。宁可漏删（占点配额），
     * 也绝不误删（用户看得见的图没了才是事故）。
     */
    fun isReferencedLocally(hash: String): Boolean {
        if (hash.isBlank()) return false
        val ref = refOf(hash)
        val cacheName = cacheFile(hash).name
        val imageFields = setOf("imagePaths", "imageUrls", "quotedImagePath",
            "appearanceImagePath", "appearanceImagePaths")
        fun imageValueReferences(value: JsonElement): Boolean = when {
            value.isJsonArray -> value.asJsonArray.any { imageValueReferences(it) }
            value.isJsonPrimitive && value.asJsonPrimitive.isString -> refForLocal(value.asString) == ref
            else -> false
        }
        fun originalReferenced(value: JsonElement): Boolean = when {
            value.isJsonArray -> value.asJsonArray.any { originalReferenced(it) }
            value.isJsonObject -> value.asJsonObject.entrySet().any { (field, child) ->
                if (field in imageFields) imageValueReferences(child) else originalReferenced(child)
            }
            else -> false
        }
        val targets = LocalStore.messageConvIds().map { LocalStore.messagesFile(it) } +
            listOf(LocalStore.conversationsFile())
        return targets.any { f ->
            val text = runCatching { if (f.isFile) f.readText() else null }.getOrNull() ?: return@any false
            // 保留引用/缓存形态的快速路径。原图回退只读图片字段，正文提到路径不算引用。
            if (text.contains("\"$ref\"") || text.contains(cacheName)) true
            else runCatching { originalReferenced(JsonParser.parseString(text)) }.getOrDefault(false)
        }
    }

    /** 一组条目里的全部 hash（去重保序） */
    fun hashesIn(entries: List<String>): List<String> =
        entries.filter { isRef(it) }.map { hashOfRef(it) }.distinct()

    /**
     * 本机缓存里**现有的**全部图 hash（1.2.3+ 补缺用，G5）。
     *
     * 内容寻址缓存的文件名自带指纹，目录清单就是本机图片存量清单 ——
     * 补缺/首推按它排 IMG 队列。放这里而不是 Adapter：纯 LocalStore 逻辑，
     * JVM 可单测（Adapter 的类初始化要 Android Looper）。
     */
    fun localCacheHashes(): List<String> =
        LocalStore.filesDir().listFiles()?.mapNotNull { hashFromCacheName(it.name) } ?: emptyList()

    private fun writeCache(hash: String, payload: ByteArray): String? = runCatching {
        cacheFile(hash).writeBytes(payload)
        refOf(hash)
    }.getOrNull()
}
