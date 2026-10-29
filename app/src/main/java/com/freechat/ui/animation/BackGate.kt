package com.freechat.ui.animation

import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * 返回键逐级闸（2026-10-28 工单④）：一次返回只走一级。
 * 页面过渡（380ms，= DURATION_PAGE）没落地前下一级的返回处理已经就绪，连按会「跳级」——
 * 转场窗内的返回键记成「落地后再走一级」（最多补一级，更急的连打当噪声丢弃）。
 *
 * 2026-10-28 返修：被吞返回的弹层（导览/通知/弹层）吞键或关闭时必须 [cancelPending]——
 * 被吞的键不留幽灵补按（幽灵补按落地时弹层已关，会穿透到根页退桌面）。
 */
object BackGate {
    /** 转场窗 = 页面过渡时长。 */
    const val WINDOW_MS = 380L

    private val handler = Handler(Looper.getMainLooper())
    private var lastBackUptime = 0L
    private var pending: Runnable? = null

    /** 返回键过闸：立刻可走就同步执行 [action]；转场窗内则补记一次（落地后执行）。 */
    fun pass(action: () -> Unit) {
        val now = SystemClock.uptimeMillis()
        if (now - lastBackUptime < WINDOW_MS) {
            if (pending == null) {
                val runner = Runnable {
                    pending = null
                    lastBackUptime = SystemClock.uptimeMillis()
                    action()
                }
                pending = runner
                handler.postDelayed(runner, WINDOW_MS - (now - lastBackUptime))
            }
            return
        }
        lastBackUptime = now
        action()
    }

    /** 弹层吞键/关闭时清掉幽灵补按。 */
    fun cancelPending() {
        pending?.let { handler.removeCallbacks(it) }
        pending = null
    }
}
