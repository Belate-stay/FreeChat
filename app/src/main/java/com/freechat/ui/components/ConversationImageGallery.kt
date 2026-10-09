package com.freechat.ui.components

import com.freechat.model.Message

data class PreviewImage(val messageId: String, val source: String) {
    val isLocal: Boolean get() = ImageDisplayPolicy.isLocal(source)
    val key: String get() = "$messageId:$source" // Pager keys must be saveable in an Android Bundle.
}

object ConversationImageGallery {
    /** Uses the same generated-image identities as chat thumbnails. Call on IO. */
    fun images(messages: List<Message>): List<PreviewImage> = messages.flatMap { message ->
        (message.imagePaths.orEmpty() + ImageDisplayPolicy.generatedImages(message))
            .filter { it.isNotBlank() }.distinct().map { PreviewImage(message.id, it) }
    }
}
