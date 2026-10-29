package com.freechat.data

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class TabletChat1212RegressionTest {
    private fun source(path: String) = File("src/main/java/com/freechat/$path").readText()

    @Test fun nonScrollableTabletChatsRecoverTheComposerAfterTheirRealLayoutIsReady() {
        val chat = source("ui/screens/ChatScreen.kt")
        val recovery = chat.substringAfter("var inputHidden by remember")
            .substringBefore("val inputOffset =")
        assertTrue("Observe the current conversation's real scrollability", recovery.contains("snapshotFlow"))
        assertTrue("Changing conversations must replace the observed list state",
            recovery.contains("LaunchedEffect(tablet, listState)"))
        assertTrue("An empty conversation has no list gesture to reveal input", recovery.contains("messages.isEmpty()"))
        assertTrue("Do not reveal a long conversation before its restored layout is measured",
            recovery.contains("viewportRestored && listState.layoutInfo.totalItemsCount > 0"))
        assertTrue(recovery.contains("!listState.canScrollBackward && !listState.canScrollForward"))
        assertTrue(recovery.contains("if (!tablet) return@LaunchedEffect"))
        assertTrue(recovery.contains("if (cannotScroll) inputHidden = false"))
        assertFalse("Recovery must not move reading position", recovery.contains("scrollToItem("))
        assertFalse("Recovery must not summon the keyboard", recovery.contains("requestFocus("))
        assertTrue("Long conversations retain the previous tablet behavior",
            chat.contains("if (!viewportRestored && !tablet) inputHidden = false"))
    }

    @Test fun tabletSidebarKeepsTheSameFixedArtisticBrandAsPhone() {
        val main = source("MainActivity.kt")
        val brand = main.substringAfter("Text(\"FreeChat\", Modifier.weight(1f)")
            .substringBefore("PaneExpandButton()")
        assertTrue("Left brand uses the existing phone Aurora family", brand.contains("fontFamily = Aurora"))
        assertTrue(brand.contains("fontSize = 27.sp"))
        assertTrue(brand.contains("letterSpacing = 2.sp"))
    }

    @Test fun tabletChatTitleUsesTheSelectionWithoutChangingPhoneOrBrandTypography() {
        val chat = source("ui/screens/ChatScreen.kt")
        assertTrue(chat.contains("val currentConversationTitle = conversations.firstOrNull { it.id == currentConvId }"))
        val header = chat.substringAfter("if (tablet) Text(").substringBefore(") else Spacer(Modifier.weight(1f))")
        assertTrue("Standard chat shows its actual title", header.contains("else -> currentConversationTitle"))
        assertTrue("Blank character names fall back to the selected title",
            header.contains("currentCharacter?.name?.ifBlank { currentConversationTitle } ?: currentConversationTitle"))
        assertTrue(header.contains("multiSelect.active -> s.selectedCount"))
        assertTrue(header.contains("style = MaterialTheme.typography.titleLarge"))
        assertFalse("Only left brand is artistic on tablet", header.contains("Aurora"))
    }

    @Test fun versionMatchesTheCorrectedBetaWorkOrder() {
        val gradle = File("build.gradle.kts").readText()
        assertTrue(gradle.contains("versionCode = 280"))
        assertTrue(gradle.contains("versionName = \"1.2.3\""))
    }
}
