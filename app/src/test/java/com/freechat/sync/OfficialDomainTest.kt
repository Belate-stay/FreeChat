package com.freechat.sync

import com.freechat.i18n.buildStrings
import java.io.File
import java.net.URI
import org.junit.Assert.*
import org.junit.Test

class OfficialDomainTest {
    @Test fun existingSessionsUseTheApprovedHttpsAccountServer() {
        val endpoint = URI(ApiClient.BASE)
        assertEquals("https", endpoint.scheme)
        assertEquals("freechater.com", endpoint.host)
        assertEquals("/api", endpoint.path)
    }

    @Test fun officialWebsiteLinksMatchTheAccountDomain() {
        for (screen in listOf("AgreementScreen.kt", "SettingsScreen.kt")) {
            val source = File("src/main/java/com/freechat/ui/screens/$screen").readText()
            assertTrue(screen, source.contains("https://freechater.com/"))
            assertFalse(screen, source.contains("118.178.227.178"))
        }
    }

    @Test fun authorNotesReflectDomainApprovalInAllLocales() {
        assertTrue(buildStrings("zh-CN").authorWebNote.contains("域名已过审，直接用域名"))
        assertTrue(buildStrings("zh-TW").authorWebNote.contains("網域已過審，直接用網域"))
        assertTrue(buildStrings("en").authorWebNote.contains("The domain has cleared review"))
    }
}
