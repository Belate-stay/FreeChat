package com.freechat.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 「检查更新」的纯逻辑契约：清单校验、新旧判定、安装包一致性。 */
class AppUpdateCheckerTest {

    private val valid = """{"packageName":"com.freechat","versionName":"1.1.10","versionCode":206,
        "apkUrl":"https://freechater.com/download/FreeChat_v1.1.10.apk"}"""

    @Test
    fun `parse accepts a well formed manifest`() {
        val latest = AppUpdateChecker.parseLatest(valid, "com.freechat")
        assertEquals("com.freechat", latest?.packageName)
        assertEquals("1.1.10", latest?.versionName)
        assertEquals(206L, latest?.versionCode)
        assertEquals("https://freechater.com/download/FreeChat_v1.1.10.apk", latest?.apkUrl)
    }

    @Test
    fun `parse rejects mismatched package name`() {
        assertNull(AppUpdateChecker.parseLatest(valid, "com.evil"))
    }

    @Test
    fun `parse rejects malformed or hostile manifests`() {
        assertNull(AppUpdateChecker.parseLatest("not json", "com.freechat"))
        assertNull(AppUpdateChecker.parseLatest("[]", "com.freechat"))
        assertNull(AppUpdateChecker.parseLatest("""{"packageName":"com.freechat"}""", "com.freechat"))
        // 非 https 的下载地址一律拒收
        assertNull(
            AppUpdateChecker.parseLatest(
                valid.replace("https://freechater.com", "http://freechater.com"),
                "com.freechat"
            )
        )
        assertNull(
            AppUpdateChecker.parseLatest(
                valid.replace("https://freechater.com", "javascript:alert(1)"),
                "com.freechat"
            )
        )
        // 非法编译版本号拒收
        assertNull(
            AppUpdateChecker.parseLatest(
                valid.replace("\"versionCode\":206", "\"versionCode\":0"),
                "com.freechat"
            )
        )
    }

    @Test
    fun `update only when remote build number is greater`() {
        val latest = AppUpdateChecker.parseLatest(valid, "com.freechat")!!
        assertFalse(AppUpdateChecker.needsUpdate(206, latest)) // 同版本：已是最新
        assertFalse(AppUpdateChecker.needsUpdate(207, latest)) // 本机更新：不算
        assertTrue(AppUpdateChecker.needsUpdate(205, latest))  // 远端更大：有更新
    }

    @Test
    fun `downloaded archive must match manifest package and build number`() {
        val latest = AppUpdateChecker.parseLatest(valid, "com.freechat")!!
        assertTrue(AppUpdateChecker.archiveMatches(latest, "com.freechat", 206))
        assertFalse(AppUpdateChecker.archiveMatches(latest, "com.other", 206))
        assertFalse(AppUpdateChecker.archiveMatches(latest, "com.freechat", 205))
        assertFalse(AppUpdateChecker.archiveMatches(latest, null, 206))
    }
}
