package com.freechat.data

import com.freechat.ui.adaptive.TabletCameraFrame
import com.freechat.ui.adaptive.TabletLayoutPolicy
import com.freechat.ui.adaptive.TabletNavigationPolicy
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class TabletChat1205ContractTest {
    private fun source(path: String) = File("src/main/java/com/freechat/$path").readText()

    @Test fun auxiliaryAndFullscreenKeepTheOriginalChatMeasurementWidth() {
        val method = TabletNavigationPolicy::class.java.methods.firstOrNull { it.name == "stableChatFrame" }
        assertNotNull("A stable-width camera must own the layout", method)
        for (width in listOf(600f, 800f, 1280f, 1600f)) {
            val normal = TabletLayoutPolicy.panes(width)
            for (supporting in listOf(false, true)) for (fullscreen in listOf(false, true)) {
                val frame = method!!.invoke(TabletNavigationPolicy, width, supporting, fullscreen) as TabletCameraFrame
                assertEquals(normal.detailWidth, frame.chatWidth, .001f)
                assertEquals(frame.chatX + frame.chatWidth, frame.dividerX, .001f)
                if (supporting && !fullscreen) {
                    assertEquals(0f, frame.chatX, .001f)
                    assertEquals(normal.listWidth, width - frame.auxiliaryX, .001f)
                }
                if (fullscreen) assertEquals((width - normal.detailWidth) / 2f, frame.chatX, .001f)
            }
        }
    }

    @Test fun canvasMeasuresStableColumnsAndDoesNotSlideTheComposerWithConversationArrival() {
        val canvas = source("ui/adaptive/TabletChatCanvas.kt")
        assertTrue(canvas.contains("TabletNavigationPolicy.stableChatFrame"))
        assertFalse(canvas.contains("Constraints.fixed(chatWidthPx"))
        assertFalse(canvas.contains("translationX = arrival.value * size.width"))
        val chat = source("ui/screens/ChatScreen.kt")
        assertTrue(chat.contains("TabletChatContent"))
        assertTrue(chat.contains("if (!viewportRestored && !tablet) inputHidden = false"))
    }

    @Test fun outgoingPixelsAreCapturedBeforeSwitchingAndDraftTextHasItsOwnMotion() {
        val main = source("MainActivity.kt")
        assertTrue(main.contains("chatCover.switchTo(conv.id)"))
        val input = source("ui/components/ChatInput.kt")
        assertTrue(input.contains("draftSwapProgress"))
        assertTrue(input.contains("then(draftContentMotion)"))
        assertFalse(main.contains("var tabletWorkspaceReturn"))
    }

    @Test fun onlyActualChatCanRequestAFullscreenAffordance() {
        val workspace = source("ui/adaptive/TabletWorkspace.kt")
        assertTrue(workspace.contains("fun PaneExpandButton(chat: Boolean = false)"))
        assertTrue(workspace.contains("if (!chat) return"))
        assertTrue(source("ui/screens/ChatScreen.kt").contains("PaneExpandButton(chat = true)"))
    }

    @Test fun restoringALongDraftDoesNotAutoExpandOrTakeKeyboardFocusOnTablet() {
        val input = source("ui/components/ChatInput.kt")
        assertTrue(input.contains("draftEditedByUser by remember(voiceSessionKey)"))
        assertTrue(input.contains("!tabletComposer || draftEditedByUser"))
        assertTrue(input.contains("LaunchedEffect(wrappedLineCount, draftEditedByUser)"))
        assertTrue("Only explicit edits and paste may enable automatic expansion",
            Regex("draftEditedByUser = true").findAll(input).count() >= 4)
    }
}
