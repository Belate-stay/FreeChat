package com.freechat.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.freechat.model.AppIcon
import com.freechat.model.activationOrder

/** Device-local. Choosing only saves a request; the launcher changes on the next process start. */
class LauncherIconManager(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("freechat_launcher_icon", Context.MODE_PRIVATE)
    private val pm = app.packageManager
    fun requestedIcon(): AppIcon = AppIcon.fromId(prefs.getString("requested_icon", null))
    fun request(icon: AppIcon): Boolean = prefs.edit().putString("requested_icon", icon.id).commit()

    private fun component(icon: AppIcon) = ComponentName(app.packageName, "${app.packageName}.${icon.alias}")
    private fun enabled(icon: AppIcon): Boolean = when (pm.getComponentEnabledSetting(component(icon))) {
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
        PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> icon.enabledByDefault
        else -> false
    }
    fun activeIcon(): AppIcon = AppIcon.entries.firstOrNull { enabled(it) } ?: AppIcon.default

    fun applyPendingOnProcessStart(): Boolean = try {
        val target = requestedIcon()
        val changes = target.activationOrder().filter { enabled(it) != (it == target) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (changes.isNotEmpty()) pm.setComponentEnabledSettings(changes.map {
                PackageManager.ComponentEnabledSetting(component(it),
                    if (it == target) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP)
            })
        } else {
            // Enable first. An interrupted update can leave two icons, but never leave no entry.
            changes.forEach {
                pm.setComponentEnabledSetting(component(it),
                    if (it == target) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP)
            }
        }
        true
    } catch (e: Exception) {
        // The pending request remains available to retry. Never disable MainActivity or kill a chat.
        Log.w("LauncherIcon", "Launcher update unavailable: ${e.javaClass.simpleName}")
        false
    }
}
