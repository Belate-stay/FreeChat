package com.freechat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * 前台服务：退到后台后 AI 仍在思考回复时保活进程（小米 HyperOS 杀后台很凶）。
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

        /** 开始前台服务（静默保活通知，不打扰用户） */
        fun startThinking(context: Context, title: String) {
            try {
                val intent = Intent(context, ReplyService::class.java)
                    .putExtra(EXTRA_TITLE, title)
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
                else context.startService(intent)
            } catch (_: Exception) {
                // Android 12+ 从后台启动前台服务可能被系统限制（ForegroundServiceStartNotAllowedException），
                // 保活尽力而为，绝不能让「保活失败」反过来把回复流程搞崩。
            }
        }

        /** 发一条「新消息」式悬浮通知（悬浮 + 通知中心，对标微信），点击回到对应对话 */
        fun showReply(context: Context, title: String, text: String) {
            ensureChannels(context)
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(REPLY_NOTIF_ID, buildReplyNotification(context, title, text))
        }

        /** 停止前台服务（其保活通知随之消失） */
        fun stop(context: Context) {
            context.stopService(Intent(context, ReplyService::class.java))
        }

        fun ensureChannels(context: Context) {
            if (Build.VERSION.SDK_INT >= 26) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                // 保活渠道：静默，不显示状态栏、不响铃、不悬浮
                val keepalive = NotificationChannel(
                    CHANNEL_KEEPALIVE, "后台保活", NotificationManager.IMPORTANCE_MIN
                ).apply {
                    setShowBadge(false)
                    enableVibration(false)
                    setSound(null, null)
                    description = "AI 后台思考时保持进程存活"
                }
                // 回复渠道：高优先级，触发悬浮通知（heads-up）
                val reply = NotificationChannel(
                    CHANNEL_REPLY, "消息回复", NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    enableVibration(true)
                    enableLights(true)
                    description = "AI 回复通知"
                }
                nm.createNotificationChannel(keepalive)
                nm.createNotificationChannel(reply)
            }
        }

        private fun buildThinkingNotification(context: Context, title: String): Notification {
            return Notification.Builder(context, CHANNEL_KEEPALIVE)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(title)
                .setContentText("后台运行中")
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannels(this)
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "FreeChat"
        val notification = buildThinkingNotification(this, title)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(FOREGROUND_NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(FOREGROUND_NOTIF_ID, notification)
        }
        return START_NOT_STICKY
    }
}
