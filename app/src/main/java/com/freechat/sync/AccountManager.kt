package com.freechat.sync

/**
 * 账号：注册 / 登录 / 登出 / 找回。
 *
 * 流程和网页端 `src/core/sync/account.ts` 一条一条对齐 —— 两端对"同一个账号"的
 * 理解必须一致，否则同一份数据在两边会各自算出一套结果。
 *
 * ## 登出**不清**待推队列
 *
 * 登出期间用户照样能聊天、能建对话，那些改动要攒着，等再登回来推上去。
 * 清了等于把它们判了死刑，而且不会报任何错。
 *
 * ## 换账号要**整个换掉**队列
 *
 * 上一个账号攒的待推队列一个都不能留给新账号 —— 那些东西是别人的。
 */
object AccountManager {

    // ============================================================
    //  冷启动
    // ============================================================

    /**
     * App 起来时调一次。
     *
     * 有令牌就先跟服务器确认一下还作不作数 —— 令牌可能已经在别处改密码时被吊销了，
     * 这时候界面还显示"已登录"、却什么都同步不上去，是最让人困惑的状态。
     * **网络不通不算失效**：先按已登录算，等下一轮自己重试。
     */
    suspend fun bootstrap(): Session.Auth? {
        val auth = Session.loadAuth() ?: run {
            // 没登录也要把上次留下的那份状态读进来 —— 登出期间的改动才有地方记账
            Session.loadSyncStateAny()
            return null
        }
        try {
            val (user, _) = ApiClient.me(auth.token)
            Session.saveAuth(auth.copy(userId = user.id, username = user.username))
            // 顺手对一下头像的版本号：对得上就用盘上那份，对不上才下新的
            AvatarStore.loadFromDisk(user.id)
            runCatching { refreshAvatar(user) }
        } catch (e: ApiError) {
            if (e.isUnauthorized) {
                Session.clearAuth()
                AvatarStore.clear()
                SyncEngine.signOut()
                return null
            }
            // 网络不通：当作还登着。头像也退回盘上那份
            AvatarStore.loadFromDisk(auth.userId)
        }
        // 头像上面已经核对过了，这里不要再白跑一趟 GET /me
        beginSession(loadAvatar = false)
        return Session.loadAuth()
    }

    /**
     * 登录成功之后接通同步。
     *
     * [seed] 只在**这个账号在这台设备上第一次登录**时为真 —— 那一刻本地已有的
     * 全部数据都要送上去，和云端那份做并集。之后每次登录都不再全量重推：
     * 服务端虽然能识别"内容没变就不动版本号"，但把全部对话重新传一遍本身就很贵。
     */
    private suspend fun beginSession(loadAvatar: Boolean = true) {
        val auth = Session.loadAuth() ?: return

        if (loadAvatar) {
            // 刚登录：本地那些图未必是这个账号的，先无条件换成这个账号的（没有就是没有）
            AvatarStore.loadFromDisk(auth.userId)
            runCatching {
                val (u, _) = ApiClient.me(auth.token)
                refreshAvatar(u)
            }
        }

        // 先记住上一次是谁 —— 登出期间本地改动攒在 [Adapter] 里，同一个账号要接上
        val prevUserId = Session.peekSyncState()?.userId
        val state = Session.loadSyncState(auth.userId)

        if (prevUserId == auth.userId) {
            // 同一个账号：盘上那份 + 内存里登出期间攒的，取并集，别冲掉任何一边
            Adapter.replaceQueue((state.dirty + Adapter.dirtyKeys()).distinct())
        } else {
            // 换了账号（或头一次登录）：整个换掉 —— 绝不能把上个账号的待推队列推给新账号
            Adapter.replaceQueue(state.dirty)
        }

        val fresh = state.cursor == 0L && state.revs.isEmpty()
        if (fresh) {
            SyncEngine.seedEverything()
        } else if (!state.seededExtras) {
            // 老账号：把这次新加的两样（角色头像、每对话新规则）的存量补一遍
            SyncEngine.seedExtras()
        }

        SyncEngine.syncSoon(0)
    }

    // ============================================================
    //  注册 / 登录 / 登出
    // ============================================================

    /**
     * 注册。返回**恢复码** —— 只在这一次返回，丢了就只能靠它找回账号，
     * 界面必须让用户当场存下来（截图/复制），不能只是弹一下就过去。
     */
    suspend fun register(username: String, password: String): String {
        val res = ApiClient.register(username, password)
        Session.saveAuth(Session.Auth(res.token, res.user.id, res.user.username))
        beginSession()
        return res.recoveryCode.orEmpty()
    }

    suspend fun login(username: String, password: String) {
        val res = ApiClient.login(username, password)
        Session.saveAuth(Session.Auth(res.token, res.user.id, res.user.username))
        beginSession()
    }

    /** 用恢复码重设密码。成功后服务端会吊销其它设备的所有令牌，并下发一张新的恢复码 */
    suspend fun recover(username: String, recoveryCode: String, newPassword: String): String {
        val res = ApiClient.recover(username, recoveryCode, newPassword)
        Session.saveAuth(Session.Auth(res.token, res.user.id, res.user.username))
        beginSession()
        return res.recoveryCode.orEmpty()
    }

