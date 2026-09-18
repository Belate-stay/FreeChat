package com.freechat.sync

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.freechat.data.AppJson
import com.freechat.data.LocalStore
import com.freechat.data.PerConvStore
import com.freechat.model.Conversation
import com.freechat.model.PerConvSettings

/**
 * 本地存储 ↔ 同步对象的映射，以及「本地改了什么」的追踪。
 *
 * 这是 `FreeChatWeb/src/core/sync/adapter.ts` 的对应物，思路照搬：
 * 落盘只有一个入口（[LocalStore]），所以挂一个写入监听就够了，
 * 不必去动业务里几十个改数据的地方。
 *
 * ## 对象怎么切
 *
 *   conv:\<对话id\>      对话元数据（标题、置顶、角色设定…）
 *   msgs:\<对话id\>      该对话的消息列表
 *   mems:\<对话id\>      该对话的记忆条目
 *   settings:global      全局设置
 *
 * 按「一条对话一个箱子」切：两台设备改不同对话时永远不会冲突，
 * 以后加字段也不用动服务端 —— 服务端只认 `kind + id + 版本号 + 一坨 JSON`，
 * 根本不解析业务结构。
 *
 * ## 对话是个数组，得比一比才知道改的是哪条
 *
 * 所有对话躺在**同一个** `freechat_conversations.json` 里，改一条标题整数组都会重写。
 * 所以要先跟上一版比一遍，才知道**具体哪几条**变了 —— 否则每改一个标题
 * 都得把全部对话推一遍。
 */
object Adapter {

    private const val TAG = "SyncAdapter"

    /** 待推送队列。**这是"活的那份"，只属于这里一个** */
    private val dirty = LinkedHashSet<String>()

    private var onDirty: (() -> Unit)? = null

    private var started = false

    /** 上一版的对话数组，用来 diff */
    private var convSnapshot: List<Conversation>? = null

    /** 上一版的每对话设置（convId → 覆盖项），用来 diff —— 只有参与同步的那半变了才推 */
    private var perConvSnapshot: Map<String, PerConvSettings>? = null

    /** 队列变了先通知谁（引擎自己防抖） */
    fun setDirtyHandler(fn: (() -> Unit)?) {
        onDirty = fn
    }

    // ============================================================
    //  键的映射
    // ============================================================

    /** `kind:id` —— 同步状态的键 */
    fun objKey(kind: String, id: String) = "$kind:$id"

    /**
     * 本地逻辑键 → 同步对象；返回 null 表示**这份数据不参与同步**。
     *
     * 不参与的两个，各有各的理由：
     *  - `freechat:perconv` 每对话设置那份**文件**。它一个文件装着一整张表（convId → 覆盖项），
     *    而云端的粒度是「一条对话一个箱子」，所以它不能整体映射成某个对象 ——
     *    改由 [diffPerConv] 逐条比出哪条对话变了，只推那一条（见 [SyncKind.PCSET]）。
     *    而且里面**只有一半参与同步**：另外一半是本机自定义模型的 id，
     *    另一台设备上没有那些模型，同步过去只会选中一个点不通的项。
     *  - 其余（`custom_models` 明文 Key、草稿、图片…）压根不落在这几个文件里。
     */
    fun objectOfKey(key: String): Pair<String, String>? {
        val msgsPrefix = "freechat:msgs:"
        val memPrefix = "freechat:memory:"
        if (key.startsWith(msgsPrefix)) {
            val id = key.removePrefix(msgsPrefix)
            return if (id.isBlank()) null else SyncKind.MSGS to id
        }
        if (key.startsWith(memPrefix)) {
            val id = key.removePrefix(memPrefix)
            return if (id.isBlank()) null else SyncKind.MEMS to id
        }
        // 对话数组走 diff，不在这里直接映射；设置由 SettingsRepository 那边单独通知
        return null
    }

    // ============================================================
    //  脏标记
    // ============================================================

    private fun mark(key: String) {
        val isNew = synchronized(this) { dirty.add(key) }
        if (isNew) notifyDirty()
    }

    fun markDirty(key: String) = mark(key)

    fun unmarkDirty(key: String) {
        synchronized(this) { dirty.remove(key) }
        persistQueue()
    }

