package com.freechat.i18n

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

enum class AppLanguage(val code: String, val label: String, val labelEn: String) {
    SYSTEM("system", "跟随系统", "Follow System"),
    ZH_CN("zh-CN", "简体中文", "Simplified Chinese"),
    ZH_TW("zh-TW", "繁體中文", "Traditional Chinese"),
    EN("en", "English", "English");

    companion object {
        fun fromCode(code: String): AppLanguage =
            entries.find { it.code == code } ?: SYSTEM
    }
}

object LocaleManager {

    // 保存 App 启动时的原始系统 Locale，防止被 setDefault() 污染
    private var originalSystemLocale: Locale? = null

    /**
     * 当前生效的语言代码。
     *
     * 界面里的文字走 `LocalStrings`（CompositionLocal），但 **ViewModel、服务、通知**
     * 这些不在组合里的地方拿不到它 —— 而它们照样有要给用户看的字（同步失败的提示、
     * 主动消息的通知标题）。所以这里留一份当前语言的全局影子：只在 [applyLocale]
     * 里更新，任何切语言的地方都绕过它，读的时候永远和界面同源。
     */
    @Volatile
    private var resolvedCode: String = "zh-CN"

    /** 非 Composable 场景（ViewModel / 后台服务 / 通知）取当前语言包 */
    fun strings(): AppStrings = buildStrings(resolvedCode)

    /**
     * 记下当前生效的语言。启动时（没法走 applyLocale 那条路，那时候还没 Activity）
     * 由 FreeChatApp 从设置里读出来灌一次，之后交给 [applyLocale]。
     */
    fun rememberResolved(resolved: String) {
        resolvedCode = resolved
    }

    /** 在 Application.onCreate 中调用，记录原始系统 locale */
    fun captureSystemLocale() {
        if (originalSystemLocale == null) {
            originalSystemLocale = Locale.getDefault()
        }
    }

    fun applyLocale(context: Context, language: AppLanguage) {
        rememberResolved(resolveLocale(language))
        val locale = when (language) {
            AppLanguage.SYSTEM -> originalSystemLocale ?: Locale.getDefault()
            AppLanguage.ZH_CN -> Locale.SIMPLIFIED_CHINESE
            AppLanguage.ZH_TW -> Locale.TRADITIONAL_CHINESE
            AppLanguage.EN -> Locale.ENGLISH
        }
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        @Suppress("DEPRECATION")
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }

    /** Determine which concrete locale to use for string lookup */
    fun resolveLocale(language: AppLanguage): String = when (language) {
        AppLanguage.SYSTEM -> {
            val default = originalSystemLocale ?: Locale.getDefault()
            when {
                default.language.equals("zh", ignoreCase = true) && default.country.equals("TW", ignoreCase = true) -> "zh-TW"
                default.language.equals("zh", ignoreCase = true) -> "zh-CN"
                else -> "en"
            }
        }
        else -> language.code
    }
}
