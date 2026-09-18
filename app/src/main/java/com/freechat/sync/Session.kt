package com.freechat.sync

import android.content.Context
import android.content.SharedPreferences
import com.freechat.data.AppJson

/**
 * 登录态与同步进度 —— 存在 SharedPreferences 里（App 私有目录，别的应用读不到）。
 *
 * 两块东西：
 *   - [Auth]      令牌 + 账号
 *   - [SyncState] 拉到哪了（游标）、每个对象同步到第几版、待推送队列的快照、上次同步时间
 *
 * ## 为什么待推送队列必须落盘
 *
 * 用户改完一句话立刻把 App 划掉，那次改动不能就这么算了。所以**每次本地写入都会
 * 更新队列并落盘**（由 [com.freechat.sync.Adapter] 负责），下次打开接着推。
 *
 * ## 换了账号就当从零开始
 *
 * 游标和版本号都是**服务端针对某个账号**发的，套到另一个账号上会指向完全不同、
 * 甚至根本不存在的对象。所以 [loadSyncState] 一发现盘上那份的 userId 对不上，
 * 整个弃掉。
 *
 * ## 登出**不清**队列
 *
 * 登出期间用户照样能聊天、能建对话，这些改动当然也要在下次登录时推上去。
 * 清了队列等于把这些改动判了死刑，而且不会报任何错。
 */
object Session {

    private const val PREFS = "freechat_session"
    private const val K_AUTH = "auth"
    private const val K_SYNC = "sync_state"

    private val LOCK = Any()

    @Volatile
    private var sp: SharedPreferences? = null

    @Volatile
    private var authCache: Auth? = null

    @Volatile
    private var authLoaded = false

    @Volatile
    private var syncCache: SyncState? = null

