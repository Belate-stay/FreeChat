package com.freechat.sync

import com.freechat.data.AppJson
import com.freechat.data.LocalStore
import com.freechat.data.healed
import com.freechat.model.CharacterProfile
import com.freechat.model.Conversation
import com.freechat.model.MemoryEntry
import com.freechat.model.Message
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 本地模型 ↔ 线上格式的翻译，以及**设备本地字段的剥离与还原**。
 *
 * ## 线上格式就是两端的模型本身
 *
 * 网页端的 `src/core/types.ts` 开头写着一句「与安卓端 com.freechat.model 一一对应」，
 * 字段名、枚举取值都照抄安卓 —— 所以这里**不需要另立一套 schema**，
 * 直接把模型序列化成 JSON 就是线上格式。多出来的字段（安卓 1.0.34 加的
 * `supportingCast` / `worldRules` / `userPersona` / `aiCreativity` / `proactiveEnabled` /
 * `referencePrototype`）网页端不认识，但它是纯 JSON 透传的，不认识也会原样留着。
 *
 * ## 需要翻译的只有「设备本地字段」
 *
 * 有几类字段**在这个设备上才有意义**，传上去只会污染另一端：
 *
 * | 字段 | 为什么不能传 |
 * |---|---|
 * | `Message.imagePaths` / `attachmentPath` / `quotedImagePath` | 本机的绝对路径（`/data/user/0/.../img_xxx.jpg`），另一端根本读不到；而且把 App 内部目录结构传上服务器没有任何好处 |
 * | `Message.imageUrls` 里的本地路径 | 同上 —— 生图返回 base64 时会被存成本机 `gen_*.png`，和远端 URL 混在同一个列表里 |
 * | `CharacterProfile.avatarPath` | 绝对路径，同上。但**头像本身要同步** —— 剥掉路径、换成 base64 本体 + 内容指纹，见下面「角色头像」一节 |
 * | `CharacterProfile.appearanceImagePath(s)` / `appearanceImageDescs` | 形象参考图（用户自己上传的图，体积大），仍只同步文字 |
 * | `CharacterProfile.languageModelId` / `visionModelId` | 本机自定义模型的 id，另一端没有这个模型，同步过去只会选中一个点不通的项 |
 *
 * **路径与识图结果是成对使用的**（`CharacterSetupScreen` 用 `filterIndexed` 按下标一起删、
 * `ChatViewModel` 用 `getOrNull(i)` 按下标取），所以 `appearanceImageDescs` 必须和
 * `appearanceImagePaths` **一起剥**：只留文字不留图，下标就会对不上，
 * 描述会被安到另一张图上 —— 那是"看起来没坏但内容错了"的那类 bug。
 *
 * 剥离是**一进一出**的：推上去时挖空，拉下来时用本地那份填回来。
 * 只做前者会让别的设备把我们本地的头像清掉，只做后者则会一直把本地路径传上去。
 */
object Wire {

    // ============================================================
    //  对话
    // ============================================================

    /** 对话 → 线上格式（挖掉设备本地字段） */
    fun convToWire(conv: Conversation): JsonObject {
        val obj = AppJson.gson.toJsonTree(conv).asJsonObject
        val profile = obj.getAsJsonObject("characterProfile") ?: return obj
        // 头像：**本体跟着角色卡一起走**（另一台设备/网页端根本没有这个文件，只给路径等于什么都没给）
        val avatar = readFile(conv.characterProfile?.avatarPath)
        profile.addProperty(K_AVATAR_HASH, avatar?.let { hashOf(it) } ?: "")
        profile.addProperty(
            K_AVATAR_DATA,
            if (avatar != null && avatar.size <= MAX_INLINE_AVATAR) {
                android.util.Base64.encodeToString(avatar, android.util.Base64.NO_WRAP)
            } else ""
        )
        // 「我确实没有头像」和「我不知道」是两回事 —— 只有前者才该让云端跟着清掉（见类注释）
        profile.addProperty(
            K_AVATAR_CLEARED,
            avatar == null && !conv.characterProfile?.avatarHash.isNullOrEmpty()
        )
        profile.addProperty("avatarPath", "")
        profile.addProperty("appearanceImagePath", "")
        profile.add("appearanceImagePaths", JsonArray())
        profile.add("appearanceImageDescs", JsonArray())
        profile.addProperty("languageModelId", "")
        profile.addProperty("visionModelId", "")
        return obj
    }

