package com.freechat.proactive

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * 闹钟到点：把主动智能的生成服务拉起来。
 *
 * 这里只做一件事——启动前台服务，绝不在这里发网络请求：
 * onReceive 的生命周期只有几秒，一次模型调用动辄十几秒，放在这里必被系统掐断。
 * 前台服务同时解决了两件事：进程有合法身份活下去 + 可以持唤醒锁保证 CPU 不睡。
 */
class ProactiveReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ProactiveScheduler.ACTION_FIRE) return
        val convId = intent.getStringExtra(ProactiveScheduler.EXTRA_CONV_ID).orEmpty()
        if (convId.isBlank()) return

        val app = context.applicationContext
        val store = ProactiveStore(app)
        val req = store.get(convId)
        if (req == null) {
            Log.d("FreeChat", "Proactive fire ignored: no pending request")
            return
        }

        val started = try {
            val svc = Intent(app, ProactiveService::class.java)
                .putExtra(ProactiveScheduler.EXTRA_CONV_ID, convId)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) app.startForegroundService(svc)
            else app.startService(svc)
            true
        } catch (e: Exception) {
            // Android 12+ 后台启动前台服务受限（精确闹钟是官方豁免项，但厂商 ROM 未必照做）
            Log.e("FreeChat", "Proactive service start failed", e)
            false
        }

        if (!started) {
            // 起不来就顺延重试；重试几次仍不行就放弃（否则会每小时空转一次、白耗电）
            if (req.retries < ProactiveStore.MAX_RETRIES) {
                store.put(req.copy(
                    dueAt = System.currentTimeMillis() + ProactiveStore.RETRY_DELAY_MS,
                    retries = req.retries + 1
                ))
                store.get(convId)?.let { ProactiveScheduler.setAlarm(app, it) }
            } else {
                Log.w("FreeChat", "Proactive giving up after retries")
                store.remove(convId)
            }
        }
    }
}

/**
 * 开机 / 应用升级后重新挂闹钟。
 * 这两种情况下系统会把已注册的 AlarmManager 闹钟全部清空，不重挂就等于「关机一次，主动智能永久失效」。
 */
class ProactiveBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON" -> {
                try {
                    ProactiveScheduler.restoreAll(context.applicationContext)
                } catch (e: Exception) {
                    Log.e("FreeChat", "Proactive restore failed", e)
                }
            }
        }
    }
}
