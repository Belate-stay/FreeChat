package com.freechat.data

import com.freechat.model.Message
import com.freechat.model.Role
import com.freechat.model.SearchCitation
import org.junit.Assert.*
import org.junit.Test

class SearchPresentationTest {
    @Test fun newRepliesKeepBodyLinksAndAllRetrievedSourcesSeparate() {
        val answer = "Useful link in the answer.\n\n[Android](https://developer.android.com/)"
        val sources = SearchPresentation.normalize((1..12).map { "Source $it" to "https://example.org/$it" })
        val display = SearchPresentation.forDisplay(answer, sources)
        assertEquals(answer, display.answer)
        assertEquals(12, display.sources.size)
        assertEquals(answer, SearchPresentation.forDisplay(answer, emptyList()).answer)
    }

    @Test fun invalidSourcesAreDiscardedAndDuplicateUrlsDoNotCreateExtraRows() {
        val sources = SearchPresentation.normalize(listOf("" to "https://example.org/report",
            "Repeated" to "https://example.org/report", "Bad" to "javascript:alert(1)", "Bad" to "file:///x"))
        assertEquals(1, sources.size)
        assertEquals("example.org", sources.single().title)
    }

    @Test fun oldAutomaticFooterIsSplitWithoutMutatingThePersistedAnswer() {
        val original = SearchPresentation.withSources("Answer", listOf("A" to "https://example.org/a", "B" to "https://example.org/b"))
        val message = AppJson.gson.fromJson("{\"role\":\"ASSISTANT\",\"content\":${AppJson.gson.toJson(original)}}", Message::class.java)
        assertNull(message.searchSources)
        val display = SearchPresentation.forDisplay(message.content, message.searchSources)
        assertEquals("Answer", display.answer)
        assertEquals(2, display.sources.size)
        assertEquals(original, message.content)
    }

    @Test fun newMessagesWithoutSourcesAreNotTreatedAsLegacyFooters() {
        val original = "Recommended reading:\n\n[Article](https://example.org/article)"
        val restored = AppJson.gson.fromJson(AppJson.gson.toJson(Message(role = Role.ASSISTANT, content = original)), Message::class.java)
        assertNotNull(restored.searchSources)
        assertEquals(original, SearchPresentation.forDisplay(restored.content, restored.searchSources).answer)
    }

    @Test fun metadataSurvivesJsonAndDoesNotAppearInBodyOrCopyableCode() {
        val message = Message(role = Role.ASSISTANT, content = "```text\nOriginal\n```",
            searchSources = listOf(SearchCitation("Report", "https://example.org/report")))
        val restored = AppJson.gson.fromJson(AppJson.gson.toJson(message), Message::class.java)
        assertEquals(message.searchSources, restored.searchSources)
        assertEquals(message.content, restored.content)
        assertFalse(restored.content.contains("https://"))
    }

    @Test fun legacyParserDoesNotTakeCodeOrOrdinaryProseAsSources() {
        listOf("```markdown\nExample:\n\n[A](https://example.org/a)",
            "Answer\n\nMore reading: [A](https://example.org/a)",
            "[A](https://example.org/a)").forEach { content ->
            val display = SearchPresentation.forDisplay(content, null)
            assertEquals(content, display.answer)
            assertTrue(display.sources.isEmpty())
        }
    }

    @Test fun legacyCodeFenceClosureAndWindowsNewlinesRemainIntact() {
        val original = SearchPresentation.withSources("```text\nOriginal\n", listOf("Report" to "https://example.org/report"))
        val display = SearchPresentation.forDisplay(original.replace("\n", "\r\n"), null)
        assertEquals("```text\r\nOriginal\r\n\r\n```", display.answer)
        assertEquals(1, display.sources.size)
    }
}