    /**
     * 线上格式 → 对话。
     *
     * 一定要用 [AppJson.gson] 解：它注册了 `InstanceCreator`，缺的键会保持字段声明的默认值；
     * 换一个裸 `Gson()` 来解，网页端没写的那些键在 Kotlin 里就是 null ——
     * 类型声明拦不住反射赋值，用户下一次开口就崩。见 `AppJson` 的注释。
     *
     * [local] 是本地同名的那条（没有就传 null）：设备本地字段从它取回，其余以远端为准。
     * 返回 null 表示远端那份压根解析不出来（宁可当成本地没有变化，也不能拿半个对象覆盖）。
     */
    fun convFromWire(el: JsonElement, local: Conversation?): Conversation? {
        val remote = runCatching { AppJson.gson.fromJson(el, Conversation::class.java) }.getOrNull() ?: return null
        val healed = remote.healed()
        val lp = local?.characterProfile
        val avatar = adoptAvatar(el, lp)
        return healed.copy(
            characterProfile = healed.characterProfile?.copy(
                avatarPath = avatar.path,
                avatarHash = avatar.hash,
                appearanceImagePath = lp?.appearanceImagePath ?: "",
                appearanceImagePaths = lp?.appearanceImagePaths ?: emptyList(),
                appearanceImageDescs = lp?.appearanceImageDescs ?: emptyList(),
                languageModelId = lp?.languageModelId ?: "",
                visionModelId = lp?.visionModelId ?: ""
            )
        )
    }

    /** 推送用的 updatedAt：有天然时间戳就用天然的，**它不是冲突判定的依据**（冲突一律看版本号） */
    fun convUpdatedAt(conv: Conversation?): Long = conv?.updatedAt ?: System.currentTimeMillis()

    // ============================================================
    //  角色头像 —— 跟着角色卡走
    // ============================================================

    /**
     * 角色的头像是**角色卡的一部分**，两台设备看到的必须是同一张脸。
     *
     * 麻烦在于头像本体是二进制、而且本机存的是绝对路径（`/data/user/0/…/avatar_17….jpg`），
     * 路径传过去对面根本读不到。所以这里做的是「**把字节编进角色卡**」：路径照旧剥掉，
     * 换成一串 base64 和一个内容指纹（[CharacterProfile.avatarHash]）。
     *
     * 为什么不做成"单独一个头像对象、需要时再拉"：那样得给头像再建一套版本号/墓碑/引用计数
     * （头像删了以后那份字节谁来回收？），而头像本来就小（裁好的方图，几十 KB），
     * 跟着角色卡一起走是最省事也最不容易出错的路子。代价是改一次标题会把头像重推一遍 ——
     * 一条对话一箱子的粒度下，这点流量比多养一套机制划算。
     *
     * ## 三态是关键，别简化成两态
     *
     * | 线上 | 意思 |
     * |---|---|
     * | `avatarData` 有值 | 这就是头像本体，拿去用 |
     * | `avatarCleared = true` | 我**明确没有**头像了（用户删的），你也跟着清掉 |
     * | 两者都没有 | **我不知道** —— 一个字都别动，保持你本地那份 |
     *
     * 第三态不能省：一台刚装上、还没下到头像的设备如果被当成"我没有头像"，
     * 它下一次推角色卡就会把云端那张脸抹掉，而且两边都收不到任何提示。
     */

    private const val K_AVATAR_DATA = "avatarData"
    private const val K_AVATAR_HASH = "avatarHash"
    private const val K_AVATAR_CLEARED = "avatarCleared"

    /**
     * 内联进角色卡的上限。超过就不带本体（只带指纹）—— 单单一个畸形大图不该把整条对话
     * 顶到服务端那个 16MB 的对象上限上去。正常裁剪出来的头像离这个数差着两个数量级。
     */
    private const val MAX_INLINE_AVATAR = 2 * 1024 * 1024

