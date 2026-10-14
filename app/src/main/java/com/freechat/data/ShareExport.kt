package com.freechat.data

import com.freechat.model.CharacterExport
import com.freechat.model.CharacterProfile
import com.freechat.model.ChatMode
import com.freechat.model.MemoryEntry
import com.freechat.model.Message
import com.freechat.model.PerConvSettings
import com.freechat.model.Role
import com.freechat.model.SearchCitation
import com.freechat.model.toExport
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.Base64

/**
 * 拟人角色「分享导出/导入」的纯逻辑层（不碰 Android Context，编解码走 ByteArray/String）。
 * UI 只负责勾选项与系统分享；文件读写、建对话在 ChatViewModel。
 */
object ShareExport {
    const val KIND = "freechat-share"
    const val VERSION = 1

    /**
     * 勾选项（1.1.26.3 两层化）。「角色设定」与其子项「提示词」必选恒开；
     * 头像/形象参考图是角色设定的子项，场景图是聊天记录的子项（文字消息必选恒开，状态不进这里）。
     */
    data class Options(
        val persona: Boolean = true,
        val avatar: Boolean = true,
        val appearance: Boolean = true,
        val chats: Boolean = true,
        val sceneImages: Boolean = false,
        val memories: Boolean = true
    ) {
        val any: Boolean get() = persona || chats || memories
    }

    /** desc = 形象参考图的识图描述（导入后「生效」靠它），头像/场景图用不到留空 */
    data class ShareImage(val data: String = "", val mime: String = "image/jpeg", val desc: String = "")

    /** 导出消息只留文字字段；图/附件在 [toShareMessage] 就剥掉。勾「场景图」的视觉行带 [sceneImage] */
    data class ShareMessage(
        val id: String = "",
        val role: Role = Role.USER,
        val content: String = "",
        val timestamp: Long = 0,
        val modelName: String? = null,
        val thinkingTimeMs: Long = 0L,
        val reasoningContent: String = "",
        val quotedText: String? = null,
        val mode: Int = 0,
        val favorited: Boolean = false,
        val failed: Boolean = false,
        val searchSources: List<SearchCitation>? = emptyList(),
        val sceneVisualization: Boolean = false,
        val sceneImage: ShareImage? = null
    )

    /** 对话壳/元信息：勾不勾「对话记录」都在，记忆绑定靠它 */
    data class ShareConversation(
        val id: String = "",
        val title: String = "",
        val mode: Int = 0,
        val createdAt: Long = 0,
        val updatedAt: Long = 0,
        val profile: CharacterExport? = null,
        val messages: List<ShareMessage> = emptyList(),
        val deletedMessageIds: List<String> = emptyList(),
        val deletedVisualMessageIds: List<String> = emptyList()
    )

    data class ShareMemory(
        val convId: String = "",
        val entries: List<MemoryEntry> = emptyList(),
        val mood: PerConvSettings? = null
    )

    data class SharePackage(
        val kind: String = KIND,
        val version: Int = VERSION,
        val name: String = "",
        val exportedAt: Long = 0,
        // persona = CharacterExport 全字段平铺 + dialogueMode/plotLength/avatar/appearanceImages
        val persona: JsonObject? = null,
        val conversations: List<ShareConversation> = emptyList(),
        val memories: List<ShareMemory> = emptyList()
    )

    // ---------- 消息：文字-only 双向转换 ----------

    /**
     * 视觉行只有勾了「场景图」（调用方解析出 [sceneImage]）才导，按原位置留在消息序列里；
     * 图没了的视觉行不导。剥掉图字段后 content 仍空白的文字行也不导。
     */
    fun toShareMessage(m: Message, sceneImage: ShareImage? = null): ShareMessage? {
        if (m.sceneVisualization) {
            if (sceneImage == null) return null
            return ShareMessage(
                id = m.id, role = m.role, content = m.content, timestamp = m.timestamp,
                mode = m.mode.ordinal, favorited = m.favorited, failed = m.failed,
                sceneVisualization = true, sceneImage = sceneImage
            )
        }
        if (m.content.isBlank()) return null
        return ShareMessage(
            id = m.id, role = m.role, content = m.content, timestamp = m.timestamp,
            modelName = m.modelName, thinkingTimeMs = m.thinkingTimeMs,
            reasoningContent = m.reasoningContent, quotedText = m.quotedText,
            mode = m.mode.ordinal, favorited = m.favorited, failed = m.failed,
            searchSources = m.searchSources
        )
    }

    /**
     * 消息 id 原样保留（记忆 sourceMessageIds 依赖它）；场景图由调用方落盘后传 [sceneImagePath]。
     * 场景图进 **imageUrls**（1.1.26.5 修）——助手行的图只从 imageUrls 渲染
     * （ChatBubble.generatedImages/ImageDisplayPolicy），写 imagePaths 会显示不出来；
     * 与生成时 `imageUrls = urls` 的构造对齐。
     */
    fun toMessage(s: ShareMessage, sceneImagePath: String = ""): Message = Message(
        id = s.id, role = s.role, content = s.content, timestamp = s.timestamp,
        modelName = s.modelName, isStreaming = false, thinkingTimeMs = s.thinkingTimeMs,
        reasoningContent = s.reasoningContent, quotedText = s.quotedText,
        imageUrls = if (sceneImagePath.isNotEmpty()) listOf(sceneImagePath) else emptyList(),
        mode = ChatMode.entries.getOrElse(s.mode) { ChatMode.COMPANION },
        favorited = s.favorited, failed = s.failed, searchSources = s.searchSources,
        sceneVisualization = s.sceneVisualization
    )

