package com.freechat.qa

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.freechat.data.AppJson
import com.freechat.data.LocalStore
import com.freechat.data.RequestModels
import com.freechat.model.*
import com.freechat.viewmodel.ChatViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.UUID
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/** Debug-only integration check of the actual VM and HTTP transport, with no external API calls. */
class ImageRoutingQaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vm = ViewModelProvider(this)[ChatViewModel::class.java]
        lifecycleScope.launch {
            val server = ImageRoutingFixture()
            val id = "image-routing-qa-${UUID.randomUUID()}"
            try {
                delay(1000) // Do not race the asynchronous settings loader.
                val language = server.model("language-a", ModelType.LANGUAGE)
                val visual = server.model("image-a", ModelType.VISUAL)
                val vision = server.model("vision-a", ModelType.VISION)
                val conversation = Conversation(id = id, title = "路由测试", rules = "QA_RULE：记住用户的喜好并结合上文回答")
                state(vm, "_conversations", vm.conversations.value + conversation)
                state(vm, "_autoSummarizeMemory", true)
                com.freechat.data.MemoryManager().append(id, MemoryEntry(summary = "QA_MEMORY：用户喜欢蓝色的衣服",
                    keywords = listOf("衣服"), kind = "plot"))
                var rows = listOf(Message(role = Role.USER, content = "画一只穿黑裙子的白猫，全身照，水彩风格"))
                suspend fun request(text: String, lang: ModelInfo = language, force: Boolean = false): Message {
                    val requestRows = if (rows.last().role == Role.USER && rows.last().content == text) rows
                        else rows + Message(role = Role.USER, content = text)
                    LocalStore.writeText(LocalStore.messagesFile(id), AppJson.gson.toJson(requestRows))
                    return withContext(RequestModels(lang, visual, vision)) { generate(vm, text, id, requestRows, force, lang, visual) }
                }
                state(vm, "_liveReasoning", "IMAGE QA REASONING")
                val first = request(rows.last().content)
                check(!first.failed && first.imageUrls.size == 1 && first.imagePrompt!!.contains("黑裙"))
                check(java.io.File(first.imageUrls.single()).isFile && server.deliveries == 1) { "Remote result was not saved locally" }
                check(first.reasoningContent == "IMAGE QA REASONING") { "Image completion lost its reasoning" }
                rows = rows + first
                val second = request("再来一张", server.model("mimo-v2.6-flash", ModelType.LANGUAGE))
                check(!second.failed && second.imageUrls.size == 1 && second.imagePrompt!!.contains("水彩"))
                check(server.edits == 1 && server.lastEdit.contains("黑裙") && server.lastEdit.contains("image[]"))
                rows = rows + Message(role = Role.USER, content = "再来一张") + second
                val third = request("背景换成海边", server.model("language-b", ModelType.LANGUAGE))
                check(!third.failed && third.imagePrompt!!.contains("海边") && server.edits == 2)
                check(server.chatRequests == 0) // Clear image requests never depend on LM tool support.
                val generatedAgain = request("一只猫", force = true)
                check(!generatedAgain.failed && generatedAgain.imageUrls.size == 1)
                ChatViewModel::class.java.getDeclaredField("pendingQuoteText").apply { isAccessible = true }.set(vm, "QA_QUOTE")
                val readPicture = request("这张图是什么？")
                check(!readPicture.failed && readPicture.content == "这是一只猫")
                check(server.visionRequests == 1)
                listOf("QA_RULE", "QA_MEMORY", "QA_QUOTE", "黑裙", "data:image/").forEach { marker ->
                    check(server.lastVision.contains(marker)) { "Vision context lost $marker" }
                }
                check(!server.lastVision.contains("\"enable_thinking\":false")) { "Vision disabled an enabled reasoning model" }
                withContext(RequestModels(language, visual, vision)) {
                    analyze(vm, java.util.Base64.getEncoder().encodeToString(java.io.File(first.imageUrls.single()).readBytes()), id, rows,
                        CharacterProfile(name = "QA_PERSONA", dialogueMode = DialogueMode.WECHAT, deepThinkingMode = true))
                }
                listOf("QA_PERSONA", "QA_MEMORY", "QA_RULE", "黑裙").forEach { marker ->
                    check(server.lastVision.contains(marker)) { "Companion vision context lost $marker" }
                }
                // Real Android bounds-only decoding must continue into compression, including >4MB originals.
                withContext(Dispatchers.IO) {
                    val random = java.util.Random(18)
                    val pixels = IntArray(1400 * 1400) { 0xff000000.toInt() or random.nextInt(0x1000000) }
                    val large = Bitmap.createBitmap(pixels, 1400, 1400, Bitmap.Config.ARGB_8888)
                    val bytes = ByteArrayOutputStream().apply { large.compress(Bitmap.CompressFormat.PNG, 100, this) }.toByteArray()
                    large.recycle()
                    check(bytes.size > 4 * 1024 * 1024)
                    val original = java.io.File(filesDir, "large-image-qa.png")
                    LocalStore.writeBytes(original, bytes)
                    val ref = requireNotNull(com.freechat.sync.ImageSync.ensureCached(original.absolutePath))
                    val payload = com.freechat.sync.ImageSync.cacheFile(com.freechat.sync.ImageSync.hashOfRef(ref))
                    check(payload.length() in 1..(4 * 1024 * 1024L) && payload.length() < bytes.size)
                    com.freechat.sync.ImageSync.releaseUnreferenced(listOf(original.absolutePath))
                }
                val native = request("给这段文字配一个合适的视觉作品", server.model("language-native", ModelType.LANGUAGE).copy(supportsThinking = true))
                check(!native.failed && native.imageUrls.size == 1)
                check(native.reasoningContent.orEmpty().contains("NATIVE IMAGE REASONING")) { "Tool image lost model reasoning" }
                val compatible = request("给这段文字配一个合适的视觉作品", server.model("language-rejects-tools", ModelType.LANGUAGE))
                check(!compatible.failed && compatible.imageUrls.size == 1 && server.rejections == 1)
                request("给这段文字配一个合适的视觉作品", server.model("language-rejects-tools", ModelType.LANGUAGE))
                check(server.rejections == 1) // Cached fallback, no rejection loop or extra planner.
                // Actual standard-mode regenerate(), after switching the global language binding.
                state(vm, "_selectedModel", server.model("mimo-v2.6-flash", ModelType.LANGUAGE))
                state(vm, "_selectedVisualModel", visual)
                state(vm, "_autoSummarizeMemory", false)
                LocalStore.writeText(LocalStore.messagesFile(id), AppJson.gson.toJson(listOf(
                    rows.first().copy(content = "一只猫"), first)))
                vm.switchToConversation(Conversation(id = id, title = "路由测试"))
                ChatViewModel::class.java.getDeclaredMethod("persistConversationMessages", String::class.java, List::class.java)
                    .apply { isAccessible = true }.invoke(vm, id, vm.messages.value)
                check(vm.messages.value[1].id == first.id)
                vm.regenerate(1)
                withTimeout(20_000) { while (vm.isLoading.value) delay(50) }
                val replacement = vm.messages.value.last()
                check(replacement.role == Role.ASSISTANT && !replacement.failed && replacement.imageUrls.size == 1)
                check(replacement.id != first.id && replacement.imagePrompt == first.imagePrompt && vm.messages.value.size == 2)
                check(!vm.isGeneratingImage.value && vm.generationModelName.value.isBlank()) { "Image presentation state did not settle" }
                // An edited result must repeat its original edit using the still-retained prior picture.
                rows = listOf(rows.first(), replacement)
                val edited = request("背景换成海边", server.model("language-b", ModelType.LANGUAGE))
                check(!edited.failed && edited.imagePrompt!!.contains("海边"))
                check(edited.imageReferenceMessageId == replacement.id) { "Edit did not record its actual reference" }
                val storedType = object : com.google.gson.reflect.TypeToken<List<Message>>() {}.type
                val editedUser = AppJson.gson.fromJson<List<Message>>(LocalStore.readText(LocalStore.messagesFile(id)), storedType).last()
                val editedRows = rows + editedUser + edited.copy(timestamp = maxOf(edited.timestamp, editedUser.timestamp + 1))
                ChatViewModel::class.java.getDeclaredMethod("persistConversationMessages", String::class.java, List::class.java)
                    .apply { isAccessible = true }.invoke(vm, id, editedRows)
                check(vm.messages.value[3].id == edited.id)
                val editsBefore = server.edits
                vm.regenerate(3)
                withTimeout(20_000) { while (vm.isLoading.value) delay(50) }
                val editedReplacement = vm.messages.value.last()
                check(!editedReplacement.failed && editedReplacement.id != edited.id && vm.messages.value.size == 4)
                check(editedReplacement.imagePrompt == edited.imagePrompt) {
                    "Edited prompt changed: expected=${edited.imagePrompt}, actual=${editedReplacement.imagePrompt}"
                }
                check(server.edits == editsBefore + 1) {
                    "Original edit was not repeated: before=$editsBefore, after=${server.edits}, reference=${editedReplacement.imageReferenceMessageId}"
                }
                check(server.lastEdit.contains("image[]") && server.lastEdit.contains("海边"))
                check(server.chatRequests == 4)
                vm.conversations.value.find { it.id == id }?.let(vm::deleteConversation)
                Log.i("ImageRoutingQA", "PASS edits=${server.edits} vision=2 context=standard+companion local_delivery=1 compression=1 native=1 fallback=2 rejection=1 regeneration=2 chat=${server.chatRequests}")
            } catch (e: Exception) { Log.e("ImageRoutingQA", "FAIL", e) }
            finally {
                server.close()
                vm.conversations.value.filter { it.id.startsWith("image-routing-qa-") }.forEach(vm::deleteConversation)
                LocalStore.messagesFile(id).delete()
                finish()
            }
        }
    }

    private suspend fun analyze(vm: ChatViewModel, base64: String, id: String, rows: List<Message>, character: CharacterProfile): Any =
        suspendCoroutine<Any> { continuation ->
            try {
                val method = ChatViewModel::class.java.getDeclaredMethod("callVisionChat", List::class.java, String::class.java,
                    String::class.java, List::class.java, CharacterProfile::class.java, Continuation::class.java).apply { isAccessible = true }
                val result = method.invoke(vm, listOf(base64 to "image/png"), "结合上文分析图片", id, rows, character, continuation)
                if (result !== COROUTINE_SUSPENDED) continuation.resume(requireNotNull(result))
            } catch (e: Exception) { continuation.resumeWithException(e) }
        }

    @Suppress("UNCHECKED_CAST")
    private fun state(vm: ChatViewModel, name: String, value: Any) {
        val field = ChatViewModel::class.java.getDeclaredField(name).apply { isAccessible = true }
        (field.get(vm) as MutableStateFlow<Any>).value = value
    }

    private suspend fun generate(vm: ChatViewModel, text: String, id: String, rows: List<Message>,
        force: Boolean, language: ModelInfo, visual: ModelInfo): Message = suspendCoroutine { continuation ->
        try {
            val method = ChatViewModel::class.java.getDeclaredMethod("generateReply", String::class.java,
                List::class.java, List::class.java, String::class.java, String::class.java,
                Long::class.javaPrimitiveType, String::class.java, List::class.java,
                Boolean::class.javaPrimitiveType, Continuation::class.java).apply { isAccessible = true }
            val result = method.invoke(vm, text, emptyList<Any>(), emptyList<Any>(), language.displayName,
                visual.displayName, System.currentTimeMillis(), id, rows, force, continuation)
            if (result !== COROUTINE_SUSPENDED) continuation.resume(result as Message)
        } catch (e: Exception) { continuation.resumeWithException(e) }
    }
}

