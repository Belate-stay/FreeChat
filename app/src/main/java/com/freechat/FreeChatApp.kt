package com.freechat

import android.app.Application
import com.freechat.data.LocalStore
import com.freechat.data.SettingsRepository
import com.freechat.i18n.AppLanguage
import com.freechat.i18n.LocaleManager
import kotlinx.coroutines.flow.first
import com.freechat.sync.AccountManager
import com.freechat.sync.AvatarStore
import com.freechat.sync.Session
import com.freechat.sync.SyncEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FreeChatApp : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        LocaleManager.captureSystemLocale()

        // 顺序有讲究，三条都不能换：
        // 1. 存储层的唯一写入口 —— 必须早于任何读盘（各 ViewModel/Service 起来之前）
        LocalStore.init(filesDir)
        // 2. 登录态 —— 同步引擎启动时要读令牌，追在它后面会读到一份空的
        Session.init(this)
        // 3. 同步引擎 —— 它会装上写入监听。**没登录也要装**：登出期间用户照样在改东西，
        //    那些改动得进待推队列，不然等哪天登录了会凭空消失，而且不报任何错。
        SyncEngine.init(this)
        // 4. 头像缓存 —— 抽屉左下角那个圆头像每次拉侧栏都在，得先有盘上那份垫着。
        //    真正跟服务器对账是下面 bootstrap 里的事。
        AvatarStore.attach(this)
        Session.peekAuth()?.userId?.let { AvatarStore.loadFromDisk(it) }
        if (Session.peekAuth() == null) AvatarStore.clear()

        // 语言包在组合之外也要能用（通知标题、后台提示），而 MainActivity 里那次 applyLocale
        // 要等到界面起来才跑。所以这里先按存档灌一次：不然冷启动就弹通知的场景会拿到默认简体。
        scope.launch {
            runCatching {
                val code = SettingsRepository(this@FreeChatApp).languageCode.first()
                LocaleManager.rememberResolved(LocaleManager.resolveLocale(AppLanguage.fromCode(code)))
            }
        }

        // 有令牌就跟服务器确认一下还作不作数。网络不通不算失效，等下一轮自己重试。
        scope.launch { runCatching { AccountManager.bootstrap() } }
    }
}
