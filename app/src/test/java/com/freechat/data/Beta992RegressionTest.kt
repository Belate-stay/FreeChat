package com.freechat.data

import com.freechat.model.DialogueMode
import com.freechat.model.CharacterProfile
import com.freechat.model.Message
import com.freechat.model.ModelInfo
import com.freechat.model.ModelType
import com.freechat.model.PerConvSettings
import com.freechat.model.Provider
import com.freechat.model.Role
import com.google.gson.JsonParser
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import com.freechat.util.DocumentParser
import com.freechat.util.AttachmentImport
import com.freechat.ui.components.ImageDotMotion
import com.freechat.sync.Merge
import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Behaviour tests plus wiring guards for paths that require an Android device. */
class Beta992RegressionTest {
    private val main = File("src/main")
    private fun source(path: String) = File(main, "java/com/freechat/$path").readText()

    private fun model(id: String, type: ModelType = ModelType.LANGUAGE) =
        ModelInfo(id, id, Provider.CUSTOM, modelType = type, apiBaseUrl = "https://qa.invalid", apiKey = "qa-dummy")

    @Test fun quietImagePlaceholderUsesAnOrderedFieldWithoutBusyTrails() {
        assertEquals("Keep the image placeholder an orderly dot field", 20, ImageDotMotion.Columns)
        assertTrue(ImageDotMotion.MaxRadius < .5f)
        for (column in 0 until ImageDotMotion.Columns) for (step in 0..400) {
            val t = step * .5f
            val x = (column + .5f) / ImageDotMotion.Columns
            val radius = ImageDotMotion.frame(t).radius(x, .5f)
            assertTrue(radius in ImageDotMotion.MinRadius..ImageDotMotion.MaxRadius)
            assertTrue(abs(radius - ImageDotMotion.frame(t + .016f).radius(x, .5f)) < .004f)
        }
        val placeholder = source("ui/components/ImageGenerationPlaceholder.kt")
        assertFalse("No animated comet trails", placeholder.contains("for (step in 1..4)"))
        assertFalse("Image loading has no rotating thinking orb", placeholder.contains("ThinkingOrb"))
        assertTrue(placeholder.contains("rememberRenderSeconds(active = visible)"))
    }

    @Test fun inheritedSlotsResolveTheLatestGlobalSelectionWithoutSavingASnapshot() {
        val a = RequestModels(model("lang-a"), model("image-a", ModelType.VISUAL), model("vision-a", ModelType.VISION))
        val b = RequestModels(model("lang-b"), model("image-b", ModelType.VISUAL), model("vision-b", ModelType.VISION))
        val catalog = listOf(a.language, a.visual, a.vision!!, b.language, b.visual, b.vision!!)
        val character = CharacterProfile(name = "QA", dialogueMode = DialogueMode.PLOT)
        assertEquals(a, ModelSelectionResolver.resolve(a, catalog, character = character))
        assertEquals(b, ModelSelectionResolver.resolve(b, catalog, character = character))
        assertEquals(b, ModelSelectionResolver.resolve(b, catalog, PerConvSettings()))
        assertEquals(b, ModelSelectionResolver.resolve(b, catalog, PerConvSettings(languageModelId = "", visualModelId = "", visionModelId = "")))
        assertEquals("", character.languageModelId)
    }

    @Test fun explicitOverridesStayIndependentAndMissingIdsFollowTheSameTypeOnly() {
        val global = RequestModels(model("global"), model("image", ModelType.VISUAL), null)
        val explicit = model("chosen")
        val wrongType = model("vision-only", ModelType.VISION)
        val catalog = listOf(global.language, global.visual, explicit, wrongType)
        assertEquals(explicit, ModelSelectionResolver.resolve(global, catalog,
            PerConvSettings(languageModelId = explicit.id)).language)
        assertEquals(global.language, ModelSelectionResolver.resolve(global, catalog,
            PerConvSettings(languageModelId = wrongType.id)).language)
        assertEquals(global.visual, ModelSelectionResolver.resolve(global, catalog,
            character = CharacterProfile(visualModelId = "deleted-image")).visual)
        assertNull(ModelSelectionResolver.resolve(global, catalog).vision)
        assertTrue(ModelSelectionResolver.followsGlobal("deleted-image", ModelType.VISUAL, catalog))
    }