private class ImageRoutingFixture : AutoCloseable {
    private val socket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
    @Volatile var edits = 0
    @Volatile var lastEdit = ""
    @Volatile var chatRequests = 0
    @Volatile var visionRequests = 0
    @Volatile var rejections = 0
    @Volatile var deliveries = 0
    @Volatile var lastVision = ""
    private var generations = 0
    private val png = ByteArrayOutputStream().apply {
        Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(0xff589ba6.toInt()) }
            .compress(Bitmap.CompressFormat.PNG, 100, this)
    }.toByteArray()
    private val imageJson = AppJson.gson.toJson(mapOf("data" to listOf(mapOf("b64_json" to java.util.Base64.getEncoder().encodeToString(png)))))
    init {
        Thread({
            while (!socket.isClosed) try {
                socket.accept().use { connection ->
                    val input = connection.getInputStream().buffered()
                    fun line(): String = buildString { while (true) { val c = input.read(); if (c < 0 || c == 10) break; if (c != 13) append(c.toChar()) } }
                    val path = line().split(' ').getOrNull(1).orEmpty()
                    val headers = generateSequence { line().takeIf(String::isNotEmpty) }.toList()
                    val length = headers.firstOrNull { it.startsWith("Content-Length:", true) }?.substringAfter(':')?.trim()?.toInt() ?: 0
                    require(length in 0..32_000_000)
                    val bytes = ByteArray(length)
                    var count = 0
                    while (count < length) { val n = input.read(bytes, count, length - count); check(n > 0); count += n }
                    val body = bytes.toString(Charsets.UTF_8)
                    var status = "200 OK"
                    val result = if (path == "/delivery.png") { deliveries++; png } else when {
                        path.endsWith("/images/edits") -> { edits++; lastEdit = body; imageJson }
                        path.endsWith("/images/generations") -> {
                            if (generations++ == 0) AppJson.gson.toJson(mapOf("data" to listOf(mapOf("url" to "http://127.0.0.1:${socket.localPort}/delivery.png"))))
                            else imageJson
                        }
                        path.endsWith("/chat/completions") -> {
                            val request = com.google.gson.JsonParser.parseString(body).asJsonObject
                            val model = request["model"].asString
                            if (model == "vision-a") { visionRequests++; lastVision = body; reply("这是一只猫") }
                            else {
                                chatRequests++
                                when {
                                    model == "language-rejects-tools" && request.has("tools") -> {
                                        rejections++; status = "400 Bad Request"; "{\"error\":{\"message\":\"tools is not supported\"}}"
                                    }
                                    model == "language-native" -> AppJson.gson.toJson(mapOf("choices" to listOf(mapOf("message" to mapOf(
                                        "content" to null, "reasoning_content" to "NATIVE IMAGE REASONING", "tool_calls" to listOf(mapOf("id" to "fixture-call", "type" to "function", "function" to mapOf(
                                            "name" to "generate_image", "arguments" to "{\"prompt\":\"一只白猫\"}"))))))))
                                    else -> { check(!request.has("tools")); reply("<freechat_tool>{\"name\":\"generate_image\",\"arguments\":{\"prompt\":\"一只白猫\"}}</freechat_tool>") }
                                }
                            }
                        }
                        else -> error("Unexpected fixture route: $path")
                    }.toByteArray(Charsets.UTF_8)
                    connection.getOutputStream().apply {
                        write("HTTP/1.1 $status\r\nContent-Type: ${if (path == "/delivery.png") "image/png" else "application/json"}\r\nContent-Length: ${result.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        write(result); flush()
                    }
                }
            } catch (e: Exception) { if (!socket.isClosed) Log.e("ImageRoutingQA", "Fixture failure", e) }
        }, "image-routing-fixture").apply { isDaemon = true; start() }
    }
    fun model(id: String, type: ModelType) = ModelInfo(id, id, Provider.CUSTOM, supportsWebSearch = false,
        supportsThinking = false, supportsDeepThinking = true, deepThinkingDefault = true,
        modelType = type, apiBaseUrl = "http://127.0.0.1:${socket.localPort}/v1", apiKey = "fixture-key")
    private fun reply(content: String) = AppJson.gson.toJson(mapOf("choices" to listOf(mapOf("message" to mapOf("content" to content)))))
    override fun close() { socket.close() }
}
