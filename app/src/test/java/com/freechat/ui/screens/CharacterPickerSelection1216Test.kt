package com.freechat.ui.screens

import org.junit.Assert.*
import org.junit.Test

class CharacterPickerSelection1216Test {
    @Suppress("UNCHECKED_CAST")
    private fun toggle(selected: Set<String>, requested: List<String>, limit: Int = 4): Set<String>? {
        val owner = runCatching { Class.forName("com.freechat.ui.screens.CharacterPickerScreenKt") }.getOrNull()
        assertNotNull("Picker must provide its real atomic selection policy", owner)
        val method = owner!!.declaredMethods.firstOrNull { it.name == "toggleCharacterSelection" }
        assertNotNull("Picker must provide its real atomic selection policy", method)
        method!!.isAccessible = true
        return method.invoke(null, selected, requested, limit) as Set<String>?
    }

    @Test fun wholeFolderSelectionDeduplicatesCardIds() {
        assertEquals(setOf("one", "two", "three"), toggle(setOf("one"), listOf("one", "two", "two", "three")))
    }

    @Test fun wholeFolderSelectionOverLimitRejectsTheWholeChange() {
        val original = setOf("one", "two", "three")
        assertNull(toggle(original, listOf("four", "five")))
        assertEquals(setOf("one", "two", "three"), original)
    }

    @Test fun selectingAnAlreadySelectedFolderRemovesAllItsCards() {
        assertEquals(setOf("other"), toggle(setOf("one", "two", "other"), listOf("one", "two")))
    }

    @Test fun fourCardsAreAllowedAndSingleCardTogglesOff() {
        assertEquals(setOf("one", "two", "three", "four"), toggle(setOf("one", "two", "three"), listOf("four")))
        assertEquals(emptySet<String>(), toggle(setOf("one"), listOf("one")))
    }

    @Test fun emptyFolderSelectionDoesNotChangeTheDraft() {
        assertEquals(setOf("one"), toggle(setOf("one"), emptyList()))
    }
}
