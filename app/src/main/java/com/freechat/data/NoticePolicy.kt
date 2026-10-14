package com.freechat.data

/**
 * 1.1.20：启动弹窗（冲突副本提醒 / 检查更新提醒）的提醒策略 —— 纯函数，测试钉住。
 *
 * 两种提醒的口径（2026-10-10 用户拍板）：
 *  · 冲突副本 —— 勾「永不提醒」后**永远**不再弹（不论还有没有副本）；
 *  · 检查更新 —— 勾「近期不再提醒」后 **3 天**内不弹；3 天后还不是最新版就再提醒。
 */
object NoticePolicy {

    /** 「近期不再提醒」的冷却期：3 天 */
    const val UPDATE_SNOOZE_MS = 3L * 24 * 60 * 60 * 1000

    /** 上次勾「近期不再提醒」的时刻之后，更新提醒是否该再弹（0 = 从没勾过，该弹就弹） */
    fun updateReminderDue(snoozedAt: Long, now: Long): Boolean =
        snoozedAt <= 0L || now - snoozedAt >= UPDATE_SNOOZE_MS

    /**
     * 一次启动最多弹一个（1.1.26.4）：两个都中时**轮流**——上次弹过的让位，
     * 另一个下次启动再弹（不固定优先级，防一个永远饿死另一个）。
     * lastShown = "copy"/"update"/""（没弹过）；弹过与否由调用方粘滞闩锁，本函数只挑不锁。
     */
    fun pickLaunchNotice(copyPending: Boolean, updatePending: Boolean, lastShown: String): String? = when {
        copyPending && updatePending -> if (lastShown == "copy") "update" else "copy"
        copyPending -> "copy"
        updatePending -> "update"
        else -> null
    }
}
