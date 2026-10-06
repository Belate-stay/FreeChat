package com.freechat.sync

import com.freechat.data.LocalStore
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

    /** `img_<16位十六进制>.jpg` → 指纹；不是缓存文件名返回 null */
    fun hashFromCacheName(name: String): String? {
        if (!name.startsWith("img_") || !name.endsWith(".jpg")) return null
        val hash = name.removePrefix("img_").removeSuffix(".jpg")
        return hash.takeIf { it.length == 16 && it.all { c -> c in "0123456789abcdef" } }
    }

    /** data:image/...;base64,xxx → wire 引用（载荷落缓存） */
    fun ensureCachedFromDataUri(uri: String): String? {
        if (!uri.startsWith("data:image")) return null
        val comma = uri.indexOf(',')
        if (comma < 0) return null
        val bytes = runCatching {
            java.util.Base64.getDecoder().decode(uri.substring(comma + 1).filterNot { it.isWhitespace() })
        }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return null
        return writeCache(Wire.hashOf(bytes), bytes)
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
        val payload = ImageCodec.compressForUpload(file) ?: raw.takeIf { it.size <= 500 * 1024 } ?: return null
        return writeCache(hash, payload)
    }

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

    /** 一组条目里的全部 hash（去重保序） */
    fun hashesIn(entries: List<String>): List<String> =
        entries.filter { isRef(it) }.map { hashOfRef(it) }.distinct()

    private fun writeCache(hash: String, payload: ByteArray): String? = runCatching {
        cacheFile(hash).writeBytes(payload)
        refOf(hash)
    }.getOrNull()
}
