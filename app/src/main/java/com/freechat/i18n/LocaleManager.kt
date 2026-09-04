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

    /** 在 Application.onCreate 中调用，记录原始系统 locale */
    fun captureSystemLocale() {
        if (originalSystemLocale == null) {
            originalSystemLocale = Locale.getDefault()
        }
    }

    fun applyLocale(context: Context, language: AppLanguage) {
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