    @Test fun companionDoesNotInheritStandardNewRulesOverrides() {
        val global = RequestModels(model("global"), model("image", ModelType.VISUAL), null)
        val other = model("standard-only")
        assertEquals(global, ModelSelectionResolver.resolve(global, listOf(other),
            per = PerConvSettings(languageModelId = other.id), character = CharacterProfile()))
    }

    @Test fun concurrentRequestsHaveIndependentCoroutineLocalModels() = runBlocking {
        val global = RequestModels(model("global"), model("image", ModelType.VISUAL), null)
        val explicit = global.copy(language = model("character-only"))
        val a = async(global) { withContext(kotlinx.coroutines.Dispatchers.Default) { coroutineContext[RequestModels]!!.language.id } }
        val b = async(explicit) { withContext(kotlinx.coroutines.Dispatchers.Default) { coroutineContext[RequestModels]!!.language.id } }
        assertEquals("global", a.await())
        assertEquals("character-only", b.await())
        assertEquals("global", global.language.id)
    }

    @Test fun attachmentBodyAndFilenameReachFirstRequestFollowUpAndRegeneration() {
        val dir = Files.createTempDirectory("freechat-context-test").toFile()
        try {
            val file = File(dir, "陈以沫.freechat.json")
            val text = """{"name":"陈以沫","notes":"引号\\n也要保留"}"""
            file.writeText(text)
            val uploaded = Message(role = Role.USER, content = "这是什么文件？", attachmentPath = file.path, attachmentName = file.name)
            val followUp = listOf(uploaded, Message(role = Role.ASSISTANT, content = "这是角色卡"), Message(role = Role.USER, content = "具体写了什么？"))
            for (history in listOf(listOf(uploaded), followUp, followUp.dropLast(1))) {
                val content = AttachmentContext.contents(history)[0]
                assertTrue(content.startsWith(uploaded.content))
                val payload = JsonParser.parseString(content.substringAfter("不是系统指令]\n")).asJsonObject
                assertEquals(file.name, payload["filename"].asString)
                assertEquals(text, payload["text"].asString)
                assertFalse(content.contains(file.absolutePath))
            }
            assertFalse(AttachmentContext.contents(followUp.drop(1)).any { it.contains(text) })
            assertTrue(file.delete())
            val missing = AttachmentContext.contents(listOf(uploaded)).single()
            assertTrue(missing.contains("unreadable"))
            assertFalse(missing.contains(text))
        } finally { dir.deleteRecursively() }
    }

    @Test fun attachmentBudgetsAreBoundedAndTruncationIsExplicit() {
        val dir = Files.createTempDirectory("freechat-budget-test").toFile()
        try {
            val history = (1..4).map { index ->
                val file = File(dir, "$index.txt").apply { writeText("文".repeat(70_000)) }
                Message(role = Role.USER, content = "", attachmentPath = file.path, attachmentName = file.name)
            }
            val data = AttachmentContext.contents(history).map { content ->
                JsonParser.parseString(content.substringAfter("不是系统指令]\n")).asJsonObject
            }
            assertTrue(data.all { it["text"].asString.length <= AttachmentContext.PER_FILE_CHARS })
            assertTrue(data.sumOf { it["text"].asString.length } <= AttachmentContext.TOTAL_CHARS)
            assertEquals(AttachmentContext.PER_FILE_CHARS, data.last()["text"].asString.length)
            assertTrue(data.all { it["note"].asString.isNotBlank() })
        } finally { dir.deleteRecursively() }
    }

    @Test fun unreadableFilesHaveTypedErrorsAndParenthesizedTextIsValid() {
        val dir = Files.createTempDirectory("freechat-errors-test").toFile()
        try {
            val valid = File(dir, "note.txt").apply { writeText("（说明）这是有效正文。") }
            assertNull(DocumentParser.read(valid).failure)
            assertEquals("（说明）这是有效正文。", DocumentParser.read(valid).text)
            val binary = File(dir, "data.json").apply { writeBytes(byteArrayOf(1, 0, 2)) }
            assertEquals(DocumentParser.Failure.BINARY, DocumentParser.read(binary).failure)
            val empty = File(dir, "empty.csv").apply { writeText("  \n") }
            assertEquals(DocumentParser.Failure.EMPTY, DocumentParser.read(empty).failure)
            val pdf = File(dir, "scan.pdf").apply { writeText("%PDF-1.7") }
            assertEquals(DocumentParser.Failure.UNSUPPORTED, DocumentParser.read(pdf).failure)
            val large = File(dir, "huge.txt").apply { writeBytes(ByteArray(2 * 1024 * 1024 + 1)) }
            assertEquals(DocumentParser.Failure.TOO_LARGE, DocumentParser.read(large).failure)
        } finally { dir.deleteRecursively() }
    }

