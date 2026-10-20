package com.freechat.data

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class TabletNavigation1204ContractTest {
    private fun source(path: String) = File("src/main/java/com/freechat/$path").readText()

    @Test fun tabletPagesDistinguishCoverFromBackAndHoldTheOutgoingContent() {
        val file = File("src/main/java/com/freechat/ui/adaptive/TabletPageMotion.kt")
        assertTrue("Tablet pages need an intent-aware transition owner", file.exists())
        val motion = file.readText()
        assertTrue(motion.contains("back: Boolean"))
        assertTrue(motion.contains("ExitTransition.KeepUntilTransitionsFinished"))
        assertTrue(motion.contains("paneInteraction(active)"))
        assertTrue(motion.contains("TabletNavigationPolicy.pageTravel"))
    }

    @Test fun embeddedAccountIsARightPaneNotAnExclusiveWorkspace() {
        val main = source("MainActivity.kt")
        assertTrue(main.contains("val embeddedAccount"))
        assertTrue(main.contains("detailPage ="))
        assertTrue(main.contains("tabletDetailContent ="))
        assertTrue(main.contains("onTabletCategorySelected ="))
    }

    @Test fun rootBackAndClickUseDifferentNavigationIntents() {
        val main = source("MainActivity.kt")
        assertTrue(main.contains("fun navigateTo("))
        assertTrue(main.contains("fun navigateBack("))
        val conversationArrival = main.substringAfter("fun openConversation(").substringBefore("onSelectConversation = selectConversation@")
        assertTrue(conversationArrival.contains("chatCover.switchTo(conv.id)"))
        assertTrue(conversationArrival.contains("if (!keepWindow && currentScreen != Screen.CHAT) tabletChatArrival++"))
        assertTrue(main.contains("back = tabletNavigationBack"))
    }

    @Test fun tabletMotionDeceleratesAndFloatingShadowHasRealElevation() {
        val animation = source("ui/animation/FreeChatAnimation.kt")
        assertTrue(animation.contains("val tabletEaseOut = CubicBezierEasing(.22f, 1f, .36f, 1f)"))
        val layer = source("ui/adaptive/TabletDestinationLayer.kt")
        assertTrue(layer.contains("floatingPanelShadow"))
    }

    @Test fun workspaceCoverRetainsItsOriginalRightPageUntilCovered() {
        val main = source("MainActivity.kt")
        assertTrue(main.contains("var retainedHomeDetail"))
        assertTrue(main.contains("if (destination == null) requestedHomeDetail else retainedHomeDetail"))
        assertFalse("Workspace Back returns to Chat, never to a previous Account page",
            main.contains("tabletWorkspaceReturn = currentScreen"))
    }

    @Test fun aSelectedHomeWorkspaceCoversTheOutgoingWorkspaceFromAbove() {
        val main = source("MainActivity.kt")
        assertTrue(main.contains("var tabletWorkspaceArrival"))
        assertTrue(main.contains("if (destination == null && !tabletNavigationBack) 1f else 0f"))
        assertTrue(main.contains("LocalTabletSceneMotion provides homeSceneMotion"))
        val canvas = source("ui/adaptive/TabletChatCanvas.kt")
        assertTrue(canvas.contains("TabletMovingPane(TabletPane.LIST)"))
        assertTrue(canvas.contains("TabletMovingPane(TabletPane.DETAIL)"))
    }

    @Test fun actualCoverSurfacesStayOpaqueEvenWithTheFlowingBackdrop() {
        assertTrue(source("ui/adaptive/TabletPageMotion.kt").contains("liquidOpaqueBackground"))
        assertTrue(source("ui/adaptive/TabletChatCanvas.kt").contains("liquidOpaqueBackground"))
    }

    @Test fun changingTheTabletPresentationDoesNotRecreateSettings() {
        val main = source("MainActivity.kt")
        assertTrue(main.contains("val persistentSettings = remember { movableContentOf"))
        assertTrue(main.contains("persistentSettings()"))
    }

    @Test fun favoriteDetailsUseTheSameRightPaneCoverAndBackTransition() {
        val main = source("MainActivity.kt")
        assertTrue(main.contains("label = \"tablet_favorite_detail\""))
        assertTrue(main.contains("if (page == Screen.FAVORITE_DETAIL) favoriteDetailItem else null"))
        assertTrue(main.contains("FavoriteDetail(item)"))
    }

    @Test fun incomingHomeAndChatStartOffscreenOnTheFirstFrame() {
        val main = source("MainActivity.kt")
        val canvas = source("ui/adaptive/TabletChatCanvas.kt")
        assertTrue(main.contains("val homeArrival = remember(tabletWorkspaceArrival)"))
        assertTrue(main.contains("remember(homeArrival)"))
        assertTrue(canvas.contains("val routeArrival = remember(arrivalKey)"))
        assertFalse(canvas.contains("routeArrival.snapTo(1f)"))
        assertFalse(main.contains("homeArrival.snapTo(1f)"))
    }
}
