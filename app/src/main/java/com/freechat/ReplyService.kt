package com.freechat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager

/**
 * 前台服务：退到后台后 AI 仍在思考回复时保活进程 + 保持 CPU/Wi-Fi 唤醒。
 *
 * 为什么需要 WakeLock：前台服务只能保证「进程不被回收」，保证不了「CPU 不休眠」。
 * 息屏后系统会挂起 CPU，此时流式响应的读线程（OkHttp readUtf8Line）被冻住，
 * 数据到了 socket 但没人消费——用户看到的现象就是「后台还在、没被杀，但等了 5 分钟 AI 也没回」。
 * 因此生成期间持一把 PARTIAL_WAKE_LOCK（只保 CPU，不亮屏）+ 一把 WifiLock（Wi-Fi 射频不掉到省电态），
 * 生成结束 / 服务销毁 / 超时兜底时释放，绝不留长耗电的锁。
 *
 * 保活通知走「静默」渠道（IMPORTANCE_MIN，不悬浮、不响铃、不占状态栏横幅），
 * 回复完成时才用「消息回复」渠道（IMPORTANCE_HIGH）发一条悬浮通知（对标微信新消息）。
 */
class ReplyService : Service() {

    companion object {
        private const val CHANNEL_KEEPALIVE = "freechat_keepalive"  // 静默：前台服务保活
        private const val CHANNEL_REPLY = "freechat_reply"          // 悬浮：新消息回复
        private const val FOREGROUND_NOTIF_ID = 1001
        private const val REPLY_NOTIF_ID = 1002
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_TEXT = "text"
        private const val LOCK_TAG = "FreeChat:generating"

        /** 单次唤醒锁最长持有时间（兜底，防止异常路径把锁漏在系统里耗电） */
        private const val MAX_HOLD_MS = 15 * 60 * 1000L

        /** 当前运行中的服务实例。续期唤醒锁直接在进程内调用，不走 Intent——
         *  Android 8+ 后台启动服务受限，靠 Intent 续期在息屏时会悄无声息地失败。 */
        @Volatile
        private var instance: ReplyService? = null

        /** 前台服务是否真的还活着（进程被系统回收过 / 启动失败过，调用方的标志位要据此对账） */
        fun isRunning(): Boolean = instance != null

        /**
         * 续期唤醒锁（生成期间周期性调用，约每 5 分钟一次）。
         * 长回复（尤其带思考链的）可能超过一把锁的兜底时长，不续期就会在生成中途被释放，
         * 息屏后 CPU 一挂起，用户看到的就是「AI 迟迟不回复」。
         *
         * 服务实例不在就直接返回，**不在这里重启服务**：调用方可能恰好在 startForegroundService
         * 刚发出、实例还没建好的窗口里调进来，那样会白白多起一次服务、还会把保活通知的标题
         * 覆盖成默认值。要不要重拉服务交给调用方的对账逻辑决定（它知道启动宽限期）。
         */
        fun renew(context: Context) {
            instance?.acquireLocks()
        }

        /**
         * 开始前台服务（静默保活通知，不打扰用户）。
         * @return 是否成功启动。失败时调用方不应把「保活已开」记为 true，否则后续不会重试。
         */
        fun startThinking(context: Context, title: String): Boolean {
            return try {
                val intent = Intent(context, ReplyService::class.java)
                    .putExtra(EXTRA_TITLE, title)
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
                else context.startService(intent)
                true
            } catch (_: Exception) {
                // Android 12+ 从后台启动前台服务可能被系统限制（ForegroundServiceStartNotAllowedException），
                // 保活尽力而为，绝不能让「保活失败」反过来把回复流程搞崩。
                false
            }
        }

        /** 发一条「新消息」式悬浮通知（悬浮 + 通知中心，对标微信），点击回到对应对话 */
        fun showReply(context: Context, title: String, text: String) {
            ensureChannels(context)
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(REPLY_NOTIF_ID, buildReplyNotification(context, title, text))
        }

        /** 只清「新消息回复」这一条通知，不动 App 的其它通知 */
        fun clearReply(context: Context) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(REPLY_NOTIF_ID)
        }

