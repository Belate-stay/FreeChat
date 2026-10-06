package com.freechat.data

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class NativeSearchEvidenceTest {
    @Test fun recognizesFlatNestedAndProviderSourceMetadata() {
        val json = JsonParser.parseString("""{
          "search_info":{"search_results":[{"title":"Official","url":"https://example.org/official"}]},
          "choices":[{"delta":{"annotations":[
            {"type":"url_citation","url_citation":{"title":"Report","url":"https://example.org/report"}},
            {"title":"Official","url":"https://example.org/official"}
          ]}}]
        }""").asJsonObject
        assertEquals(listOf("Official" to "https://example.org/official", "Report" to "https://example.org/report"),
            NativeSearchEvidence.citations(json))
    }

    @Test fun ignoresModelProseAndUnsafeUrls() {
        val json = JsonParser.parseString("""{"choices":[{"delta":{"content":"I searched the web","annotations":[
          {"title":"Unsafe","url":"javascript:alert(1)"}]}}]}""").asJsonObject
        assertTrue(NativeSearchEvidence.citations(json).isEmpty())
    }
}
