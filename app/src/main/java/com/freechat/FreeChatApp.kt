package com.freechat

import android.app.Application
import com.freechat.i18n.LocaleManager

class FreeChatApp : Application() {
    override fun onCreate() {
        super.onCreate()
        LocaleManager.captureSystemLocale()
    }
}
