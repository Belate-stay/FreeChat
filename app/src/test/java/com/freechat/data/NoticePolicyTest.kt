package com.freechat.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.1.20 启动提醒的口径契约：更新提醒「近期不再提醒」= 3 天冷却。 */
class NoticePolicyTest {

    @Test
    fun `没勾过就该弹`() {
        assertTrue(NoticePolicy.updateReminderDue(snoozedAt = 0L, now = 1_000_000L))
    }

    @Test
    fun `勾了之后三天内不弹`() {
        val now = 1_000_000_000_000L
        assertFalse(NoticePolicy.updateReminderDue(snoozedAt = now - NoticePolicy.UPDATE_SNOOZE_MS + 1, now = now))
        assertFalse(NoticePolicy.updateReminderDue(snoozedAt = now - 1000L, now = now))
    }

    @Test
    fun `满三天恢复提醒`() {
        val now = 1_000_000_000_000L
        assertTrue(NoticePolicy.updateReminderDue(snoozedAt = now - NoticePolicy.UPDATE_SNOOZE_MS, now = now))
        assertTrue(NoticePolicy.updateReminderDue(snoozedAt = now - NoticePolicy.UPDATE_SNOOZE_MS * 2, now = now))
    }

    @Test
    fun `一次最多弹一个且两个都中时轮流`() {
        // 只中一个：弹它
        assertEquals("copy", NoticePolicy.pickLaunchNotice(copyPending = true, updatePending = false, lastShown = ""))
        assertEquals("update", NoticePolicy.pickLaunchNotice(copyPending = false, updatePending = true, lastShown = ""))
        // 都不中：不弹
        assertNull(NoticePolicy.pickLaunchNotice(copyPending = false, updatePending = false, lastShown = ""))
        // 两个都中：轮流——上次弹过的这次让位，不固定优先级防饿死
        assertEquals("copy", NoticePolicy.pickLaunchNotice(copyPending = true, updatePending = true, lastShown = ""))
        assertEquals("update", NoticePolicy.pickLaunchNotice(copyPending = true, updatePending = true, lastShown = "copy"))
        assertEquals("copy", NoticePolicy.pickLaunchNotice(copyPending = true, updatePending = true, lastShown = "update"))
    }
}