    /** 本机已有的头像文件字节；没有/读不动就是 null */
    private fun readFile(path: String?): ByteArray? {
        val p = path?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { java.io.File(p).takeIf { it.isFile }?.readBytes() }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    /** 内容指纹：16 位十六进制（sha256 前 8 字节）。够区分两张图，又不至于把角色卡顶大 */
    fun hashOf(bytes: ByteArray): String = try {
        val md = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
        buildString { for (i in 0 until 8) append("%02x".format(md[i])) }
    } catch (_: Exception) {
        // 理论上不会发生（SHA-256 是 JDK 必备）。真发生了也不能让整条对话推不上去，
        // 退回一个"每次都不一样"的指纹：对方会重写一遍图，功能照常，只是白费一点流量
        bytes.size.toString() + "-" + System.currentTimeMillis().toString(16)
    }

    private data class AdoptedAvatar(val path: String, val hash: String)

    /**
     * 把线上那份头像落到本地，返回本地该用的 (path, hash)。
     *
     * 三种情况（对应上面那张表）：
     *  · 有本体且指纹和本地记的一样、本地文件还在 → 什么都不做（**别白写一次盘**，这条路径每轮同步都在跑）
     *  · 有本体但和本地那张不一样 → 落成一个按指纹命名的文件，两端从此指的同一张图
     *  · 明确清空 → 把本地那张删掉
     *  · 什么都没说 → 本地怎么样就怎么样
     */
    private fun adoptAvatar(wire: JsonElement, lp: CharacterProfile?): AdoptedAvatar {
        val keep = AdoptedAvatar(lp?.avatarPath ?: "", lp?.avatarHash ?: "")
        val profile = wire.takeIf { it.isJsonObject }?.asJsonObject
            ?.getAsJsonObject("characterProfile") ?: return keep
        val hash = profile.get(K_AVATAR_HASH)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        val data = profile.get(K_AVATAR_DATA)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        val cleared = profile.get(K_AVATAR_CLEARED)?.takeIf { it.isJsonPrimitive }?.asBoolean == true

        if (data.isNotEmpty() && hash.isNotEmpty()) {
            if (hash == keep.hash && keep.path.isNotBlank() && java.io.File(keep.path).isFile) return keep
            val bytes = runCatching { android.util.Base64.decode(data, android.util.Base64.DEFAULT) }.getOrNull()
                ?: return keep
            if (bytes.isEmpty()) return keep
            val file = java.io.File(LocalStore.filesDir(), "avatar_$hash.jpg")
            // 同一个指纹的文件名是固定的：两台设备、两条对话撞上同一张图也只会有一份
            runCatching {
                if (!file.isFile) file.writeBytes(bytes)
            }.onFailure { return keep }
            return AdoptedAvatar(file.absolutePath, hash)
        }

        if (cleared) {
            if (keep.path.isNotBlank()) runCatching { java.io.File(keep.path).delete() }
            return AdoptedAvatar("", "")
        }

        return keep
    }

    // ============================================================
    //  消息
    // ============================================================

    fun msgsToWire(msgs: List<Message>): JsonArray {
        val arr = JsonArray()
        for (m in msgs) {
            val obj = AppJson.gson.toJsonTree(m).asJsonObject
            obj.add("imagePaths", JsonArray())
            obj.add("imageUrls", JsonArray().apply {
                // 只留真正的网络地址：生图走 base64 时这里存的是本机文件路径，传上去是死链
                m.imageUrls.filter { it.startsWith("http://") || it.startsWith("https://") }.forEach { add(it) }
            })
            obj.addProperty("attachmentPath", null as String?)
            obj.addProperty("quotedImagePath", null as String?)
            arr.add(obj)
        }
        return arr
    }

    /**
     * 线上格式 → 消息列表。
     *
     * [local] 是本地同一对话的消息，用来把设备本地字段填回去：
     * 一条消息只要有 id 对得上，就保留本机那份的图片/附件路径 ——
     * 消息内容以远端为准（那边可能是最新的），但图只能在本地这台机器上看。
     *
     * 远端来的新消息没有本地对应项，这些字段就是空的，符合预期。
     */
    fun msgsFromWire(el: JsonElement, local: List<Message>): List<Message> {
        val type = object : TypeToken<List<Message>>() {}.type
        val remote = runCatching { AppJson.gson.fromJson<List<Message>>(el, type) }.getOrNull() ?: return local
        val localById = local.associateBy { it.id }
        return remote.map { m ->
            val heal = m.healed()
            val mine = localById[heal.id]
            if (mine == null) heal.copy(
                imagePaths = emptyList(),
                attachmentPath = null,
                quotedImagePath = null,
                imageUrls = heal.imageUrls.filter { it.startsWith("http://") || it.startsWith("https://") }
            ) else heal.copy(
                imagePaths = mine.imagePaths,
                attachmentPath = mine.attachmentPath,
                quotedImagePath = mine.quotedImagePath,
                // 网络地址两边都留着；本机路径原本就在 mine.imageUrls 里，取并集不丢
                imageUrls = (heal.imageUrls + mine.imageUrls).distinct()
            )
        }
    }

    /** 消息数组的 updatedAt：最后一条消息的时间 */
    fun msgsUpdatedAt(msgs: List<Message>): Long =
        msgs.lastOrNull()?.timestamp ?: System.currentTimeMillis()

    // ============================================================
    //  记忆
    // ============================================================

    fun memsToWire(list: List<MemoryEntry>): JsonArray =
        AppJson.gson.toJsonTree(list).asJsonArray

    /**
     * 线上格式 → 记忆列表。
     *
     * 记忆条目**没有**注册 InstanceCreator（见 `AppJson`），所以缺键就是 null，
     * 必须逐条过一遍 [MemoryManager.healed] 那套补全 —— 这正是
     * 「拟人模式所有请求都失败」那个 bug 的同一个入口，只是这次 JSON 来自另一台设备。
     */
    fun memsFromWire(el: JsonElement): List<MemoryEntry>? {
        val type = object : TypeToken<List<MemoryEntry>>() {}.type
        val list = runCatching { AppJson.gson.fromJson<List<MemoryEntry>>(el, type) }.getOrNull() ?: return null
        return list.map { it.healed() }
    }

    fun memsUpdatedAt(list: List<MemoryEntry>): Long =
        list.maxOfOrNull { it.timestamp } ?: System.currentTimeMillis()

    // ============================================================
    //  小工具
    // ============================================================

    /** 稳定序列化（键序无关）—— 判断「本地这份和云端那份一字不差吗」用 */
    fun stableJson(el: JsonElement?): String {
        if (el == null || el.isJsonNull) return "null"
        if (el.isJsonArray) return "[" + el.asJsonArray.joinToString(",") { stableJson(it) } + "]"
        if (el.isJsonObject) {
            val o = el.asJsonObject
            return "{" + o.keySet().sorted().joinToString(",") { "\"$it\":${stableJson(o.get(it))}" } + "}"
        }
        return el.toString()
    }

    suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }
}
