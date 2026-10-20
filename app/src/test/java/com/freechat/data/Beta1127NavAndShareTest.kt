package com.freechat.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Beta 1.1.26.7 契约：①我的分享页管理（点卡片菜单/调节/长按多选批量撤销）；
 * ②设置返回逻辑（不跳级/原路返回/滚动原位）；③返回方向按层级深度。
 */
class Beta1127NavAndShareTest {
    private fun source(path: String) = File("src/main/java/com/freechat/$path").readText()

    @Test fun shareRowHasNoTrailingRevokeAndOpensMenuOnTap() {
        val shares = source("ui/screens/MySharesScreen.kt")
        assertFalse("行尾「撤销」按钮已下线，撤销只进卡片菜单", shares.contains("s.shareRevoke,"))
        assertTrue("点卡片弹动作菜单", shares.contains("state.menuShare = share"))
        assertTrue("长按进多选", shares.contains("state.enterSelect(share)"))
        val settings = source("ui/screens/SettingsScreen.kt")
        assertTrue("菜单三选项：查看内容/复制链接/撤销分享红字",
            settings.contains("s.shareViewContent") && settings.contains("s.shareLinkCopy"))
        assertTrue("撤销分享红字", settings.contains("labelColor = colors.ErrorRed"))
    }

    @Test fun shareTuneMenuSharesFavoritesImplementationAndAnimations() {
        val tune = source("ui/components/TuneMenu.kt")
        assertTrue("共享 TuneMenu 是唯一实现", tune.contains("fun TuneMenu("))
        assertTrue("主↔子菜单沿用 pageTransition", tune.contains("FreeChatAnimation.pageTransition(forward, travel, animateSize = true)"))
        val favorites = source("ui/screens/FavoritesScreen.kt")
        val settings = source("ui/screens/SettingsScreen.kt")
        assertTrue("收藏页走共享 TuneMenu", favorites.contains("com.freechat.ui.components.TuneMenu("))
        assertTrue("我的分享走共享 TuneMenu", settings.contains("com.freechat.ui.components.TuneMenu("))
        assertFalse("收藏页私有 TuneMenu 已删", favorites.contains("private fun TuneMenu("))
    }

    @Test fun shareMultiSelectHeaderUnifiesWithFavorites() {
        val settings = source("ui/screens/SettingsScreen.kt")
        assertTrue("标题「已选择x项」与收藏页统一", settings.contains("s.selectedCount(myShares.selectedIds.size)"))
        assertTrue("右上角「撤销分享」", settings.contains("s.shareRevokeFull"))
        assertTrue("多选换头走 contentReplacement", settings.contains("settingsShareSelectHeader"))
        assertTrue("返回键先退出多选", settings.contains("myShares.exitSelect()"))
        val shares = source("ui/screens/MySharesScreen.kt")
        assertTrue("勾选圈与收藏页同视觉", shares.contains("shareCheckFill") && shares.contains("shareCheckStroke"))
    }

    @Test fun settingsReturnNeverSkipsLevels() {
        val settings = source("ui/screens/SettingsScreen.kt")
        val main = source("MainActivity.kt")
        assertTrue("模型编辑返回走深链闩回来源模型列表", main.contains("settingsOpenModelList = modelEditorType"))
        assertTrue("模型列表闩被设置页消费", settings.contains("openModelListOnLaunch"))
        assertTrue("三级页返回上一级（作者/开发者→关于）",
            settings.contains("SubScreen.AUTHOR, SubScreen.DEV_PASSWORD, SubScreen.DEV_OPTIONS -> returnToAbout()") &&
                settings.contains("subScreen = if (showTabletCategories) null else SubScreen.ABOUT_FREECHAT"))
    }

    @Test fun scrollPositionsSurviveFullScreenDetours() {
        val main = source("MainActivity.kt")
        val settings = source("ui/screens/SettingsScreen.kt")
        val motion = source("ui/animation/PageMotion.kt")
        assertTrue("滚动记忆由 MainActivity 持有", main.contains("val settingsScrolls = remember"))
        assertTrue("侧滑进设置清空 = 统一回顶部", main.contains("settingsScrolls.clear()"))
        assertTrue("持久化助手实时回写", motion.contains("snapshotFlow { state.value }.collect { saved[key] = it }"))
        assertTrue("手机主列表沿用 main，平板分类独立保留位置",
            settings.contains("if (showTabletCategories) \"settings_main_\" + selectedCategory.name else \"main\", levelScrolls"))
        assertTrue("二级页按页名接持久记忆", settings.contains("rememberPersistentScrollState(\"sub_\" + screen.name, levelScrolls)"))
        assertFalse("旧 levelScrolls.getOrPut 已退场", settings.contains("levelScrolls.getOrPut"))
    }

    @Test fun pageTransitionDirectionFollowsNavigationDepth() {
        val settings = source("ui/screens/SettingsScreen.kt")
        assertTrue("SubScreen 有深度定义", settings.contains("private fun subScreenDepth"))
        assertTrue("三级=2 二级=1 主页=0", settings.contains("SubScreen.AUTHOR, SubScreen.DEV_PASSWORD, SubScreen.DEV_OPTIONS, SubScreen.MY_FEEDBACK -> 2"))
        assertTrue("页面加上分类快照后仍按子页深度增减",
            settings.contains("forward = { initial, target -> subScreenDepth(target.first) >= subScreenDepth(initial.first) }"))
        assertFalse("旧的 target != null 方向判定已删", settings.contains("forward = { _, target -> target != null }"))
    }

    @Test fun shareWebUrlIsDeterministicPublicLink() {
        val types = source("sync/SyncTypes.kt")
        assertTrue("分享页地址与服务端 share.js 同口径",
            types.contains("\"https://freechater.com/s/\$id\""))
    }

    @Test fun favoritesListStateSurvivesDetailRoundTrip() {
        val main = source("MainActivity.kt")
        val favorites = source("ui/screens/FavoritesScreen.kt")
        assertTrue("收藏列表态提升到 MainActivity", main.contains("favoritesListState"))
        assertTrue("侧滑进收藏重置回顶", main.contains("LazyListState(0, 0)"))
        assertTrue("FavoritesScreen 接外部 listState", favorites.contains("listState:"))
    }
}
