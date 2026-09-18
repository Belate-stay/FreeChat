package com.freechat.proactive

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 主动智能的定时层：把 ProactiveRequest 挂成系统级精确闹钟。
 *
 * 为什么是 AlarmManager 而不是长连接 / 服务器推送：
 * 闹钟活在系统里，不在 App 进程里。App 被用户从最近任务划掉（这是国内后台被杀的最常见形态）
 * 之后，进程没了、服务没了、网络没了，闹钟照样到点把进程拉起来。
 * 这一点是任何「客户端自己 sleep 等时间到」的方案都做不到的——那种方案进程一死就全没了。
 *
 * 已知边界（诚实记录，不是 bug）：
 * - 用户「强行停止」（应用信息页那个按钮）之后，系统会冻结该应用的全部闹钟，直到用户再次手动打开 App。任何方案都无解。
 * - 关机期间不触发；开机后由 ProactiveBootReceiver 重新挂回来（按原定时刻，过期太久就丢弃）。
 * - Android 12+ 精确闹钟需要用户授权；没授权时退化为「不精确闹钟」，
 *   到点误差可能几分钟——对这个功能（「三点多了，人呢」）完全可以接受，但要在 UI 上告诉用户。
 */
object ProactiveScheduler {

    private const val TAG = "FreeChat"
    const val ACTION_FIRE = "com.freechat.proactive.FIRE"
    const val EXTRA_CONV_ID = "conv_id"

    /** 过期太久就别补发了（比如关机两天后才开机） */
    private const val STALE_MS = 3 * 60 * 60 * 1000L

    /**
     * 计划的最远视界（24 小时）。
     * 提示词里教了「明天 9:00」这种写法，所以不能拒收 —— 拒收就是模型以为定好了、实际什么都没有。
     * 超出视界的一律**压到视界上**（顺延一次总比静默丢失强），再由模型临到跟前重新决定。
     */
    private const val MAX_HORIZON_MS = 24 * 60 * 60 * 1000L

    private fun pendingIntent(context: Context, convId: String): PendingIntent {
        val intent = Intent(context, ProactiveReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra(EXTRA_CONV_ID, convId)
            // data 唯一化：保证不同对话的 PendingIntent 不会互相覆盖（比只靠 requestCode 稳）
            data = Uri.parse("freechat://proactive/$convId")
        }
        return PendingIntent.getBroadcast(
            context, convId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** 挂一条计划（会先按静默时段与最小间隔校正时刻） */
    fun schedule(context: Context, req: ProactiveRequest) {
        val store = ProactiveStore(context)
        val now = System.currentTimeMillis()
        var at = store.adjustQuietHours(store.applyGapFloor(req.convId, req.dueAt))
        // 太远：压到视界上。压完可能正好落进静默时段（视界就是「此刻的钟点 +24h」），所以再校正一次
        if (at - now > MAX_HORIZON_MS) at = store.adjustQuietHours(now + MAX_HORIZON_MS)
        // 兜底：绝不挂一个已经过去的时刻（闹钟会立刻触发，等于刚定完就发）
        at = at.coerceAtLeast(now + 60_000L)
        val fixed = req.copy(dueAt = at)
        store.put(fixed)
        setAlarm(context, fixed)
    }

    fun setAlarm(context: Context, req: ProactiveRequest) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context, req.convId)
        val trigger = req.dueAt
        try {
            if (canScheduleExact(context)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            } else {
                // 没拿到精确闹钟授权：退化成不精确（Doze 下同样能唤醒进程，只是时刻有几分钟浮动）
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            }
            Log.d(TAG, "Proactive alarm set for ${fmt(trigger)} (${req.characterName})")
        } catch (e: SecurityException) {
            // 授权在调用瞬间被撤销 / 厂商 ROM 拒绝：再退一档，别让整条链路崩掉
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            } catch (e2: Exception) {
                Log.e(TAG, "Proactive alarm failed entirely", e2)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Proactive alarm failed", e)
        }
    }

    /** 取消某个对话的待触发计划（用户主动来了 / 模型决定取消 / 角色关掉了开关） */
    fun cancel(context: Context, convId: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context, convId))
        ProactiveStore(context).remove(convId)
    }

    /** 开机 / 应用升级后把账本里的计划重新挂回系统（系统闹钟在那两种情况下会被清空） */
    fun restoreAll(context: Context) {
        val store = ProactiveStore(context)
        val now = System.currentTimeMillis()
        for (req in store.all()) {
            // 比的是「现在已经比它晚了多久」。写反成 dueAt - now 的话，
            // 未来三小时内的计划全都会被当成「没过期」的反面删掉 —— 重启一次，今晚的约定就没了。
            if (now - req.dueAt > STALE_MS) store.remove(req.convId)
            else setAlarm(context, req)      // 还没到点（或刚过去几分钟）的重新挂上
        }
    }

    /** Android 12+ 才有「精确闹钟」这个概念；低版本一律视为可用 */
    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    /** 跳到系统的「闹钟与提醒」授权页（Android 12+） */
    fun exactAlarmSettingsIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        return Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun fmt(ts: Long): String =
        SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Calendar.getInstance().apply { timeInMillis = ts }.time)
}
