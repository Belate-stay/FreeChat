package com.freechat.model

import java.util.UUID

data class Message(
    val id: String = UUID.randomUUID().toString(),
    val role: Role,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val modelName: String? = null,
    val isStreaming: Boolean = false,
    val thinkingTimeMs: Long = 0L,
    val reasoningContent: String = "",  // 深度思考过程
    val imageUrls: List<String> = emptyList(),  // 生图结果（AI）
    val imagePaths: List<String> = emptyList(),  // 用户上传图片的本地文件路径
    val attachmentPath: String? = null,   // 附件文件本地路径（docx/xlsx/pptx 等，用户上传或 AI 生成）
    val attachmentName: String? = null,   // 附件显示名（如「方案.pptx」）
    val quotedText: String? = null,       // 用户引用消息的文本内容（用于气泡内缩略显示，弱化处理）
    val quotedImagePath: String? = null,  // 用户引用消息的图片路径（引用图片时显示缩略图）
    val mode: ChatMode = ChatMode.STANDARD,  // 消息所处模式（历史消息按当时模式显示）
    val favorited: Boolean = false,  // 是否已收藏（收藏夹用）
    val imageContext: String? = null,  // 用户图片消息的识图结果（追问时注入，修复图片上下文断裂）
    /**
     * 这一轮**生成失败**（网络、连接、API 配置、模型限制…）—— 气泡上会挂一枚「生成失败」标识，
     * 并且禁止收藏与分享。
     *
     * 为什么不靠文案判断：各条链路的失败提示千奇百怪（"请稍后重试""HTTP 401"），
     * 而模型正常生成的内容里也可能出现「失败」二字。失败是**产生这条消息的那段代码知道的事**，
     * 所以由它标出来，只在明确的失败分支上置真，不做文本嗅探。
     */
    val failed: Boolean = false
)

/** 收藏夹条目：一条被收藏的消息 + 它所属的对话（收藏夹按对话分类展示用） */
data class FavoriteItem(
    val conversation: Conversation,
    val message: Message
)

enum class Role {
    USER,
    ASSISTANT,
    SYSTEM
}

/** 回复温度风格 */
enum class TempMode(val label: String, val apiValue: Double) {
    AUTO("自动", 1.0),
    WARM("热情", 1.3),
    OBJECTIVE("客观", 0.3);
}

/** 回复长度风格 */
enum class LengthMode(val label: String) {
    AUTO("自动"),
    FULL("完整"),
    CONCISE("精辟");
}