    /** 引擎拿它来遍历待推送的对象 */
    fun dirtyKeys(): List<String> = synchronized(this) { dirty.toList() }

    fun pendingCount(): Int = synchronized(this) { dirty.size }

    /**
     * 通知必须是**只调度、不阻塞**的。
     *
     * 写入监听是在 [LocalStore] 的锁里被回调的（写盘那一步已经出锁，但外面很可能
     * 还套着调用方的 `locked {}`）。要是在这里同步去跑同步引擎、引擎又去抢那把锁，
     * 当场死锁。所以只往主线程丢一个信号就返回。
     */
    private fun notifyDirty() {
        val fn = onDirty ?: return
        MAIN.post { runCatching { fn() }.onFailure { Log.e(TAG, "dirty handler failed", it) } }
    }

    /** 队列落盘 —— 用户改完立刻划掉 App 也不能丢 */
    private fun persistQueue() {
        val keys = dirtyKeys()
        runCatching { Session.update({ it.copy(dirty = keys) }) }
            .onFailure { Log.e(TAG, "persist queue failed", it) }
    }

    // ============================================================
    //  对话数组的 diff
    // ============================================================

    /**
     * 对话数组变了 —— 逐条比出**具体哪几条**变了。
     *
     * 只比内容不看时间：`updatedAt` 会被聊天不停顶掉，靠它判断不了"这条到底改没改"。
     */
    private fun diffConversations(next: List<Conversation>) {
        val prev = synchronized(this) {
            convSnapshot.also { convSnapshot = next }
        }
        if (prev == null) return

        val prevMap = prev.associateBy { it.id }.toMutableMap()
        for (c in next) {
            val before = prevMap.remove(c.id)
            if (before == null || !Merge.sameValue(before, c)) mark(objKey(SyncKind.CONV, c.id))
        }
        // 剩下的就是被删掉的 —— 推的时候本地已经读不到它，会走删除
        for (gone in prevMap.keys) mark(objKey(SyncKind.CONV, gone))
    }

    /**
     * 每对话设置变了 —— 逐条比出**哪条对话的「新规则」变了**。
     *
     * 跟 [diffConversations] 同一个套路，只是多了两道筛子：
     *  · **只比参与同步的那半**（[PerConvBridge.syncedHalf]）。用户只是在这台机器上换了个
     *    语言模型？那不该产生任何一次上传 —— 那半个字段的另一台设备根本不认。
     *  · **整条消失 = 删**（对话被删了，它的新规则也该在云端立墓碑），推的时候
     *    本地读不到就会走删除，跟对话数组那边一模一样。
     */
    private fun diffPerConv(next: Map<String, PerConvSettings>) {
        val prev = synchronized(this) {
            perConvSnapshot.also { perConvSnapshot = next }
        }
        if (prev == null) return // 第一次见：只立基线，不当成"从无到有全变了"

        for ((id, s) in next) {
            val before = PerConvBridge.syncedHalf(prev[id])
            val after = PerConvBridge.syncedHalf(s)
            if (Wire.stableJson(before) != Wire.stableJson(after)) mark(objKey(SyncKind.PCSET, id))
        }
        for (gone in prev.keys) if (gone !in next) mark(objKey(SyncKind.PCSET, gone))
    }

    // ============================================================
    //  装上监听
    // ============================================================

    /**
     * 装上监听、开始记录本地改动。
     *
     * **整个进程只装一次，登出也不摘**（和网页端一样）：登出期间用户改的东西
     * 同样要记下来，否则那些改动永远等不到推送。摘了监听等于"看不见"，
     * 比同步失败更糟。
     *
     * 幂等：重复调用不能把 [convSnapshot] 刷新掉 —— 那等于承认"现在这份就是基线"，
     * 中间发生的改动会被当成没发生过。
     */
    fun start() {
        synchronized(this) {
            if (started) return
            started = true
        }
        LocalStore.setWriteListener { key ->
            if (key == LocalStore.CONVERSATIONS_KEY) {
                // 对话数组：读回来跟上一版比出具体哪几条变了。
                // 这里重读一次盘而不是让 LocalStore 把内容也传过来 —— 监听只该做「记一笔」
                // 这种轻活，多传一个可能上兆的字符串进去不值当。回调是在锁外触发的，读得动。
                val list = runCatching {
                    AppJson.gson.fromJson<List<Conversation>>(
                        LocalStore.readText(LocalStore.conversationsFile()) ?: "[]",
                        object : com.google.gson.reflect.TypeToken<List<Conversation>>() {}.type
                    )
                }.getOrNull() ?: emptyList()
                diffConversations(list)
                persistQueue()
                return@setWriteListener
            }
            if (key == LocalStore.PER_CONV_KEY) {
                // 每对话设置：一个文件装一整张表，得比出**哪一条对话**变了（见 diffPerConv）
                diffPerConv(PerConvStore.load())
                persistQueue()
                return@setWriteListener
            }
            val obj = objectOfKey(key) ?: return@setWriteListener
            mark(objKey(obj.first, obj.second))
            persistQueue()
        }
    }