        /** 停止前台服务（其保活通知随之消失） */
        fun stop(context: Context) {
            context.stopService(Intent(context, ReplyService::class.java))
        }

        fun ensureChannels(context: Context) {
            if (Build.VERSION.SDK_INT >= 26) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                // 保活渠道：静默，不显示状态栏、不响铃、不悬浮
                val s = com.freechat.i18n.LocaleManager.strings()
                val keepalive = NotificationChannel(
                    CHANNEL_KEEPALIVE, s.channelKeepAlive, NotificationManager.IMPORTANCE_MIN
                ).apply {
                    setShowBadge(false)
                    enableVibration(false)
                    setSound(null, null)
                    description = s.channelKeepAliveDesc
                }
                // 回复渠道：高优先级，触发悬浮通知（heads-up）
                val reply = NotificationChannel(
                    CHANNEL_REPLY, s.channelReply, NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    enableVibration(true)
                    enableLights(true)
                    description = s.channelReplyDesc
                }
                nm.createNotificationChannel(keepalive)
                nm.createNotificationChannel(reply)
            }
        }

        private fun buildThinkingNotification(context: Context, title: String): Notification {
            return Notification.Builder(context, CHANNEL_KEEPALIVE)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(title)
                .setContentText(com.freechat.i18n.LocaleManager.strings().backgroundRunning)
                .setContentIntent(contentIntent(context))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build()
        }

        private fun buildReplyNotification(context: Context, title: String, text: String): Notification {
            return Notification.Builder(context, CHANNEL_REPLY)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(title)
                .setContentText(text.take(80))
                .setStyle(Notification.BigTextStyle().bigText(text))
                .setContentIntent(contentIntent(context))
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setPriority(Notification.PRIORITY_HIGH)
                .setDefaults(Notification.DEFAULT_VIBRATE)
                .build()
        }

        private fun contentIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private val timeoutHandler = Handler(Looper.getMainLooper())
    private val timeoutRunnable = Runnable {
        // 兜底：异常路径（如回复流程中途崩了没走到 stop）也不让锁漏在系统里
        releaseLocks()
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannels(this)
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "FreeChat"
        val notification = buildThinkingNotification(this, title)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(FOREGROUND_NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(FOREGROUND_NOTIF_ID, notification)
        }
        acquireLocks()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        releaseLocks()
        super.onDestroy()
    }

    /** 生成期间持锁：PARTIAL_WAKE_LOCK 保 CPU + WifiLock 保 Wi-Fi 射频。
     *  重复调用 = 续期：必须先释放再重新 acquire —— WakeLock 的 timeout 在第一次 acquire 时就定死了，
     *  光再调一次 acquire() 不会把兜底时间往后推（这是「长回复中途被冻住」的隐藏原因）。 */
    private fun acquireLocks() {
        if (wakeLock == null) {
            try {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, LOCK_TAG).apply {
                    setReferenceCounted(false)
                }
            } catch (_: Exception) {
                wakeLock = null
            }
        }
        try {
            wakeLock?.takeIf { it.isHeld }?.release()
            wakeLock?.acquire(MAX_HOLD_MS)
        } catch (_: Exception) {
        }

        if (wifiLock == null) {
            try {
                val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                @Suppress("DEPRECATION")
                wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, LOCK_TAG).apply {
                    setReferenceCounted(false)
                }
            } catch (_: Exception) {
                wifiLock = null
            }
        }
        try {
            // WifiLock 没有超时概念，但同样先释放再拿，语义统一、幂等
            wifiLock?.takeIf { it.isHeld }?.release()
            wifiLock?.acquire()
        } catch (_: Exception) {
        }

        // 每次开始生成 / 每次续期都重置兜底计时
        timeoutHandler.removeCallbacks(timeoutRunnable)
        timeoutHandler.postDelayed(timeoutRunnable, MAX_HOLD_MS)
    }

    /** 释放全部唤醒锁（幂等，任何路径都能安全调用） */
    private fun releaseLocks() {
        timeoutHandler.removeCallbacks(timeoutRunnable)
        try {
            wakeLock?.takeIf { it.isHeld }?.release()
        } catch (_: Exception) {
        }
        try {
            wifiLock?.takeIf { it.isHeld }?.release()
        } catch (_: Exception) {
        }
    }
}