    fun init(context: Context) {
        if (sp != null) return
        sp = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    // ============================================================
    //  登录态
    // ============================================================

    data class Auth(
        val token: String = "",
        val userId: String = "",
        val username: String = ""
    ) {
        fun isValid(): Boolean = token.isNotEmpty() && userId.isNotEmpty()
    }

    /** 只读内存，可能还没从盘上读出来 —— 同步的启动路径上不要用它判断"登没登录" */
    fun peekAuth(): Auth? = authCache

    fun isLoggedIn(): Boolean = authCache?.isValid() == true

    fun loadAuth(): Auth? {
        if (authLoaded) return authCache
        val raw = prefs()?.getString(K_AUTH, null)
        val parsed = raw?.let { runCatching { AppJson.gson.fromJson(it, Auth::class.java) }.getOrNull() }
        authCache = if (parsed != null && parsed.isValid()) parsed else null
        authLoaded = true
        return authCache
    }

    fun saveAuth(auth: Auth) {
        authCache = auth
        authLoaded = true
        write(K_AUTH, auth)
    }

    /**
     * 清登录态。**注意它不清 [SyncState]** —— 队列要留着，见类注释。
     * 换账号时 [loadSyncState] 会因为 userId 对不上而自动弃掉旧进度。
     */
    fun clearAuth() {
        authCache = null
        authLoaded = true
        prefs()?.edit()?.remove(K_AUTH)?.apply()
    }

    // ============================================================
    //  同步进度
    // ============================================================

    data class SyncState(
        val userId: String = "",
        /** 上次拉到的服务器自增游标 */
        val cursor: Long = 0,
        /** 每个对象最后一次同步到的服务器版本号，键是 `kind:id` */
        val revs: Map<String, Long> = emptyMap(),
        /** 待推送队列**落盘的一份快照**。活的那份在 [Adapter]，只属于它一个 */
        val dirty: List<String> = emptyList(),
        /** 上次同步成功的时间（给界面显示） */
        val lastSyncAt: Long = 0,
        /**
         * 这台设备上的「存量补推」做过了没有（角色头像、每对话新规则）。
         *
         * 队列只记**改动**，不记存量：老账号在这台设备上早就同步过了（游标不是 0），
         * 所以 [SyncEngine.seedEverything] 那条路不会再走 —— 于是**本次更新之前**
         * 就存在的角色头像和定制，云端压根没见过，要等用户哪天恰好再动一下那条对话
         * 才轮得到。头像尤其一眼看得出：换台设备打开就少张脸。
         */
        val seededExtras: Boolean = false
    )

    private fun emptyState(userId: String) = SyncState(userId = userId)

    /** 当前内存里那份（可能还没读盘） */
    fun peekSyncState(): SyncState? = syncCache

    fun loadSyncState(userId: String): SyncState {
        if (syncCache?.userId == userId) return syncCache!!
        val raw = prefs()?.getString(K_SYNC, null)
        val parsed = raw?.let { runCatching { AppJson.gson.fromJson(it, SyncState::class.java) }.getOrNull() }
        // 换了账号就当从零开始 —— 绝不把 A 的游标和版本号套到 B 上
        syncCache = if (parsed != null && parsed.userId == userId) {
            SyncState(
                userId = userId,
                cursor = parsed.cursor,
                revs = parsed.revs ?: emptyMap(),
                dirty = parsed.dirty ?: emptyList(),
                lastSyncAt = parsed.lastSyncAt,
                seededExtras = parsed.seededExtras
            )
        } else emptyState(userId)
        return syncCache!!
    }

    /**
     * 不问你登的是谁，把盘上那份读进内存。
     *
     * 登出状态也需要它：那会儿用户照样在改东西，改动要进队列，而队列挂在 [Adapter] 上、
     * 由它顺手存进这里。没有它，登出期间改的东西一个字节都留不下来。
     *
     * 读到的可能是**上一个账号**的状态 —— 不要紧，真登录时会走 [loadSyncState]，
     * 对不上号就整个弃掉重来。
     */
    fun loadSyncStateAny(): SyncState? {
        syncCache?.let { return it }
        val raw = prefs()?.getString(K_SYNC, null) ?: return null
        val parsed = runCatching { AppJson.gson.fromJson(raw, SyncState::class.java) }.getOrNull() ?: return null
        if (parsed.userId.isEmpty()) return null
        syncCache = parsed
        return parsed
    }

    /**
     * 当前这份同步状态（内存里没有就从盘上读，还没有就给个空的）。
     *
     * 每次落盘都写整份，所以 `mode` 这里用**读-改-写**：调用方拿来改一改再存回去。
     */
    fun syncState(): SyncState = syncCache ?: loadSyncStateAny() ?: SyncState()

    /**
     * 改并存。
     *
     * [immediate] 为真时立刻同步写盘 —— 用在「推送成功后把 key 从队列里摘掉」
     * 这类**丢了就会重复推**的场合；平时（游标推进）走异步，
     * 免得推送循环里每动一下就卡一次 IO。
     */
    fun update(block: (SyncState) -> SyncState, immediate: Boolean = false) {
        val next = synchronized(LOCK) {
            val cur = syncCache ?: SyncState()
            block(cur).also { syncCache = it }
        }
        write(K_SYNC, next, immediate)
    }

    /** 账号注销时用：连进度一起清掉，别留一个「账号已经不在了、游标还在」的残骸 */
    fun clearSyncState() {
        synchronized(LOCK) { syncCache = null }
        prefs()?.edit()?.remove(K_SYNC)?.apply()
    }

    // ============================================================

    private fun prefs(): SharedPreferences? = sp

    private fun write(key: String, value: Any, immediate: Boolean = false) {
        val p = prefs() ?: return
        val json = AppJson.gson.toJson(value)
        val editor = p.edit().putString(key, json)
        // 队列那类"丢了就得重来一遍"的东西同步写；其余交给 apply 在后台线程落盘
        if (immediate) editor.commit() else editor.apply()
    }
}
