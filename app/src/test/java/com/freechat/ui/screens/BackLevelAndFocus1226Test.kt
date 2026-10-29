package com.freechat.ui.screens

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Beta 1.2.2.6 工单④契约：①系统返回键逐级闸——转场窗内连按不连跳（补一级落地再走）；
 * ②返回焦点记忆——二级页往返列表回到点进去的位置（滚动/文件夹层级都算）；
 * ③退出大页面重进回顶部——前向进入（navigateTo）清记忆，返回（navigateBack）不清。
 */
class BackLevelAndFocus1226Test {
    private fun main() = File("src/main/java/com/freechat/MainActivity.kt").readText()
    private fun source(path: String) = File("src/main/java/com/freechat/$path").readText()

    @Test fun systemBackIsGatedToOneLevelPerTransitionWindow() {
        val activity = main()
        assertTrue("返回键过闸：override onBackPressed 在场", activity.contains("override fun onBackPressed()"))
        assertTrue("转场窗内连按补记一级（BackGate）", activity.contains("BackGate.pass"))
        assertTrue("落地后补走一级（虚拟按键重派发）", activity.contains("onBackPressedDispatcher.onBackPressed()"))
        val gate = File("src/main/java/com/freechat/ui/animation/BackGate.kt").readText()
        assertTrue("窗口时长 = 页面过渡时长", gate.contains("WINDOW_MS = 380L"))
        assertTrue("弹层吞键/关闭清幽灵补按（2026-10-28 返修）", gate.contains("fun cancelPending()"))
    }

    @Test fun frostedSheetCardsAreTheFirstBackPoint() {
        val sheet = File("src/main/java/com/freechat/ui/components/SheetPanel.kt").readText()
        assertTrue("弹层亮起即重挂返回钩子（文件夹切换重组不得抢回优先级，2026-10-28 返修）",
            sheet.contains("key(visible)"))
        assertTrue("弹层任何关闭路径都清幽灵补按", sheet.contains("dismissSheet"))
    }

    @Test fun forwardEntryResetsBigPagePositionButBackReturnKeepsIt() {
        val activity = main()
        assertTrue("进选人页清文件夹与滚动", activity.contains("Screen.CHARACTER_PICKER -> { pickerFolderId = null; newPageScrolls.remove(\"picker\") }"))
        assertTrue("进群设置清滚动", activity.contains("Screen.GROUP_SETTINGS -> newPageScrolls.remove(\"groupSettings\")"))
        assertTrue("进故事设置清滚动", activity.contains("Screen.STORY_SETUP -> newPageScrolls.remove(\"storySetup\")"))
        assertTrue("进角色库清滚动", activity.contains("Screen.CHARACTER_LIBRARY -> newPageScrolls.remove(\"library\")"))
        val navigateBack = activity.substringAfter("fun navigateBack(screen: Screen) {").substringBefore("var tabletNavigationOrigin")
        assertFalse("返回（navigateBack）不清滚动记忆=回到点进的位置", navigateBack.contains("newPageScrolls"))
    }

    @Test fun pickerFolderLevelSurvivesRoundTripsLikeTheLibraryPrecedent() {
        val activity = main()
        assertTrue("选人页文件夹层级提升到 MainActivity", activity.contains("var pickerFolderId by remember { mutableStateOf<String?>(null) }"))
        val picker = source("ui/screens/CharacterPickerScreen.kt")
        assertTrue("文件夹态吃提升值", picker.contains("val folderId = selectedFolderId"))
        assertFalse("不得再挂本页 remember 文件夹态", picker.contains("var folderId by remember"))
        assertTrue("返回一级=退出文件夹", picker.contains("folder != null -> onSelectFolder(null)"))
        assertTrue("进文件夹写提升态", picker.contains("onOpen = { onSelectFolder(entry.id) }"))
    }

    @Test fun bigPagesKeepTheirListPositionAcrossChildRoundTrips() {
        val motion = source("ui/animation/PageMotion.kt")
        assertTrue("Lazy 列表持久滚动助手在场", motion.contains("fun rememberPersistentLazyListState(key: String, saved: MutableMap<String, Int>): LazyListState"))
        val library = source("ui/screens/CharacterLibraryScreen.kt")
        assertTrue("角色库列表进记忆", library.contains("rememberPersistentLazyListState(\"library\", scrollMemory)"))
        assertTrue("壳列表吃外部滚动态", library.contains("state = listState"))
        val picker = source("ui/screens/CharacterPickerScreen.kt")
        assertTrue("选人列表进记忆", picker.contains("rememberPersistentLazyListState(\"picker\", scrollMemory)"))
        val story = source("ui/screens/StorySettingsScreen.kt")
        assertTrue("故事设置列表进记忆", story.contains("rememberPersistentLazyListState(\"storySetup\", scrollMemory)"))
        val group = source("ui/screens/GroupSettingsScreen.kt")
        assertTrue("群设置表单滚动进记忆", group.contains("rememberPersistentScrollState(\"groupSettings\", scrollMemory)"))
    }
}
