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
    val mode: ChatMode = ChatMode.STANDARD  // 消息所处模式（历史消息按当时模式显示）
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