    suspend fun logout() {
        val auth = Session.loadAuth()
        if (auth != null) {
            // 令牌本来就已经失效了也无所谓，本地登出照做
            runCatching { ApiClient.logout(auth.token) }
        }
        // 刻意**不清 sync_state**：同一个账号再登回来时能接着上次的进度，不用全量重传
        AvatarStore.clear()
        SyncEngine.signOut(wipeProgress = false)
    }

    /**
     * 注销账号：服务器上的一切都会没有。
     *
     * [wipeProgress] 一并清掉同步进度 —— 账号都不在了，留一个「游标还在」的残骸
     * 只会让下次登录时读到一份指向虚空的进度。**本地数据不动**：
     * 用户在这个设备上写的对话还是他的。
     */
    suspend fun deleteAccount(password: String) {
        val auth = Session.loadAuth() ?: throw ApiError(401, "unauthorized", com.freechat.i18n.LocaleManager.strings().notLoggedIn)
        ApiClient.deleteAccount(auth.token, password)
        AvatarStore.clear()
        SyncEngine.signOut(wipeProgress = true)
    }

    // ============================================================
    //  账号管理
    // ============================================================

    suspend fun changePassword(oldPassword: String, newPassword: String) {
        val auth = Session.loadAuth() ?: throw ApiError(401, "unauthorized", com.freechat.i18n.LocaleManager.strings().notLoggedIn)
        ApiClient.changePassword(auth.token, oldPassword, newPassword)
    }

    suspend fun regenerateRecoveryCode(password: String): String {
        val auth = Session.loadAuth() ?: throw ApiError(401, "unauthorized", com.freechat.i18n.LocaleManager.strings().notLoggedIn)
        return ApiClient.regenerateRecoveryCode(auth.token, password)
    }

    suspend fun me(): Pair<SyncUser, UsageInfo> {
        val auth = Session.loadAuth() ?: throw ApiError(401, "unauthorized", com.freechat.i18n.LocaleManager.strings().notLoggedIn)
        return ApiClient.me(auth.token)
    }

    suspend fun listTokens(): List<TokenInfo> {
        val auth = Session.loadAuth() ?: throw ApiError(401, "unauthorized", com.freechat.i18n.LocaleManager.strings().notLoggedIn)
        return ApiClient.listTokens(auth.token)
    }

    suspend fun revokeToken(id: String) {
        val auth = Session.loadAuth() ?: throw ApiError(401, "unauthorized", com.freechat.i18n.LocaleManager.strings().notLoggedIn)
        ApiClient.revokeToken(auth.token, id)
    }

    // ============================================================
    //  资料：用户名 / 头像
    // ============================================================

    /**
     * 改用户名。改完要**同时更新本地那份 auth** —— 账号页和同步引擎都从它读名字，
     * 只改服务器那边的话，界面会一直显示旧名字直到下次冷启动。
     */
    suspend fun setUsername(username: String): SyncUser {
        val auth = Session.loadAuth() ?: throw ApiError(401, "unauthorized", com.freechat.i18n.LocaleManager.strings().notLoggedIn)
        val user = ApiClient.changeUsername(auth.token, username)
        Session.saveAuth(auth.copy(userId = user.id, username = user.username))
        return user
    }

    /** 上传头像（JPEG 字节）。成功后本地缓存立刻换成新图，不用再下回来一遍 */
    suspend fun uploadAvatar(jpeg: ByteArray) {
        val auth = Session.loadAuth() ?: throw ApiError(401, "unauthorized", com.freechat.i18n.LocaleManager.strings().notLoggedIn)
        val b64 = android.util.Base64.encodeToString(jpeg, android.util.Base64.NO_WRAP)
        val rev = ApiClient.putAvatar(auth.token, "image/jpeg", b64)
        AvatarStore.cacheLocally(auth.userId, jpeg, rev)
    }

    suspend fun removeAvatar() {
        val auth = Session.loadAuth() ?: throw ApiError(401, "unauthorized", com.freechat.i18n.LocaleManager.strings().notLoggedIn)
        ApiClient.deleteAvatar(auth.token)
        AvatarStore.cacheLocally(auth.userId, null, 0L)
    }

    /**
     * 把云端头像同步到本地缓存。**只在版本号变了的时候才真下** —— 这是抽屉左下角
     * 那个圆头像的数据来源，每次冷启动都重下一遍图是没必要的流量。
     */
    suspend fun refreshAvatar(user: SyncUser) {
        if (AvatarStore.localRev(user.id) == user.avatarRev.toLong()) return
        if (user.avatarRev <= 0) {
            AvatarStore.cacheLocally(user.id, null, 0L)
            return
        }
        val auth = Session.loadAuth() ?: return
        val got = ApiClient.getAvatar(auth.token) ?: run {
            AvatarStore.cacheLocally(user.id, null, 0L)
            return
        }
        val bytes = runCatching { android.util.Base64.decode(got.second, android.util.Base64.DEFAULT) }.getOrNull() ?: return
        AvatarStore.cacheLocally(user.id, bytes, user.avatarRev.toLong())
    }

    /** 本地有多少数据 —— 登录页拿它告诉用户"这些东西会跟着账号走" */
    suspend fun localSummary(): String {
        val convs = Relay.conversations()
        val msgs = convs.sumOf { Relay.messagesFromDisk(it.id).size }
        return com.freechat.i18n.LocaleManager.strings().localDataSummary(convs.size, msgs)
    }
}
