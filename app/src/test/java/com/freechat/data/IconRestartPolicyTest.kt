package com.freechat.data

import org.junit.Assert.*
import org.junit.Test

class IconRestartPolicyTest {
    private fun check(pending: Boolean, loading: List<Boolean>, typing: List<Boolean>): Boolean {
        val clazz = runCatching { Class.forName("com.freechat.data.IconRestartPolicy") }.getOrNull()
        assertNotNull("Restart warning must inspect all conversations, including background replies", clazz)
        return clazz!!.getMethod("requiresConfirmation", Boolean::class.javaPrimitiveType,
            Collection::class.java, Collection::class.java).invoke(null, pending, loading, typing) as Boolean
    }
    @Test fun backgroundGenerationWarnsEvenWhenTheCurrentConversationIsIdle() {
        assertTrue(check(false, listOf(false, true), listOf(false)))
    }
    @Test fun backgroundDeliveryAndBufferedRepliesAreProtected() {
        assertTrue(check(false, listOf(false), listOf(false, true)))
        assertTrue(check(true, emptyList(), emptyList()))
    }
    @Test fun finishedConversationsDoNotTriggerAWarning() {
        assertFalse(check(false, emptyList(), emptyList()))
        assertFalse(check(false, listOf(false, false), listOf(false)))
    }
}
