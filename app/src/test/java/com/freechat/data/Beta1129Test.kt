package com.freechat.data

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Beta 1.1.29 契约：①内置对话「FreeChat简介说明」（种子幂等/删除闩/不改新规则）；
 * ⑥检查更新流程改造（检查与下载拆开、Update.md 插页、浮钮钳位）。
 */
class Beta1129Test {
    private fun source(path: String) = File("src/main/java/com/freechat/$path").readText()

    @Test fun introSeedIsPlainPinnedStandardWithTwelveRawMessages() {
        val conv = BuiltInIntro.conversation()
        assertEquals(BuiltInIntro.CONV_ID, conv.id)
        assertEquals("a04aac25-9397-49ca-a93c-49c2a1bce1c6", conv.id) // 多设备播种靠这个固定 id 收敛
        assertEquals("FreeChat简介说明", conv.title)
        assertEquals(12, conv.messageCount)
        assertTrue("默认置顶", conv.isPinned)
        assertEquals("标准档——不改新规则不内置提示词", com.freechat.model.ChatMode.STANDARD, conv.mode)
        val msgs = JsonParser.parseString(BuiltInIntro.MESSAGES_JSON).asJsonArray
        assertEquals("6 组问答 12 条消息原样内嵌", 12, msgs.size())
        assertEquals(6, msgs.count { it.asJsonObject["role"]?.asString == "USER" })
        assertEquals(6, msgs.count { it.asJsonObject["role"]?.asString == "ASSISTANT" })
        // 1.1.29.3 ④：思考过程从内置聊天记录里删去（字段留空保形），深度思考功能本身不动
        val withReasoning = msgs.count {
            it.asJsonObject["reasoningContent"]?.asString?.isNotEmpty() == true
        }
        assertEquals("内置对话内容不含思考过程", 0, withReasoning)
    }

    @Test fun introDeleteLatchesForeverAcrossDevices() {
        val repo = source("data/SettingsRepository.kt")
        val bridge = source("sync/SettingsBridge.kt")
        val vm = source("viewmodel/ChatViewModel.kt")
        assertTrue("删除闩是设置键（随设置同步）",
            repo.contains("booleanPreferencesKey(\"intro_deleted\")") &&
                repo.contains("val introDeleted: Flow<Boolean>") &&
                repo.contains("suspend fun saveIntroDeleted"))
        assertTrue("桥两侧都带闩：推是单向置真，拉到真就永久置真",
            bridge.contains("\"introDeleted\"") && bridge.contains("saveIntroDeleted(true)"))
        assertTrue("删除对话即上闩", vm.contains("saveIntroDeleted(true)"))
        assertTrue("播种三重幂等：删过不种、盘上有不种、固定 id 合并",
            vm.contains("seedBuiltInIntro") &&
                vm.contains("introDeleted.first()") &&
                vm.contains("BuiltInIntro.CONV_ID"))
        assertTrue("不抢自动命名、不打内置助理标记",
            vm.contains("convId == com.freechat.data.BuiltInIntro.CONV_ID) return"))
    }

    @Test fun updateFlowSplitsCheckFromDownloadAndFlowsGetButton() {
        val about = source("ui/screens/SettingsScreen.kt")
        val checker = source("data/AppUpdateChecker.kt")
        assertTrue("Update.md 随发版覆盖在官网 download 目录",
            checker.contains("NOTES_URL = \"https://freechater.com/download/Update.md\"") &&
                checker.contains("fun fetchUpdateNotes"))
        assertTrue("检查只查不下载：发现新版先落 available + 拉日志",
            about.contains("val checkUpdate") &&
                about.contains("AppUpdateChecker.fetchUpdateNotes()") &&
                about.contains("var available by remember"))
        assertTrue("获取更新才走下载→校验→安装",
            about.contains("val getUpdate") && about.contains("AppUpdateChecker.download("))
        assertTrue("更新内容文字流插在 logo↔选项卡之间（1.1.29.8 起 UpdateNotesStream 竖条版式）",
            about.contains("UpdateNotesStream(") && about.contains("val shownVersion = available?.versionName"))
        // 1.1.29.5 工单B/D 翻新旧契：浮钮钳位（压字/瞬移）撤掉，按钮随日志展开流式下移
        assertTrue("获取更新按钮流式排布在日志与选项卡之间，不再悬浮钳位",
            about.contains("gapMid") && !about.contains("optionsTopPx"))
    }
}