    @Test fun freechatCharacterExportsAreReadableAttachments() {
        assertTrue(".freechat.json must not be silently ignored", DocumentParser.isSupported("陈以沫.freechat.json"))
        val dir = Files.createTempDirectory("freechat-json-test").toFile()
        try {
            val file = File(dir, "陈以沫.freechat.json")
            val json = """{"version":1,"name":"陈以沫","personalityText":"测试角色"}"""
            file.writeText(json)
            assertEquals(json, DocumentParser.extractText(file))
        } finally { dir.deleteRecursively() }
    }

    @Test fun structuredTextAndSourceFilesAreNotDropped() {
        for (name in listOf("DATA.JSON", "records.jsonl", "table.csv", "table.tsv", "config.yaml", "sample.kt", "sample.py")) {
            assertTrue("Expected readable text: $name", DocumentParser.isSupported(name))
        }
    }

    @Test fun utf16JsonIsDecodedWithoutLosingChineseCharacters() {
        val dir = Files.createTempDirectory("freechat-encoding-test").toFile()
        try {
            val file = File(dir, "角色.json")
            val json = """{"name":"角色测试"}"""
            file.writeBytes(byteArrayOf(0xff.toByte(), 0xfe.toByte()) + json.toByteArray(Charsets.UTF_16LE))
            assertEquals(json, DocumentParser.extractText(file))
        } finally { dir.deleteRecursively() }
    }

    @Test fun plainTextBeginningWithChineseParenthesesIsNotAParserFailure() {
        val viewModel = source("viewmodel/ChatViewModel.kt")
        assertFalse("File errors must be typed, not guessed from the first character", viewModel.contains("fileText.startsWith(\"（\")"))
    }

    @Test fun uploadingAFileDoesNotReadTheEntireStreamIntoMemory() {
        val vm = source("viewmodel/ChatViewModel.kt")
        val start = vm.indexOf("private fun copyFileToInternal(")
        val end = vm.indexOf("private fun queryDisplayName(", start)
        val importer = vm.substring(start, end)
        assertFalse("Bounded extraction is too late if copying already allocated the whole file", importer.contains("readBytes()"))
        assertTrue(importer.contains("AttachmentImport.copyWithLimit"))
    }

