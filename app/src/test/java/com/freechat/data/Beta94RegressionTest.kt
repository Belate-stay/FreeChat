package com.freechat.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Beta 1.0.94 回归（六条工作单的文案面）：
 * ① 选档红字提醒文案=用户原话；⑤ 连接微信 Beta 标注+登录引导；③ 功能使用须知五条三语齐全；
 * ⑥ 输入区告知条两段（正文 + 可点引导）；验证码输入（M5 遗留补件）三语齐全。
 */
class Beta94RegressionTest {

    @Test
    fun modeLockWarnIsAuthorsExactCopy() {
        // 红字提醒是用户点名的原话（含中间那个停顿空位）——改文案前先问作者
        assertEquals("创建并对话后 无法修改对话模式，请确定选择。", com.freechat.i18n.buildStrings("zh-CN").dialogueModePickWarn)
        assertEquals("建立並對話後 無法修改對話模式，請確定選擇。", com.freechat.i18n.buildStrings("zh-TW").dialogueModePickWarn)
    }

    @Test
    fun wechatNoticesExistInAllLocales() {
        listOf("zh-CN", "zh-TW", "en").forEach { locale ->
            val s = com.freechat.i18n.buildStrings(locale)
            assertTrue("$locale noticeTitle", s.wechatNoticeTitle.isNotBlank())
            assertTrue("$locale betaHint", s.wechatBetaHint.isNotBlank())
            assertTrue("$locale notice1 云同步/登录", s.wechatNotice1.isNotBlank())
            assertTrue("$locale notice2 内置模型", s.wechatNotice2.isNotBlank())
            assertTrue("$locale notice3 头像备注", s.wechatNotice3.isNotBlank())
            assertTrue("$locale notice4 Bridge/模拟设置", s.wechatNotice4.isNotBlank())
            assertTrue("$locale notice5 延迟不稳定", s.wechatNotice5.isNotBlank())
            assertTrue("$locale loginUse", s.wechatLoginUse.isNotBlank())
        }
    }

    @Test
    fun wechatInputNoticeAndVerifyStringsExist() {
        listOf("zh-CN", "zh-TW", "en").forEach { locale ->
            val s = com.freechat.i18n.buildStrings(locale)
            assertTrue("$locale inputNotice", s.wechatInputNotice.isNotBlank())
            assertTrue("$locale inputReveal", s.wechatInputReveal.isNotBlank())
            assertTrue("$locale verifyPlaceholder", s.wechatVerifyPlaceholder.isNotBlank())
            assertTrue("$locale verifySubmit", s.wechatVerifySubmit.isNotBlank())
        }
        // 内置模型名是产品事实，三语一致（错了用户会找不到）
        listOf("zh-CN", "zh-TW", "en").forEach { locale ->
            val s = com.freechat.i18n.buildStrings(locale)
            assertTrue("$locale notice2 应点名 mimo-v2.6-flash", s.wechatNotice2.contains("mimo-v2.6-flash"))
        }
    }
}
