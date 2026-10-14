package com.freechat.qa

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.freechat.data.AppJson
import com.freechat.data.GenerationPhase
import com.freechat.data.LocalStore
import com.freechat.model.*
import com.freechat.ui.animation.PageMotion
import com.freechat.ui.animation.LocalMotionEnabled
import com.freechat.ui.screens.*
import com.freechat.ui.theme.*
import com.freechat.viewmodel.ChatViewModel
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Offline, debug-only regression scene using the production chat/settings/input components. */
class ChatPresentationQaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val vm = ViewModelProvider(this)[ChatViewModel::class.java]
        lifecycleScope.launch {
            delay(1000) // Let the real settings loader finish before installing the isolated fixture.
            val language = ModelInfo("qa-language", "QA Language", Provider.CUSTOM,
                supportsDeepThinking = true, deepThinkingDefault = true)
            state(vm, "_selectedModel", language)
            state(vm, "_showThinking", true)
            state(vm, "_autoSummarizeMemory", false)
            state(vm, "_inputStyle", if (intent.getBooleanExtra("compact", false)) InputStyle.COMPACT else InputStyle.COMPLETE)
            val chats = listOf(
                Conversation(id = "presentation-qa-a", title = "A"),
                Conversation(id = "presentation-qa-b", title = "B"),
                Conversation(id = "presentation-qa-action", title = "Action", mode = ChatMode.COMPANION,
                    characterProfile = CharacterProfile(name = "QA", dialogueMode = if (intent.getBooleanExtra("plot", false))
                        DialogueMode.PLOT else DialogueMode.ACTION, deepThinkingMode = true)),
                Conversation(id = "presentation-qa-wechat", title = "Wechat", mode = ChatMode.COMPANION,
                    characterProfile = CharacterProfile(name = "QA", dialogueMode = DialogueMode.WECHAT, deepThinkingMode = true)),
            )
            chats.forEach { chat ->
                val rows = (0..59).map { n -> Message(id = "${chat.id}-$n",
                    role = if (n % 2 == 0) Role.USER else Role.ASSISTANT,
                    content = "${chat.title} ROW $n\n" + "阅读位置与消息内容保持连续。".repeat(3),
                    reasoningContent = if (n % 2 == 1) "THOUGHT ${chat.title} $n\n" + "推理内容只应出现在标准模式。".repeat(5) else "",
                    timestamp = 1_750_000_000_000L + n * 1000, mode = chat.mode,
                    modelName = if (n % 2 == 1) language.displayName else null) }
                LocalStore.writeText(LocalStore.messagesFile(chat.id), AppJson.gson.toJson(rows))
            }
            state(vm, "_conversations", chats)
            vm.switchToConversation(chats.first())
            setContent {
                FreeChatTheme(if (intent.getBooleanExtra("dark", false)) ThemeMode.DARK else ThemeMode.LIGHT,
                    ColorTheme.PINE) {
                    CompositionLocalProvider(LocalAdvancedMaterial provides intent.getBooleanExtra("advanced", false),
                        LocalMotionEnabled provides !intent.getBooleanExtra("reduced", false)) {
                        var page by remember { mutableStateOf("chat") }
                        var generating by remember { mutableStateOf(false) }
                        val haze = remember { HazeState() }
                        LaunchedEffect(generating) {
                            var elapsed = 0L
                            while (generating) {
                                state(vm, "_thinkingTimeMs", elapsed)
                                elapsed += 100
                                delay(100)
                            }
                        }
                        Column(Modifier.fillMaxSize()) {
                            Row(Modifier.fillMaxWidth().statusBarsPadding(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                chats.forEach { chat -> TextButton(onClick = {
                                    generating = false
                                    vm.currentConversationId.value?.let { id ->
                                        ChatViewModel::class.java.getDeclaredMethod("setConvLoading", String::class.java, Boolean::class.javaPrimitiveType)
                                            .apply { isAccessible = true }.invoke(vm, id, false)
                                    }
                                    vm.switchToConversation(chat)
                                    page = "chat"
                                }) { Text(chat.title) } }
                                TextButton(onClick = { page = "settings" }) { Text("设置") }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                TextButton(onClick = {
                                    vm.currentConversationId.value?.let { id ->
                                        val image = vm.currentMode.value != ChatMode.COMPANION ||
                                            vm.currentCharacter.value?.dialogueMode != DialogueMode.WECHAT
                                        ChatViewModel::class.java.getDeclaredMethod("setConvLoading", String::class.java, Boolean::class.javaPrimitiveType)
                                            .apply { isAccessible = true }.invoke(vm, id, true)
                                        ChatViewModel::class.java.getDeclaredMethod("setGenerationModel", String::class.java, String::class.java, Boolean::class.javaPrimitiveType)
                                            .apply { isAccessible = true }.invoke(vm, id, if (image) "QA Image Model" else "QA Language", image)
                                        state(vm, "_generationPhase", if (image) GenerationPhase.IMAGE_GENERATING else GenerationPhase.DRAFTING)
                                        state(vm, "_liveReasoning", "LIVE IMAGE THOUGHT: 思考过程在生图期间应保持可见。")
                                        generating = true
                                    }
                                }) { Text("出图") }
                                TextButton(onClick = { page = "chat" }) { Text("返回") }
                                TextButton(onClick = {
                                    chats.forEach { Log.i("ChatPresentationQA", "${it.title}: ${vm.chatSessionState.viewport(it.id)} " +
                                        "expanded=${vm.chatSessionState.reasoningExpanded(it.id, "${it.id}-59")}") }
                                }) { Text("状态") }
                            }
                            Box(Modifier.weight(1f)) {
                                PageMotion(page, 24f, forward = { _, target -> target != "chat" }) { route ->
                                    when (route) {
                                        "settings" -> SettingsScreen(vm, false, onBack = { page = "chat" }, onOpenVoiceDebug = {}, onOpenChangelog = {})
                                        "rules" -> NewRulesScreen(vm, vm.currentConversationId.value.orEmpty(), false, onBack = { page = "chat" })
                                        "character" -> CharacterSetupScreen(vm, false, onBack = { page = "chat" }, onCreate = {}, initial = vm.currentCharacter.value)
                                        else -> ChatScreen(vm, false, onOpenDrawer = {}, onOpenNewRules = { page = "rules" },
                                            onOpenCharacterSetup = { page = "character" }, hazeState = haze)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun state(vm: ChatViewModel, name: String, value: Any) {
        val field = ChatViewModel::class.java.getDeclaredField(name).apply { isAccessible = true }
        (field.get(vm) as MutableStateFlow<Any>).value = value
    }
}
