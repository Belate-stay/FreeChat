package com.freechat.ui.components

import com.freechat.model.ChatMode
import com.freechat.model.Message
import com.freechat.model.Role
import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationImageGalleryTest {
    @Test fun uploadsGenerationsAndCompanionScenesShareMessageOrder() {
        val messages = listOf(
            Message(id = "upload", role = Role.USER, content = "", imagePaths = listOf("/photos/a.jpg", "/photos/b.jpg")),
            Message(id = "generated", role = Role.ASSISTANT, content = "", imageUrls = listOf("https://example.org/generated.png")),
            Message(id = "text", role = Role.ASSISTANT, content = "Only text"),
            Message(id = "scene", role = Role.ASSISTANT, content = "Current scene", mode = ChatMode.COMPANION,
                sceneVisualization = true, imageUrls = listOf("https://example.org/scene.png")),
        )

        assertEquals(listOf(
            PreviewImage("upload", "/photos/a.jpg"), PreviewImage("upload", "/photos/b.jpg"),
            PreviewImage("generated", "https://example.org/generated.png"),
            PreviewImage("scene", "https://example.org/scene.png"),
        ), ConversationImageGallery.images(messages))
    }

    @Test fun repeatedSourceInAnotherMessageStillHasItsOwnSelectionIdentity() {
        val source = "https://example.org/reused.png"
        val images = ConversationImageGallery.images(listOf(
            Message(id = "first", role = Role.ASSISTANT, content = "", imageUrls = listOf(source)),
            Message(id = "second", role = Role.ASSISTANT, content = "", imageUrls = listOf(source)),
        ))

        assertEquals(listOf(PreviewImage("first", source), PreviewImage("second", source)), images)
        assertEquals(1, images.indexOf(PreviewImage("second", source)))
    }

    @Test fun duplicateResultsWithinOneMessageAreNotExtraGalleryPages() {
        val source = "https://example.org/generated.png"
        val message = Message(id = "generated", role = Role.ASSISTANT, content = "",
            imageUrls = listOf(source, source))

        assertEquals(listOf(PreviewImage("generated", source)), ConversationImageGallery.images(listOf(message)))
    }

    @Test fun galleryNeverRetainsPicturesFromAPreviousConversation() {
        val old = Message(id = "old", role = Role.ASSISTANT, content = "", imageUrls = listOf("https://example.org/old.png"))
        val current = Message(id = "current", role = Role.USER, content = "", imagePaths = listOf("/photos/current.jpg"))
        ConversationImageGallery.images(listOf(old))

        assertEquals(listOf(PreviewImage("current", "/photos/current.jpg")),
            ConversationImageGallery.images(listOf(current)))
    }
}