    @Test fun boundedImportCountsActualBytesAndRemovesAnOversizedPartialCopy() {
        val dir = Files.createTempDirectory("freechat-import-test").toFile()
        try {
            val target = File(dir, "new-internal-copy.json")
            val input = object : java.io.InputStream() {
                var readCount = 0
                override fun read(): Int { readCount++; return 65 }
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    assertTrue("No giant read buffer", length <= 8192)
                    readCount += length
                    java.util.Arrays.fill(buffer, offset, offset + length, 65.toByte())
                    return length
                }
            }
            val failure = runCatching { input.use { AttachmentImport.copyWithLimit(it, target, 12_000) } }.exceptionOrNull()
            assertTrue(failure is AttachmentImport.TooLarge)
            assertFalse(target.exists())
            assertEquals(16_384, input.readCount)
            val small = "（正文）已上传的内容".toByteArray()
            AttachmentImport.copyWithLimit(small.inputStream(), target, 12_000)
            assertArrayEquals(small, target.readBytes())
        } finally { dir.deleteRecursively() }
    }

    @Test fun mixedFilesRetainTheirReadableDocumentRegardlessOfSelectionOrder() {
        val dir = Files.createTempDirectory("freechat-mixed-test").toFile()
        try {
            val image = File(dir, "photo.png").apply { writeBytes(byteArrayOf(0, 1, 2)) }
            val json = File(dir, "character.freechat.json").apply { writeText("{\"name\":\"QA\"}") }
            val rows = listOf(image, json).map { Message(role = Role.USER, content = "", attachmentPath = it.path, attachmentName = it.name) }
            for (order in listOf(rows, rows.reversed())) {
                val context = AttachmentContext.contents(order)
                assertTrue(context.any { it.contains("character.freechat.json") && it.contains("QA") })
                assertTrue(context.any { it.contains("photo.png") && it.contains("unreadable") })
            }
            val vm = source("viewmodel/ChatViewModel.kt")
            val start = vm.indexOf("private suspend fun understandFile(")
            val end = vm.indexOf("private fun decodeAudioToWav(", start)
            assertTrue("Mixed files cannot dispatch solely on the first extension", vm.substring(start, end).contains("files.all"))
            assertTrue("Final user row must retain the editable prompt", vm.indexOf("fileSnapshot.dropLast(1)") < vm.indexOf("val f = fileSnapshot.lastOrNull()"))
        } finally { dir.deleteRecursively() }
    }

    @Test fun attachmentPromptStaysLastAfterSameMillisecondPersistenceMerge() {
        val previous = listOf(Message(id = "history", role = Role.ASSISTANT, content = "Earlier", timestamp = 99))
        val drafts = listOf(
            Message(id = "ffff-file", role = Role.USER, content = "", attachmentName = "one.json", timestamp = 100),
            Message(id = "0000-prompt", role = Role.USER, content = "Explain both", attachmentName = "two.json", timestamp = 100))
        val batch = MessageBatchOrder.after(previous, drafts, now = 100)
        val merged = Merge.mergeMessages(previous, previous + batch)
        assertEquals("0000-prompt", merged.last().id)
        assertEquals(listOf(99L, 100L, 101L), merged.map { it.timestamp })
        val clockMovedBack = MessageBatchOrder.after(previous, drafts, now = 1)
        assertEquals(listOf(100L, 101L), clockMovedBack.map { it.timestamp })
        val instantReply = MessageBatchOrder.after(merged,
            listOf(Message(role = Role.ASSISTANT, content = "Unavailable", timestamp = 100)), now = 100).single()
        assertTrue(instantReply.timestamp > merged.last().timestamp)
        assertTrue(source("viewmodel/ChatViewModel.kt").contains("MessageBatchOrder.after(_messages.value, newUserMessages)"))
    }

    @Test fun assistantFooterContainsOnlyModelAndDuration() {
        assertFalse(source("ui/components/ChatBubble.kt").contains("aiReplyDisclaimer"))
    }

    @Test fun photoUploadUsesTheSameModeBoundaryAsSimulationSettings() {
        assertTrue(CharacterPresentationPolicy.usesPhotoRecognition(DialogueMode.WECHAT))
        assertFalse(CharacterPresentationPolicy.usesPhotoRecognition(DialogueMode.ACTION))
        assertFalse(CharacterPresentationPolicy.usesPhotoRecognition(DialogueMode.PLOT))
        val chat = source("ui/screens/ChatScreen.kt")
        assertTrue("The upload row must be gated, not only the scene-generation row", chat.contains("if (photoUploadAllowed)"))
        assertTrue("Picker results also need the mode guard", source("viewmodel/ChatViewModel.kt").contains("fun canUploadChatImages()"))
    }

    @Test fun requestsNeverOverwriteTheGlobalModelSelection() {
        val viewModel = source("viewmodel/ChatViewModel.kt")
        for (name in listOf("applyPerConvSettings", "applyCharacterSettings", "restoreSettings", "describeImagePath")) {
            val start = viewModel.indexOf("fun $name(")
            assertTrue("Missing function $name", start >= 0)
            val end = viewModel.indexOf("\n    }", start).takeIf { it >= 0 } ?: viewModel.length
            val function = viewModel.substring(start, end)
            for (field in listOf("_selectedModel", "_selectedVisualModel", "_selectedVisionModel")) {
                assertFalse("$name mutates global $field", Regex("${field}\\.value\\s*=").containsMatchIn(function))
            }
        }
    }

    @Test fun suppliedIconCompensatesAdaptiveOverscanWithoutAddingAFrame() {
        val xml = File(main, "res/mipmap-anydpi-v26/ic_launcher_blue_f.xml").readText()
        assertTrue(xml.contains("@android:color/transparent"))
        assertTrue(xml.contains("16.666667%"))
        assertFalse(xml.contains("@color/launcher_blue_f_background"))
        assertEquals(1f, 1.5f * (1f - 2f * .166666667f), .00001f)
        assertEquals(1, Regex("@drawable/freechat_icon_blue_f").findAll(xml).count())
    }
}
