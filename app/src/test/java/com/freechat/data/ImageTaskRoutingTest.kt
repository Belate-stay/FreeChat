package com.freechat.data

import com.freechat.model.Message
import com.freechat.model.Role
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class ImageTaskRoutingTest {
    private fun user(text: String) = Message(role = Role.USER, content = text)
    private val picture = Message(id = "picture", role = Role.ASSISTANT, content = "",
        imageUrls = listOf("https://example.invalid/cat.png"))
    private val history = listOf(user("画一只穿黑裙子的白猫，全身照，水彩风格"), picture)

    private fun plan(text: String, rows: List<Message> = emptyList(), images: List<String> = emptyList(),
        force: Boolean = false): ImageTaskRouting.Plan? = ImageTaskRouting.plan(text, images, rows, force)

    @Test fun standardModeDispatchesImagesBeforeAskingTheLanguageModelForTools() {
        val vm = File("src/main/java/com/freechat/viewmodel/ChatViewModel.kt").readText()
        val reply = vm.substringAfter("private suspend fun generateReply(").substringBefore("private suspend fun executeToolCall(")
        assertTrue("All language models must share the direct image route", reply.contains("ImageTaskRouting.plan("))
        assertTrue(reply.indexOf("ImageTaskRouting.plan(") < reply.indexOf("callDeepSeekApiStreaming("))
        assertTrue(reply.contains("executeImageTask("))
    }

    @Test fun anotherPictureUsesThePreviousPromptAndRetainedPicture() {
        listOf("再来一张", "重新生成一张", "重画一下", "再生成", "再画一张", "Generate another one").forEach { text ->
            val request = plan(text, history + user(text))
            assertEquals(text, "EDIT", requireNotNull(request).kind.name)
            assertTrue(requireNotNull(request).prompt.contains("穿黑裙子的白猫"))
            assertTrue(requireNotNull(request).prompt.contains(text))
            assertEquals(listOf("https://example.invalid/cat.png"), requireNotNull(request).referenceSources)
        }
    }

    @Test fun pictureEditsWithoutARepeatedUploadStillCallTheImageModel() {
        listOf("不要大头照，要全身照", "背景换成海边", "亮一点", "Make the background blue").forEach { text ->
            assertEquals("EDIT", requireNotNull(plan(text, history)).kind.name)
        }
    }

    @Test fun aFreshPictureDoesNotInheritAnUnrelatedPreviousPicture() {
        listOf("画一只宇航员小狗", "画一只宇航员小狗，全身照", "生成一张清晰一点的海边图片").forEach { text ->
            val request = requireNotNull(plan(text, history))
            assertEquals(text, "GENERATE", request.kind.name)
            assertEquals(text, request.prompt)
            assertEquals(emptyList<String>(), request.referenceSources)
        }
    }

    @Test fun capabilityQuestionsAndImageInstructionsAreNotImageGenerationCommands() {
        listOf("你能生成图片吗？", "你能画一张图片吗？", "Can you draw an image?", "Can you generate images?",
            "Could you create a photo?", "How do I generate an image?", "不要生成图片，只写提示词", "生成一个随机数").forEach {
            assertNull(it, plan(it, history))
        }
    }

    @Test fun normalConversationDoesNotBecomeAnEditJustBecauseAnOlderPictureExists() {
        val rows = history + user("介绍一下 Kotlin") + Message(role = Role.ASSISTANT, content = "Kotlin 是编程语言")
        assertNull(plan("改成 Python", rows))
        assertNull(plan("再解释一遍", rows))
    }

    @Test fun failedStreamingAndDeletedPicturesCannotBeReferences() {
        assertNull(plan("再来一张", listOf(picture.copy(failed = true))))
        assertNull(plan("再来一张", listOf(picture.copy(isStreaming = true))))
        assertNull(plan("再来一张", listOf(picture.copy(imageUrls = emptyList()))))
        assertNull(plan("再来一张", emptyList()))
    }

    @Test fun uploadedPicturesUseVisionOrEditsWithoutInvolvingTheLanguageModel() {
        val refs = listOf("/uploads/photo.png")
        assertEquals("VISION", requireNotNull(plan("这是什么？", images = refs)).kind.name)
        assertEquals("EDIT", requireNotNull(plan("背景换成海边", images = refs)).kind.name)
        assertEquals(refs, requireNotNull(plan("背景换成海边", images = refs)).referenceSources)
        assertEquals("GENERATE", requireNotNull(plan("一只猫", force = true)).kind.name)
    }

    @Test fun theExecutedPromptSurvivesSerializationAndEntersTheLanguageContext() {
        val row = picture.copy(imagePrompt = "白猫，黑裙，水彩，全身照", content = "")
        val saved = AppJson.gson.fromJson(AppJson.gson.toJson(row), Message::class.java)
        val task = plan("再来一张", listOf(user("沿用这个构图"), saved))
        assertTrue(requireNotNull(task).prompt.contains("黑裙"))
        val contents = AttachmentContext.contents(listOf(saved)).single()
        assertTrue("Later language models must receive the actual image requirement", contents.contains("黑裙"))
        assertFalse(contents.contains("https://example.invalid"))
    }

    @Test fun askingAboutThePreviousPictureUsesVisionRatherThanRedrawingIt() {
        assertEquals("VISION", requireNotNull(plan("这张图是什么？", history)).kind.name)
    }

    @Test fun politeConcreteImageRequestsStillGenerate() {
        listOf("你能帮我画一只猫吗？", "你能帮我生成一张黑猫的图片吗？", "你能帮我生成一个黑猫的头像吗？",
            "Can you draw a cat?", "Can you generate an image of a cat?", "Could you create a photo of a red panda?").forEach {
            assertEquals("GENERATE", requireNotNull(plan(it)).kind.name)
        }
    }

    @Test fun repeatingANonImageTaskDoesNotReuseAnOlderPicture() {
        listOf("再生成这个报告", "重新生成这篇故事").forEach { assertNull(it, plan(it, history)) }
    }

    @Test fun aBareRepeatFollowsTheLatestReplyNotAnOlderPicture() {
        val rows = history + user("写一封邮件") + Message(role = Role.ASSISTANT, content = "你好，这是一封邮件。")
        listOf("重新生成", "再生成一次", "重画一下", "重新画一张", "Generate again").forEach {
            assertNull(it, plan(it, rows + user(it)))
        }
        val linked = requireNotNull(plan("上一张图片重新生成", rows))
        assertEquals("EDIT", linked.kind.name)
        assertEquals(picture.imageUrls, linked.referenceSources)
    }

    @Test fun regenerationPreservesTheEditAndExecutedPromptNotTheCompositePromptsGenerationWords() {
        val original = picture.copy(imagePrompt = "画一只穿黑裙子的白猫，全身照，水彩风格")
        val rows = listOf(user(original.imagePrompt!!), original, user("背景换成海边"))
        val edit = requireNotNull(plan("背景换成海边", rows))
        val reply = picture.copy(id = "edited", imagePrompt = edit.prompt, imageReferenceMessageId = original.id)
        val saved = AppJson.gson.fromJson(AppJson.gson.toJson(reply), Message::class.java)
        listOf("背景换成海边", "以这个形象为基础换个构图").forEach { text ->
            val request = ImageTaskRouting.regeneration(text, emptyList(), rows, saved)
            assertEquals("EDIT", request.kind.name)
            assertEquals(reply.imagePrompt, request.prompt)
            assertEquals(original.id, request.referenceMessageId)
            assertEquals(original.imageUrls, request.referenceSources)
        }
        // Old rows without the optional reference field still classify the original user request.
        val legacy = ImageTaskRouting.regeneration("背景换成海边", emptyList(), rows, reply.copy(imageReferenceMessageId = null))
        assertEquals("EDIT", legacy.kind.name)
        assertEquals(reply.imagePrompt, legacy.prompt)
    }

    @Test fun regenerationNeverReplacesADeletedEditReferenceWithAnUnrelatedPicture() {
        val reply = picture.copy(id = "edited", imagePrompt = "画一只猫，海边背景", imageReferenceMessageId = picture.id)
        val request = ImageTaskRouting.regeneration("背景换成海边", emptyList(), listOf(picture.copy(id = "different")), reply)
        assertEquals("EDIT", request.kind.name)
        assertEquals(picture.id, request.referenceMessageId)
        assertTrue(request.referenceSources.isEmpty())
    }

    @Test fun regenerationRetainsExplicitImageIntentForAPlainSubjectWithoutUsingOlderPictures() {
        val reply = picture.copy(imagePrompt = "一只宇航员猫")
        val request = ImageTaskRouting.regeneration("一只宇航员猫", emptyList(), history, reply)
        assertEquals("GENERATE", request.kind.name)
        assertEquals(reply.imagePrompt, request.prompt)
        assertTrue(request.referenceSources.isEmpty())
    }
}
