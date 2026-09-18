package com.freechat.proactive

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import com.freechat.MainActivity
import com.freechat.i18n.LocaleManager
import com.freechat.viewmodel.ChatViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 主动智能的生成服务：被闹钟唤醒后，在这里完成一次「要不要开口」的判断与生成。
 *
 * 为什么必须是前台服务：
 * 1. 闹钟把进程拉起来时 App 处于后台，普通后台进程随时可能被回收，一次十几秒的模型调用撑不住；
 * 2. 前台服务可以合法持 PARTIAL_WAKE_LOCK —— 息屏状态下 CPU 会挂起，
 *    不持锁的话网络请求发出去了、回包到了 socket，也没人消费，表现就是「到点了但什么都没发生」。
 *
 * 生成链路的复用：不重写提示词与请求逻辑，直接调用 ChatViewModel 里现成的拟人回复管线。
 * App 正开着就用它现成的实例（状态、缓冲管线全都对得上）；
 * App 没开着（被划掉/重启后）才新建一个 —— 两者都通过 ChatViewModel.live() 判定，
 * 保证同一个进程里只有一个 VM 在写同一个对话文件。
 */
class ProactiveService : Service() {

    companion object {
        private const val TAG = "FreeChat"
        private const val CHANNEL_PROACTIVE = "freechat_proactive"
        private const val NOTIF_ID = 1003
        private const val LOCK_TAG = "FreeChat:proactive"
        /** 唤醒锁兜底持有上限：任何异常路径都不能把锁漏在系统里耗电 */
        private const val MAX_HOLD_MS = 10 * 60 * 1000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wakeLock: PowerManager.WakeLock? = null
    private val timeoutHandler = Handler(Looper.getMainLooper())
    private val timeoutRunnable = Runnable {
        releaseLock()
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val convId = intent?.getStringExtra(ProactiveScheduler.EXTRA_CONV_ID).orEmpty()
        if (convId.isBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        // 必须在 5 秒内进入前台
        ensureChannel()
        val fired = ProactiveStore(this).get(convId)
        startForegroundCompat(fired?.characterName?.ifBlank { "FreeChat" } ?: "FreeChat")
        acquireLock()

        scope.launch {
            try {
                val app = application
                // App 开着 → 用现成的 VM；没开着 → 新建一个专供本次生成
                val live = ChatViewModel.live()
                val vm = live ?: ChatViewModel(app)
                // 冷启动时 App 的前后台标记还停在默认值 true（MainActivity 从没起来过），
                // 据此判断「用户在看」会得出完全相反的结论：TA 好不容易主动找你一次，却静悄悄地什么都没发生。
                // 反过来，App 真开着（前台标记可信）时不该再弹一条 —— 话已经在屏幕上了。
                vm.runProactive(convId, forceNotify = live == null)
            } catch (e: Exception) {
                Log.e(TAG, "Proactive run failed", e)
            } finally {
                // 只清掉「刚触发的那一条」：生成过程中模型可能又给自己定了下一次，
                // 那条是新的计划，清掉它等于把刚挂上的闹钟悄悄废掉（账本里没了 → 到点查不到请求 → 静默失效）。
                // 比对 dueAt 而不是 createdAt：睡着的角色会被改到睡醒之后再找，那是一条新计划、createdAt 不变。
                // 判断与删除在 store 内部同一个锁里完成：分两步做的话，新计划可能正好落在两步之间被误删。
                if (fired != null) {
                    ProactiveStore(this@ProactiveService).removeIfUnchanged(convId, fired.dueAt)
                }
                releaseLock()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        releaseLock()
        super.onDestroy()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val s = LocaleManager.strings()
            val ch = NotificationChannel(CHANNEL_PROACTIVE, s.channelProactive, NotificationManager.IMPORTANCE_MIN).apply {
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
                description = s.channelProactiveDesc
            }
            nm.createNotificationChannel(ch)
        }
    }

    private fun startForegroundCompat(title: String) {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif: Notification =
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                Notification.Builder(this, CHANNEL_PROACTIVE)
            else
                @Suppress("DEPRECATION") Notification.Builder(this))
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(title)
                .setContentText(LocaleManager.strings().proactiveThinking)
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIF_ID, notif)
            }
        } catch (e: Exception) {
            // 前台服务被系统拒绝（厂商 ROM）：退化成普通后台服务继续跑，能跑多久算多久
            Log.e(TAG, "startForeground failed", e)
        }
    }

    private fun acquireLock() {
        try {
            if (wakeLock == null) {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, LOCK_TAG).apply {
                    setReferenceCounted(false)
                }
            }
            wakeLock?.takeIf { it.isHeld }?.release()
            wakeLock?.acquire(MAX_HOLD_MS)
            timeoutHandler.removeCallbacks(timeoutRunnable)
            timeoutHandler.postDelayed(timeoutRunnable, MAX_HOLD_MS)
        } catch (_: Exception) {
        }
    }

    private fun releaseLock() {
        timeoutHandler.removeCallbacks(timeoutRunnable)
        try { wakeLock?.takeIf { it.isHeld }?.release() } catch (_: Exception) {}
    }
}
