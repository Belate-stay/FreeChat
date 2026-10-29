package com.freechat.qa

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.freechat.MainActivity
import com.freechat.BuildConfig
import com.freechat.data.AppJson
import com.freechat.data.LocalStore
import com.freechat.data.SettingsRepository
import com.freechat.model.*
import com.freechat.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

/** Offline data in isolated emulator userdata only. Never included in release. */
class TabletSeedQaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            val repo = SettingsRepository(applicationContext)
            repo.saveHasAgreedTerms(!intent.getBooleanExtra("gate", false))
            repo.saveLanguageCode("zh-CN")
            repo.saveThemeMode(if (intent.getBooleanExtra("dark", false)) ThemeMode.DARK.ordinal else ThemeMode.LIGHT.ordinal)
            repo.saveColorTheme(ColorTheme.PINE.ordinal)
            repo.saveFontSize(intent.getIntExtra("font", FontSize.MEDIUM.ordinal))
            repo.saveInputStyle(intent.getIntExtra("inputStyle", InputStyle.COMPACT.ordinal))
            repo.saveInputBarState(intent.getIntExtra("inputState", InputBarState.AUTO_HIDE.ordinal))
            repo.saveAdvancedMaterial(intent.getBooleanExtra("advanced", true))
            repo.saveLiquidBackdrop(intent.getBooleanExtra("liquid", false))
            repo.saveHeaderBarStyle(HeaderBarStyle.CARD)
            repo.saveTabletDisplayMode(if (intent.getBooleanExtra("floating", false)) TabletDisplayMode.FLOATING else TabletDisplayMode.EMBEDDED)
            repo.saveTabletFloatingSide(TabletFloatingSide.BOTH)
            repo.saveTabletUnderlyingTouch(intent.getBooleanExtra("underlyingTouch", true))
            val offline = ModelInfo(id = "tablet-offline", displayName = "Offline QA", provider = Provider.CUSTOM,
                apiBaseUrl = "http://127.0.0.1:18764/v1", apiKey = "offline-test",
                supportsDeepThinking = true, deepThinkingDefault = true)
            repo.saveCustomModels(listOf(offline))
            repo.saveSelectedModel(offline.id)
            repo.saveShowThinking(true)
            val chats = listOf(
                Conversation(id = "tablet-standard", title = "设计与灵感", messageCount = 24, isPinned = true),
                Conversation(id = "tablet-role", title = "林间书房", messageCount = 18,
                    mode = ChatMode.COMPANION, characterProfile = CharacterProfile(name = "林间书房",
                        age = "24", dialogueMode = DialogueMode.PLOT, personalityText = "自然、温和，喜欢读书与散步。",
                        relationshipPreset = "朋友", relationshipText = "一起阅读、记录日常的朋友。",
                        personaPrompt = "保持自然温和的叙事风格。", languageModelId = offline.id)),
                Conversation(id = "tablet-wechat", title = "晚风", messageCount = 18,
                    mode = ChatMode.COMPANION, characterProfile = CharacterProfile(name = "晚风", age = "23",
                        dialogueMode = DialogueMode.WECHAT, personaPrompt = "自然口语沟通。", languageModelId = offline.id)),
            ) + (if (intent.getBooleanExtra("shortChats", false)) listOf(
                Conversation(id = "tablet-short", title = "短对话 QA", messageCount = 2, isPinned = true),
                Conversation(id = "tablet-empty", title = "空对话 QA", messageCount = 0, isPinned = true,
                    deletedMessageIds = listOf("removed-offline-fixture")),
                Conversation(id = "tablet-short-role", title = "短剧情 QA", messageCount = 2, isPinned = true,
                    mode = ChatMode.COMPANION, characterProfile = CharacterProfile(name = "短剧情角色",
                        dialogueMode = DialogueMode.PLOT, languageModelId = offline.id)),
            ) else emptyList()) + (1..12).map { Conversation(id = "tablet-history-$it", title = "阅读笔记 $it", messageCount = 2) }
            for (chat in chats) {
                val rows = (0 until chat.messageCount).map { n -> Message(id = "${chat.id}-$n",
                    role = if (n % 2 == 0) Role.USER else Role.ASSISTANT,
                    content = if (n % 2 == 0) "聊聊今天阅读的内容，也为下一次讨论留下几个问题。"
                        else "阅读不是把所有答案记住，而是逐渐看见自己的问题。\n\n我们可以先从最有共鸣的一段开始，再慢慢梳理它和日常生活的联系。",
                    reasoningContent = if (chat.mode == ChatMode.STANDARD && n % 2 == 1) "先理解问题，再结合前文梳理表达。" else "",
                    favorited = n == 3 || n == 7,
                    timestamp = 1_792_000_000_000L + n * 60_000L,
                    mode = chat.mode, modelName = if (n % 2 == 1) offline.displayName else null) }
                LocalStore.writeText(LocalStore.messagesFile(chat.id), AppJson.gson.toJson(rows))
            }
            LocalStore.writeText(LocalStore.conversationsFile(), AppJson.gson.toJson(chats))
            getSharedPreferences("feature_rec", MODE_PRIVATE).edit()
                .putInt("last_shown", if (intent.getBooleanExtra("feature", false)) 0 else BuildConfig.VERSION_CODE).apply()
            startActivity(Intent(this@TabletSeedQaActivity, MainActivity::class.java))
            finish()
        }
    }
}

/** Controls real per-conversation busy state while the production settings stay on screen. */
class TabletBusyQaReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val vm = ChatViewModel.live() ?: return
        val id = vm.currentConversationId.value ?: return
        val phase = intent.getStringExtra("phase").orEmpty()
        if (phase == "notice") { vm.forceUpdateNotice(); return }
        if (phase == "draft") {
            vm.saveDraft(intent.getStringExtra("conversation") ?: id, intent.getStringExtra("text").orEmpty())
            return
        }
        if (phase == "groupImage") {
            val image = java.io.File(context.filesDir, "qa_group_pixel.png")
            val bitmap = android.graphics.Bitmap.createBitmap(64, 64, android.graphics.Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.rgb(55, 125, 210))
            image.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            vm.addPendingImages(listOf(android.net.Uri.fromFile(image)))
            return
        }
        for ((method, enabled) in listOf("setConvLoading" to (phase == "thinking"), "setConvTyping" to (phase == "typing"))) {
            ChatViewModel::class.java.getDeclaredMethod(method, String::class.java, Boolean::class.javaPrimitiveType)
                .apply { isAccessible = true }.invoke(vm, id, enabled)
        }
        Log.i("TabletQA", "target=$id phase=$phase busy=${vm.isConversationBusy(id)}")
        if (vm.isConversationBusy(id)) {
            val old = vm.currentCharacter.value
            if (old != null) Log.i("TabletQA", "busyCommitRejected=${!vm.updateCharacter(id, old.copy(name = "MUST NOT SAVE"))}")
        }
    }
}
