package com.freechat

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.util.Log

/** Internal foreground trampoline. No exact-alarm permission or background launch is required. */
class IconRestartActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val parentPid = intent.getIntExtra(PARENT_PID, -1)
        if (parentPid <= 0 || parentPid == Process.myPid()) {
            finish()
            return
        }
        // Never accept a PID from another application. The activity is also non-exported.
        val processes = getSystemService(ActivityManager::class.java)?.runningAppProcesses.orEmpty()
        val parent = processes.firstOrNull { it.pid == parentPid }
        if (parent != null && (parent.uid != Process.myUid() || parent.processName != packageName)) {
            finish()
            return
        }
        if (parent != null) Process.killProcess(parentPid)
        try {
            // MainActivity is always enabled, independently of the selected launcher alias.
            // Its fresh Application reads the committed choice and switches aliases enable-first.
            startActivity(Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        } catch (e: Exception) {
            Log.e("LauncherIcon", "Restart launch failed: ${e.javaClass.simpleName}")
        } finally {
            finish()
            Process.killProcess(Process.myPid())
        }
    }

    companion object {
        private const val PARENT_PID = "freechat.icon_restart.parent_pid"

        fun launch(context: Context) {
            context.startActivity(Intent(context, IconRestartActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(PARENT_PID, Process.myPid()))
        }

        fun isRestartProcess(context: Context): Boolean {
            val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Application.getProcessName()
            else context.getSystemService(ActivityManager::class.java)?.runningAppProcesses
                ?.firstOrNull { it.pid == Process.myPid() }?.processName
            return name?.endsWith(":icon_restart") == true
        }
    }
}
