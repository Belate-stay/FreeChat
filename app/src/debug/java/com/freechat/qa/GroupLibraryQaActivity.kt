package com.freechat.qa

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.freechat.BuildConfig
import com.freechat.MainActivity
import com.freechat.data.*
import com.freechat.model.*
import kotlinx.coroutines.launch

/** Synthetic loopback-only fixture in the task-owned emulator. No production/user records. */
class GroupLibraryQaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.i("FreeChat1222QA", "seed start: ${intent.extras}")
        lifecycleScope.launch {
            val repo = SettingsRepository(applicationContext)
            repo.saveHasAgreedTerms(true)
            repo.saveLanguageCode("zh-CN")
            repo.saveThemeMode(if (intent.getBooleanExtra("dark", false)) ThemeMode.DARK.ordinal else ThemeMode.LIGHT.ordinal)
            repo.saveColorTheme(ColorTheme.PINE.ordinal)
            repo.saveAdvancedMaterial(intent.getBooleanExtra("advanced", true))
            repo.saveFontSize(intent.getIntExtra("font", FontSize.MEDIUM.ordinal))
            repo.saveInputStyle(intent.getIntExtra("inputStyle", InputStyle.COMPACT.ordinal))
            repo.saveInputBarState(InputBarState.PINNED.ordinal)
            repo.saveTabletDisplayMode(if (intent.getBooleanExtra("floating", false)) TabletDisplayMode.FLOATING else TabletDisplayMode.EMBEDDED)
            repo.saveTabletUnderlyingTouch(true)
            repo.saveEnableWebSearch(false)
            repo.saveAutoSummarizeMemory(intent.getBooleanExtra("memory", false))
            repo.saveClaudeDeleted(true)
            repo.saveIntroDeleted(true)
            val models = listOf(
                ModelInfo("qa-group", "QA Group Language", Provider.CUSTOM, apiBaseUrl = "http://127.0.0.1:18766/v1", apiKey = "local-fixture",
                    supportsDeepThinking = true, deepThinkingDefault = false),
                ModelInfo("qa-vision", "QA Group Vision", Provider.CUSTOM, apiBaseUrl = "http://127.0.0.1:18766/v1", apiKey = "local-fixture",
                    modelType = ModelType.VISION),
                ModelInfo("qa-image", "QA Image", Provider.CUSTOM, apiBaseUrl = "http://127.0.0.1:18766/v1", apiKey = "local-fixture",
                    modelType = ModelType.VISUAL))
            repo.saveCustomModels(models)
            repo.saveSelectedModel("qa-group")
            repo.saveSelectedVisionModel("qa-vision")
            repo.saveSelectedVisualModel("qa-image")
            val names = listOf("林予", "周屿", "许青", "沈禾", "季川")
            val folder = CharacterLibraryStore.state.value.folders.firstOrNull { it.name == "校园剧本" }
                ?: CharacterLibraryStore.createFolder("校园剧本")
            names.forEachIndexed { index, name ->
                if (CharacterLibraryStore.state.value.cards.none { it.persona.name == name })
                    CharacterLibraryStore.add(CharacterPersona(name = name, age = "23", personalityText = "自然、独立，有自己的生活",
                        personaPrompt = "你是$name，用自然口语交流，不替别人说话。", originalLearningText = "这是${name}的合成语言参考。"), folder.id)
            }
            if (intent.getBooleanExtra("libraryDetails", false)) {
                val longPersona = CharacterPersona(name = "预览测试角色", gender = "女", age = "23",
                    personalityText = "LONG_START：" + "独立自然的合成人物性格描述，保留原文，不机械裁剪。".repeat(16) + "LONG_END",
                    originalLearningText = "这是合成的语言学习样本，不含私人记录。".repeat(14) + "LEARNING_END")
                if (CharacterLibraryStore.state.value.cards.none { it.persona.name == longPersona.name })
                    CharacterLibraryStore.add(longPersona, folder.id)
                if (CharacterLibraryStore.state.value.cards.none { it.sourceConversationId == "qa-private-1216" })
                    CharacterLibraryStore.add(CharacterPersona(name = "私人角色", personalityText = "从已有对话复制的合成人设"),
                        folder.id, sourceConversationId = "qa-private-1216")
            }
            if (intent.getBooleanExtra("libraryLayout", false)) {
                val city = CharacterLibraryStore.state.value.folders.firstOrNull { it.name == "城市日常" }
                    ?: CharacterLibraryStore.createFolder("城市日常")
                if (CharacterLibraryStore.state.value.cards.none { it.persona.name == "城市角色" })
                    CharacterLibraryStore.add(CharacterPersona(name = "城市角色", personalityText = "合成的城市故事人物"), city.id)
                if (CharacterLibraryStore.state.value.cards.none { it.persona.name == "未分类角色" })
                    CharacterLibraryStore.add(CharacterPersona(name = "未分类角色", personalityText = "合成的未分类人物，验证分区可读性"))
            }
            if (intent.getBooleanExtra("contributionLayout", false)) {
                val image = java.io.File(filesDir, "qa-persona-contribution.png").apply {
                    writeBytes(java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a9VQAAAAASUVORK5CYII="))
                }
                listOf("未分类春帆", "未分类海岚").forEach { name ->
                    if (CharacterLibraryStore.state.value.cards.none { it.persona.name == name })
                        CharacterLibraryStore.add(CharacterPersona(name = name, gender = "女", age = "24",
                            avatarPath = image.absolutePath, appearanceImagePaths = listOf(image.absolutePath),
                            appearanceImageDescs = listOf("合成的一像素参考图"),
                            personalityText = "仅用于本地界面检查，坦率自然。", relationshipText = "同在书店工作的朋友",
                            originalLearningText = "合成原文第一段。\n第二段保留换行。"))
                }
            }
            if (intent.getBooleanExtra("sheetStress", false)) {
                if (CharacterLibraryStore.state.value.folders.none { it.name == "空文件夹" })
                    CharacterLibraryStore.createFolder("空文件夹")
                listOf("澄川", "云野", "临夏", "初晴", "远岚", "长名称角色用于核对放大字号的排版").forEach { name ->
                    if (CharacterLibraryStore.state.value.cards.none { it.persona.name == name })
                        CharacterLibraryStore.add(CharacterPersona(name = name,
                            personalityText = "自然、坦率，有自己的生活。这段合成描述用于验证圆形勾选不会挤压两行预览。".repeat(4),
                            personaPrompt = "这是离线测试的虚构人设。".repeat(120)))
                }
            }
            val cards = CharacterLibraryStore.state.value.cards.filter { it.persona.name in names }
                .take(intent.getIntExtra("groupSize", 2).coerceIn(1, GroupChatPolicy.MAX_MEMBERS))
            val group = GroupChatProfile(members = cards.mapIndexed { index, card -> GroupMember("member-$index", card.persona, card.id) },
                userNickname = "小陆", announcement = "周末一起去书店", replyBufferEnabled = false)
            val privateChat = Conversation(id = "qa-private-1216", title = "私人对话", mode = ChatMode.COMPANION,
                characterProfile = CharacterProfile(name = "私人角色", personaPrompt = "独立的私聊人设", proactiveEnabled = true,
                    replyBufferEnabled = false, timePerception = false, languageModelId = "private-only-model"))
            val groupChat = Conversation(id = "qa-group-1216", title = "周末书店", mode = ChatMode.COMPANION,
                groupChat = group, messageCount = 5)
            val messages = listOf(
                Message(id = "user-old", role = Role.USER, content = "周末几点见", mode = ChatMode.COMPANION),
                Message(id = "a-old", role = Role.ASSISTANT, content = "我下午有空", mode = ChatMode.COMPANION,
                    groupMemberId = group.members[0].id, groupSenderName = group.members[0].persona.name),
                Message(id = "b-old", role = Role.ASSISTANT, content = "那就三点吧", mode = ChatMode.COMPANION,
                    groupMemberId = group.members[1].id, groupSenderName = group.members[1].persona.name),
                Message(id = "user-yes", role = Role.USER, content = "好呀", mode = ChatMode.COMPANION),
                Message(id = "a-ok", role = Role.ASSISTANT, content = "到时候见", mode = ChatMode.COMPANION,
                    groupMemberId = group.members[0].id, groupSenderName = group.members[0].persona.name))
            LocalStore.writeText(LocalStore.messagesFile(groupChat.id), AppJson.gson.toJson(messages))
            LocalStore.writeText(LocalStore.messagesFile(privateChat.id), "[]")
            val story = if (intent.getBooleanExtra("story", false)) listOf(
                Conversation(id = "qa-story-1219", title = "风港来信", mode = ChatMode.COMPANION,
                    storyProfile = StoryPolicy.fromCards(cards).copy(background = "风港，暴雨刚停", worldRules = "没有魔法，各人物只知道亲历的事",
                        openingSituation = "两人在旧书店收到灯塔来信", highQualityMemory = true, deepThinking = true))) else emptyList()
            val standards = if (intent.getBooleanExtra("standards", false)) listOf(
                Conversation(id = "qa-standard-a-1219", title = "标准甲"),
                Conversation(id = "qa-standard-b-1219", title = "标准乙")) else emptyList()
            standards.forEach { LocalStore.writeText(LocalStore.messagesFile(it.id), "[]") }
            LocalStore.writeText(LocalStore.conversationsFile(), AppJson.gson.toJson(listOf(groupChat, privateChat) + story + standards))
            getSharedPreferences("feature_rec", MODE_PRIVATE).edit().putInt("last_shown", BuildConfig.VERSION_CODE).apply()
            android.util.Log.i("FreeChat1222QA", "seed complete: ${CharacterLibraryStore.state.value.cards.size} cards, stress=${intent.getBooleanExtra("sheetStress", false)}")
            startActivity(Intent(this@GroupLibraryQaActivity, MainActivity::class.java))
            finish()
        }
    }
}