    // ---------- persona ----------

    /** CharacterExport 平铺 + 图/模式扩展，写成 schema 里的 persona 节 */
    fun personaFrom(profile: CharacterProfile, avatar: String?, images: List<ShareImage>): JsonObject {
        val obj = AppJson.gson.toJsonTree(profile.toExport()).asJsonObject
        obj.addProperty("dialogueMode", profile.dialogueMode)
        obj.addProperty("plotLength", profile.plotLength)
        avatar?.let { obj.addProperty("avatar", it) }
        if (images.isNotEmpty()) obj.add("appearanceImages", AppJson.gson.toJsonTree(images))
        return obj
    }

    /**
     * persona 节 → 角色档案。头像/形象图由调用方先落盘，这里只把路径填进去。
     * 无 persona 节时用对话自带的 [CharacterExport] 文本字段兜底（无头像）。
     */
    fun personaToProfile(
        persona: JsonObject?,
        fallbackExport: CharacterExport?,
        avatarPath: String = "",
        appearanceImagePaths: List<String> = emptyList(),
        appearanceImageDescs: List<String> = emptyList()
    ): CharacterProfile? {
        val export = when {
            persona != null -> AppJson.gson.fromJson(persona, CharacterExport::class.java)
            fallbackExport != null -> fallbackExport
            else -> return null
        } ?: return null
        val dialogueMode = persona?.get("dialogueMode")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0
        val plotLength = persona?.get("plotLength")?.takeIf { it.isJsonPrimitive }?.asInt ?: 1
        return export.toProfile().copy(
            dialogueMode = dialogueMode,
            plotLength = plotLength,
            avatarPath = avatarPath,
            appearanceImagePaths = appearanceImagePaths,
            appearanceImageDescs = appearanceImageDescs
        )
    }

    fun personaAvatar(persona: JsonObject?): String? =
        persona?.get("avatar")?.takeIf { it.isJsonPrimitive }?.asString

    fun personaImages(persona: JsonObject?): List<ShareImage> =
        persona?.get("appearanceImages")?.takeIf { it.isJsonArray }?.let { arr ->
            AppJson.gson.fromJson(arr, Array<ShareImage>::class.java).orEmpty().toList()
        }.orEmpty()

    // ---------- 构建 / 解析 ----------

    /** 三 false 返回 null（至少选一项） */
    fun build(
        options: Options,
        name: String,
        persona: JsonObject?,
        conversations: List<ShareConversation>,
        memories: List<ShareMemory>,
        exportedAt: Long = System.currentTimeMillis()
    ): String? {
        if (!options.any) return null
        return AppJson.gson.toJson(
            SharePackage(
                kind = KIND, version = VERSION, name = name, exportedAt = exportedAt,
                persona = persona, conversations = conversations, memories = memories
            )
        )
    }

    fun isShareFormat(json: String): Boolean = try {
        val root = JsonParser.parseString(json)
        root.isJsonObject && root.asJsonObject.get("kind")
            ?.takeIf { it.isJsonPrimitive }?.asString == KIND
    } catch (_: Exception) { false }

    /** 新格式解析。非本格式 / 损坏返回 null */
    fun parse(json: String): SharePackage? {
        return try {
            val root = JsonParser.parseString(json)
            if (!root.isJsonObject) return null
            val obj = root.asJsonObject
            if (obj.get("kind")?.takeIf { it.isJsonPrimitive }?.asString != KIND) return null
            val pkg = AppJson.gson.fromJson(obj, SharePackage::class.java) ?: return null
            pkg.copy(
                conversations = pkg.conversations.orEmpty().map { it.healed() },
                memories = pkg.memories.orEmpty().map { it.healed() }
            )
        } catch (_: Exception) { null }
    }

    /** 老格式 CharacterExport（无 kind 节）。解析失败 / 缺角色名返回 null —— 与原 importCharacterFromJson 同语义 */
    fun parseLegacyCharacter(json: String): CharacterProfile? {
        return try {
            val exp = AppJson.gson.fromJson(json, CharacterExport::class.java) ?: return null
            if (exp.name.isBlank()) null else exp.toProfile()
        } catch (_: Exception) { null }
    }

    /** 导入时把对话壳/memories 的 convId 换成新 id（同机导入不能撞 id） */
    fun remapConvIds(pkg: SharePackage, idMap: Map<String, String>): SharePackage = pkg.copy(
        conversations = pkg.conversations.map { it.copy(id = idMap[it.id] ?: it.id) },
        memories = pkg.memories.map { it.copy(convId = idMap[it.convId] ?: it.convId) }
    )

    // ---------- base64（纯 JVM，测试可跑） ----------

    fun encodeBase64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    fun decodeBase64(data: String): ByteArray? = try {
        Base64.getDecoder().decode(data)
    } catch (_: Exception) { null }

    // ---------- 空值疗愈（Gson 反射造对象会把缺键留成 null） ----------

    @Suppress("USELESS_ELVIS", "SENSELESS_COMPARISON")
    private fun ShareConversation.healed(): ShareConversation = copy(
        id = id ?: "", title = title ?: "",
        messages = messages ?: emptyList(),
        deletedMessageIds = deletedMessageIds ?: emptyList(),
        deletedVisualMessageIds = deletedVisualMessageIds ?: emptyList()
    )

    @Suppress("USELESS_ELVIS", "SENSELESS_COMPARISON")
    private fun ShareMemory.healed(): ShareMemory = copy(
        convId = convId ?: "",
        entries = entries.orEmpty().map { it.healed() }
    )
}
