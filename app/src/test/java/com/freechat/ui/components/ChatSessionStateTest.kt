package com.freechat.ui.components

import org.junit.Assert.*
import org.junit.Test

class ChatSessionStateTest {
    @Test fun firstVisitHasNoSavedViewport() {
        assertNull(ChatSessionState().viewport("first"))
    }

    @Test fun switchingConversationsKeepsIndependentPositions() {
        val session = ChatSessionState()
        session.saveViewport("a", ChatViewportPosition(12, 187, "message-a"))
        session.saveViewport("b", ChatViewportPosition(3, 42, "message-b"))
        assertEquals(ChatViewportPosition(12, 187, "message-a"), session.viewport("a"))
        assertEquals(ChatViewportPosition(3, 42, "message-b"), session.viewport("b"))
    }

    @Test fun restoreUsesTheMessageKeyWhenEarlierRowsHaveChanged() {
        val saved = ChatViewportPosition(2, 84, "reader-anchor")
        assertEquals(1, saved.resolveIndex(listOf("new-row", "reader-anchor", "later")))
        assertEquals(84, saved.offset)
    }

    @Test fun aDeletedAnchorFallsBackToAnExistingRow() {
        assertEquals(1, ChatViewportPosition(8, 15, "deleted").resolveIndex(listOf("a", "b")))
        assertEquals(0, ChatViewportPosition(0, 15).resolveIndex(emptyList()))
    }

    @Test fun reasoningStartsExpandedButTheReadersChoiceSurvivesNavigation() {
        val session = ChatSessionState()
        assertTrue(session.reasoningExpanded("chat-a", "reply"))
        session.setReasoningExpanded("chat-a", "reply", false)
        session.saveViewport("chat-b", ChatViewportPosition(2, 15))
        assertFalse(session.reasoningExpanded("chat-a", "reply"))
        assertTrue(session.reasoningExpanded("chat-a", "other-reply"))
        assertTrue(session.reasoningExpanded("chat-b", "reply"))
    }

    @Test fun aNewApplicationSessionResetsBothKindsOfPresentationMemory() {
        val old = ChatSessionState()
        old.saveViewport("chat", ChatViewportPosition(5, 40))
        old.setReasoningExpanded("chat", "reply", false)
        val restarted = ChatSessionState()
        assertNull(restarted.viewport("chat"))
        assertTrue(restarted.reasoningExpanded("chat", "reply"))
    }
}