    /** 把盘上记的待推送队列读进内存（进程启动时调一次） */
    fun restoreQueue() {
        val saved = Session.loadSyncStateAny()?.dirty ?: return
        synchronized(this) {
            dirty.clear()
            dirty.addAll(saved.filter { it.contains(':') })
        }
    }

    /**
     * 用磁盘上记的队列**替换**内存里这份。
     *
     * 用替换而不是追加：换了账号就得整个换掉，不能把上一个账号攒的队列推给新账号。
     */
    fun replaceQueue(keys: List<String>) {
        synchronized(this) {
            dirty.clear()
            dirty.addAll(keys)
        }
        persistQueue()
        notifyDirty()
    }

    /**
     * 把本地当前存在的一切排进待推送队列 —— 首次登录时用。
     *
     * 首次登录要把这台设备上已有的东西整个推一遍，否则云端是空的、
     * 而用户会觉得"登录之后我的聊天记录不见了"。
     */
    fun markLocalEverything(conversations: List<Conversation>) {
        val keys = LinkedHashSet<String>()
        for (c in conversations) keys.add(objKey(SyncKind.CONV, c.id))
        for (id in LocalStore.messageConvIds()) keys.add(objKey(SyncKind.MSGS, id))
        for (id in LocalStore.memoryConvIds()) keys.add(objKey(SyncKind.MEMS, id))
        // 「新规则」只推**真定制过**的那些（表里有记录才算）。表里没有的对话一个字都不用说 ——
        // 说了就是「这条对话没定制」，跟云端本来就没有的东西一样，白跑一趟
        val per = PerConvStore.load()
        for (id in per.keys) keys.add(objKey(SyncKind.PCSET, id))
        synchronized(this) {
            convSnapshot = conversations
            perConvSnapshot = per
            dirty.clear()
            dirty.addAll(keys)
        }
        persistQueue()
        notifyDirty()
    }

    /**
     * 补推**存量**：本机已有的角色头像 + 每对话新规则。
     *
     * 和 [markLocalEverything] 的区别是"只补这次新加的那两样" —— 老账号在这台设备上
     * 早就同步过了，全量重推（尤其是那几兆的消息）没必要；而少了这一步，
     * 那些**在本次更新之前**就存在的头像和定制，云端永远见不到
     * （队列只记改动，不记存量）。
     *
     * 头像只挑真的带了图的那几条：角色卡跟着对话对象走，没图的对话推上去也没人看。
     */
    fun markExtras(conversations: List<Conversation>) {
        val keys = LinkedHashSet<String>()
        for (c in conversations) {
            if (!c.characterProfile?.avatarPath.isNullOrEmpty()) keys.add(objKey(SyncKind.CONV, c.id))
        }
        // 表里有记录才算"定制过"，跟云端本来就没有的东西一个字都不用说
        for (id in PerConvStore.load().keys) keys.add(objKey(SyncKind.PCSET, id))
        synchronized(this) { dirty.addAll(keys) }
        persistQueue()
        notifyDirty()
    }

    /**
     * 记下当前对话数组作为 diff 基线（不产生脏标记）。
     *
     * 启动时调一次：不先立个基线，第一次 diff 会把「从 null 变成一整份」
     * 当成全部对话都变了，白白全推一遍。
     */
    fun primeConvSnapshot(conversations: List<Conversation>) {
        synchronized(this) { convSnapshot = conversations }
    }

    private val MAIN = Handler(Looper.getMainLooper())
}
