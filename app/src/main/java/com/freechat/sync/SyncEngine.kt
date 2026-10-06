package com.freechat.sync

import android.content.Context
import android.util.Log
import com.freechat.data.LocalStore
import com.freechat.data.SettingsRepository
import com.freechat.i18n.LocaleManager
import com.freechat.model.Conversation
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * 同步引擎：**先拉后推**，一轮一轮跑到收敛。
 *
 * ## 三条不变式（违反任何一条都会静默丢数据）
 *
 * 1. **绝不静默覆盖**。服务端版本号对不上就回 409，引擎负责合并，不做谁覆盖谁。
 * 2. **删除是墓碑**，不是把行删掉。否则另一台设备分不清"你删了"和"我还没拉到"。
 * 3. **游标是服务端的自增 seq**，不是客户端时间戳。多设备时钟不可信，
 *    用本地时间当游标，两端时钟一有偏差就会静默漏掉一批改动。
 *
 * ## 为什么必须先拉
 *
 * 先拉才能知道云端现在是什么样。上来就推的话，本地这份是基于**旧的**云端状态算出来的，
 * 推上去就是把别人的改动盖掉 —— 而且服务端只会回 409，什么都不会替你做。
 *
 * ## 一轮的形状
 *
 *   拉（changes → fetch → 合并落盘）
 *   推（dirty 队列 → PUT/DELETE，409 就地合并）
 *   再推一轮（合并可能又把东西标脏了）
 *
 * 循环上限 4 轮：正常情况下第二轮之后就没事干了，留着上限是防止
 * 「两台设备正在同时对推」这种边界把循环卡死。
 */
object SyncEngine {

    private const val TAG = "SyncEngine"
    private val pushingVersions = mutableMapOf<String, Long>()

    /** 推一轮最多来回几次。正常 1 次，409 合并后 2 次，再多说明对面在猛推，见好就收 */
    private const val MAX_PUSH_ROUNDS = 4

    /** 补缺时翻云端清单的页数上限（200/页 × 50 = 一万个对象，远超真实账号；见 seedMissing） */
    private const val MAX_SEED_PAGES = 50

    enum class Phase { OFF, IDLE, SYNCING, ERROR }

    data class Status(
        val phase: Phase = Phase.OFF,
        val lastSyncAt: Long = 0,
        val error: String? = null,
        val pending: Int = 0,
        /** 需要用户知道一下的事（冲突副本、空间满…），看一眼就能清 */
        val notices: List<String> = emptyList()
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    @Volatile
    private var settingsRepo: SettingsRepository? = null

    private var debounce: Job? = null

    /** 每个对象同步到第几版了。**只属于一轮同步的局部状态**，一轮开始从盘上读、结束写回去 */
    private val revs = mutableMapOf<String, Long>()

    private var cursor = 0L

    /**
     * 本轮拉取里出现过的对话 id（只属于一轮同步，见 [pull]）。
     *
     * 用途只有一个：消息那条防线的判断 —— 远端来了一条消息，而本地还没有它的消息文件时，
     * 得先分清楚是「这台设备还没收到过」还是「用户在这台设备上删了这条对话」。
     */
    private val pulledConvIds = mutableSetOf<String>()

    // ============================================================
    //  装配
    // ============================================================

    /**
     * 由 `FreeChatApp.onCreate` 调一次。
     *
     * 注意这里**不管登没登录**都要装监听：登出期间用户照样在改东西，
     * 那些改动得进队列，等下次登录推上去。
     */
    fun init(context: Context) {
        val app = context.applicationContext
        settingsRepo = SettingsRepository(app)

        Adapter.start()
        Adapter.restoreQueue()
        Adapter.setDirtyHandler { schedule() }
        // 设置在另一个仓库里（DataStore 不是文件），得单独挂一次监听
        SettingsRepository.setWriteListener {
            Adapter.markDirty(Adapter.objKey(SyncKind.SETTINGS, SyncKind.SETTINGS_ID))
        }

        if (Session.loadAuth() != null) {
            _status.value = _status.value.copy(phase = Phase.IDLE)
            schedule(800)
        }
        updatePending()
    }

    /** 有没有登录 —— 界面用它决定显示哪一套 */
    fun isLoggedIn(): Boolean = Session.isLoggedIn()

    /**
     * 安排一次同步（防抖）。
     *
     * 用户连着改三样东西只跑一轮 —— 每改一个字就推一次既浪费又容易撞上 409。
     */
    fun schedule(delayMs: Long = 1200) {
        updatePending()
        if (Session.loadAuth() == null) return
        debounce?.cancel()
        debounce = scope.launch {
            delay(delayMs)
            runCatching { syncNow() }
        }
    }

    /** App 回到前台、或者用户手动点了同步 */
    fun syncSoon(delayMs: Long = 0) = schedule(delayMs)

    // ============================================================
    //  一轮
    // ============================================================

    suspend fun syncNow(): Boolean = mutex.withLock {
        val auth = Session.loadAuth()
        if (auth == null) {
            _status.value = _status.value.copy(phase = Phase.OFF)
            return@withLock false
        }
        val userId = auth.userId

        val saved = Session.loadSyncState(userId)
        synchronized(revs) {
            revs.clear()
            revs.putAll(saved.revs)
        }
        cursor = saved.cursor

        _status.value = _status.value.copy(phase = Phase.SYNCING, error = null)

        val ok = try {
            pull(auth, userId)
            if (accountChanged(userId)) return@withLock false
            cleanupConflictCopies()
            if (accountChanged(userId)) return@withLock false
            push(auth, userId)
            if (accountChanged(userId)) return@withLock false

            val now = System.currentTimeMillis()
            persist(now)
            _status.value = _status.value.copy(
                phase = Phase.IDLE, lastSyncAt = now, error = null, pending = Adapter.pendingCount()
            )
            true
        } catch (e: ApiError) {
            if (e.isUnauthorized) {
                // 令牌失效了（在别处登出、过期、或者改了密码）—— 清掉，让界面回到未登录
                Session.clearAuth()
                notice(LocaleManager.strings().syncLoginExpired)
                _status.value = _status.value.copy(phase = Phase.OFF, error = null, pending = Adapter.pendingCount())
            } else if (e.isNetworkError) {
                // 网不通不是错误状态，等会儿自己会好。**不要**清脏标记、不要动游标
                _status.value = _status.value.copy(error = null)
            } else {
                Log.e(TAG, "sync failed", e)
                _status.value = _status.value.copy(phase = Phase.ERROR, error = e.message)
            }
            false
        } catch (e: CancellationException) {
            // 防抖被取消、或者用户中途离开了这一页。状态交给下面的 finally 收尾，这里只负责把取消传下去。
            throw e
        } catch (e: Throwable) {
            // **必须是 Throwable，不能是 Exception**。
            // 这条路径上的栈溢出、OOM 都是 Error 而不是 Exception，原来的 `catch (Exception)`
            // 一个都接不住 —— 它们会直接穿出 syncNow，而调用方是 `runCatching`，
            // 连一行日志都不会留下，用户看到的就是「一直同步中，永远不动」。
            Log.e(TAG, "sync failed", e)
            _status.value = _status.value.copy(phase = Phase.ERROR, error = e.message ?: LocaleManager.strings().syncFailed)
            false
        } finally {
            /**
             * 兜底：**任何**一条离开路径都不许把状态留在 SYNCING。
             *
             * 上面那几个 `return@withLock false`（账号中途变了、用户登出）和 Error 穿透
             * 都会绕过所有赋值直接走人，状态就那么定格在「同步中」—— 而且再也不会自己
             * 变回来：下一轮只会重新设一次 SYNCING，再挂一次，界面上就是一个永远转的圈。
             * 只有 ERROR 是「有主」的正常出口，其余一律落回 IDLE/OFF。
             */
            if (_status.value.phase == Phase.SYNCING) {
                _status.value = _status.value.copy(
                    phase = if (Session.loadAuth() == null) Phase.OFF else Phase.IDLE
                )
            }
        }
        return@withLock ok
    }

    private fun persist(lastSyncAt: Long) {
        Session.update({
            it.copy(
                revs = synchronized(revs) { revs.toMap() },
                cursor = cursor,
                lastSyncAt = lastSyncAt,
                dirty = Adapter.dirtyKeys()
            )
        }, immediate = true)
    }

    /**
     * 这一轮开始时的账号还在不在。
     *
     * 用户完全可以在同步跑着的时候登出、换号再登录，而这一轮手里攥的是**旧令牌**。
     * 没有这道闸，A 的推送循环会把 B 刚塞进同一个脏集合的本地数据推去 A 的云端 ——
     * 既串了账号，B 自己那份还因为被摘了脏标记而永远不上传。
     * 撞上就整轮作废：脏标记**一个都不摘**，下一个账号自己的那轮从头推。
     */
    private fun accountChanged(userId: String): Boolean =
        Session.loadAuth()?.userId?.let { it != userId } ?: true

    // ============================================================
    //  拉
    // ============================================================

    /**
     * 1.0.99.3：清理存量「冲突副本」套娃（见 [ConflictCopyCleanup]）。
     *
     * 放在 pull 之后 push 之前 —— 云端拉回来的套娃副本当场删掉并带着墓碑推回去，
     * **一轮收敛**：下一轮起各端都只剩「原对话 + 至多一份干净副本」。
     * 有聊天记录的副本一律不删（[ConflictCopyCleanup.staleCopies] 的安全闸）。
     */
    private suspend fun cleanupConflictCopies() {
        val convs = Relay.conversations()
        if (convs.none { ConflictCopyCleanup.analyze(it.title).depth > 0 }) return
        val withMessages = mutableSetOf<String>()
        for (c in convs) {
            if (ConflictCopyCleanup.analyze(c.title).depth > 0 &&
                runCatching { Relay.messages(c.id).isNotEmpty() }.getOrDefault(false)
            ) withMessages.add(c.id)
        }
        val stale = ConflictCopyCleanup.staleCopies(convs) { it.id in withMessages }
        if (stale.isEmpty()) return
        Relay.applyConversations(stale.map { Relay.ConvOp.Remove(it) })
        // Remove 落盘后 diff 会自动标脏；这里再显式补一次 CONV 脏标记兜底（幂等），
        // 保证墓碑这轮一定推出去 —— 副本只有推上墓碑，别的设备才不会再把它拉回来
        for (id in stale) {
            Adapter.markDirty(Adapter.objKey(SyncKind.CONV, id))
            Adapter.markDirty(Adapter.objKey(SyncKind.MSGS, id))
            Adapter.markDirty(Adapter.objKey(SyncKind.MEMS, id))
            Adapter.markDirty(Adapter.objKey(SyncKind.PCSET, id))
        }
        notice(LocaleManager.strings().syncConflictCopiesCleaned(stale.size))
    }

    private suspend fun pull(auth: Session.Auth, userId: String) {
        var guard = 0
        pulledConvIds.clear()
        while (guard++ < 50) {
            val res = ApiClient.changes(auth.token, cursor)
            if (res.changes.isEmpty()) {
                cursor = maxOf(cursor, res.nextSeq)
                break
            }

            // 先一趟把内容全取回来（一个请求），再统一应用 —— 一条一条取会把往返次数乘上对象数
            val wanted = res.changes.filter { !it.deleted }.map { it.kind to it.id }
            val fetched = if (wanted.isEmpty()) emptyMap()
            else ApiClient.fetchObjects(auth.token, wanted).associateBy { it.kind to it.id }

            // 这一批里出现过的对话：**先整批登记，再逐条应用**。消息那条防线要按
            // 「这条对话是不是刚同步过来的」来判断（见 applyChange 的 MSGS 分支），
            // 而 conv 和 msgs 在批里的先后顺序并不由我们决定，所以不能等应用到了才登记。
            for (m in res.changes) if (m.kind == SyncKind.CONV && !m.deleted) pulledConvIds.add(m.id)

            // The ledger must be applied before its message/memory arrays, regardless of server order.
            // 1.0.99.3：IMG 也排在 MSGS 前 —— 引用要在消息解码前落到缓存，否则引用解不到路径。
            // 1.0.99.4b：IMG **墓碑**同样前置 —— 后置会撞「同批里消息还引用着这张图」：
            // 消息先把引用解成路径，墓碑转头把缓存文件删了，留下悬空路径。
            for (meta in res.changes.sortedBy {
                when {
                    it.kind == SyncKind.CONV && !it.deleted -> 0
                    it.kind == SyncKind.IMG -> 1
                    else -> 2
                }
            }) {
                if (accountChanged(userId)) return
                applyChange(meta, fetched[meta.kind to meta.id])
            }

            cursor = maxOf(cursor, res.nextSeq)
            if (!res.hasMore) break
        }
    }

    private suspend fun applyChange(meta: ObjectMeta, obj: SyncObject?) {
        val key = Adapter.objKey(meta.kind, meta.id)

        if (meta.deleted) {
            // 本地这条还有没推上去的改动 → 不删，让下一轮把它复活。
            // （数据比删除意图重要，而且用户明明刚在这台设备上改过它。）
            if (Adapter.dirtyKeys().contains(key)) {
                synchronized(revs) { revs[key] = meta.rev }
                return
            }
            when (meta.kind) {
                SyncKind.CONV -> Relay.applyConversations(listOf(Relay.ConvOp.Remove(meta.id)))
                SyncKind.MSGS -> Relay.dropMessages(meta.id)
                SyncKind.MEMS -> Relay.removeMemoryFile(meta.id)
                SyncKind.PCSET -> Relay.removePerConv(meta.id)
                // 图片对象墓碑：别处引用计数归零后被清 —— 缓存跟着删，引用留待消息层自行收敛
                SyncKind.IMG -> runCatching { ImageSync.cacheFile(meta.id).delete() }
                else -> Unit
            }
            synchronized(revs) { revs[key] = meta.rev }
            return
        }

        val data = obj?.data ?: return

        // 1.0.99.3 图片对象：下载载荷落缓存（`img_<hash>.jpg`），消息层解引用就用它
        if (meta.kind == SyncKind.IMG) {
            val payload = data.takeIf { it.isJsonPrimitive }?.asString
                ?.let { runCatching { android.util.Base64.decode(it, android.util.Base64.DEFAULT) }.getOrNull() }
            if (payload != null) ImageSync.materialize(meta.id, payload)
            synchronized(revs) { revs[key] = meta.rev }
            return
        }

        when (meta.kind) {
            SyncKind.CONV -> applyConv(meta.id, data)
            SyncKind.MSGS -> {
                /**
                 * 本地已经删了这条对话 → 云端这份消息是旧的，别把它弄回来。
                 *
                 * 但**「本地没有这条消息」不等于「本地删过它」**，两种情况必须分开：
                 *  · 对话在本地还在（刚在这一批里拉下来的，或者更早拉下来过）——
                 *    那只是"这台设备还没收到过它的消息"，照收；
                 *  · 对话在本地也没了 —— 才是用户在这台设备上删掉了它，跳过。
                 *
                 * 原来只判「消息文件在不在」，于是**另一台设备新建的对话**同步过来
                 * 只有对话、没有消息：本地还没建消息文件 → 跳过 → 游标照常往前走 → 永久丢
                 * （消息文件是聊天页保存消息时才创建的，`applyConversations` 不建）。
                 */
                val skip = !LocalStore.messagesFile(meta.id).exists() &&
                    !Adapter.dirtyKeys().contains(key) &&
                    meta.id !in pulledConvIds &&
                    Relay.conversations().none { it.id == meta.id }
                if (skip) return
                val incoming = Wire.msgsFromWire(data, Relay.messages(meta.id))
                Relay.applyMessages(meta.id, incoming)
                if (incoming.any { it.id in com.freechat.data.MessageDeletion.deletedIds(meta.id) }) Adapter.markDirty(key)
            }
            SyncKind.MEMS -> {
                val incoming = Wire.memsFromWire(data) ?: return
                Relay.applyMemories(meta.id, incoming)
                if (com.freechat.data.MessageDeletion.memories(meta.id, incoming).size != incoming.size) Adapter.markDirty(key)
            }
            SyncKind.PCSET -> {
                val obj = data.takeIf { it.isJsonObject }?.asJsonObject ?: return
                Relay.applyPerConv(meta.id, obj)
                val incoming = PerConvBridge.mergeIntoLocal(com.freechat.model.PerConvSettings(), obj)
                if (incoming != com.freechat.data.MessageDeletion.atmosphere(meta.id, incoming)) Adapter.markDirty(key)
            }
            SyncKind.SETTINGS -> {
                val repo = settingsRepo ?: return
                val remote = data.takeIf { it.isJsonObject }?.asJsonObject ?: return
                SettingsRepository.suspendApply { SettingsBridge.applyRemote(repo, remote) }
            }
        }
        synchronized(revs) { revs[key] = meta.rev }
    }

    private suspend fun applyConv(id: String, data: JsonElement) {
        val local = Relay.conversations().find { it.id == id }
        val remote = Wire.convFromWire(data, local) ?: return
        val ops = mutableListOf<Relay.ConvOp>()

        if (local == null) {
            ops += Relay.ConvOp.Upsert(remote)
        } else {
            val merged = Merge.mergeConv(local, remote)
            ops += Relay.ConvOp.Upsert(merged.conv)
            merged.alternate?.let { alt ->
                // 两台设备各改各的角色设定，判断不了谁新谁旧 —— 两份都留。
                // 副本**不抄消息历史**（消息已经并集进原对话了），只带那套设定，几乎不占空间。
                //
                // 1.0.99.3 幂等闸门：等价副本（同标题 + 人设云端长相一致）已存在就不再造 ——
                // 原来每轮同步都造一条新的并推上云，云端 seq 递增又把这条对话拉回来再判一次
                // 冲突，副本的副本层层套娃（见 Merge.samePersona 注释）。
                val duplicate = Relay.conversations().any { existing ->
                    existing.id != merged.conv.id &&
                        existing.title == alt.title &&
                        Merge.samePersona(existing.characterProfile, alt.profile)
                }
                if (!duplicate) {
                    val copy = Conversation(
                        id = UUID.randomUUID().toString(),
                        title = alt.title,
                        mode = alt.mode,
                        characterProfile = alt.profile
                    )
                    ops += Relay.ConvOp.Upsert(copy)
                    // 副本是新造的东西，云端还没有 —— 得排进队列推上去
                    Adapter.markDirty(Adapter.objKey(SyncKind.CONV, copy.id))
                    notice(LocaleManager.strings().syncConflictCopySaved(copy.title))
                }
            }
        }

        Relay.applyConversations(ops)
        val deleted = com.freechat.data.MessageDeletion.deletedIds(id)
        if (deleted.isNotEmpty()) {
            if (!remote.deletedMessageIds.containsAll(deleted) ||
                !remote.deletedVisualMessageIds.containsAll(com.freechat.data.MessageDeletion.deletedVisualIds(id)))
                Adapter.markDirty(Adapter.objKey(SyncKind.CONV, id))
            Adapter.markDirty(Adapter.objKey(SyncKind.MSGS, id))
            Adapter.markDirty(Adapter.objKey(SyncKind.MEMS, id))
            if (Relay.perConv(id) != null) Adapter.markDirty(Adapter.objKey(SyncKind.PCSET, id))
        }
    }

    // ============================================================
    //  推
    // ============================================================

    private suspend fun push(auth: Session.Auth, userId: String) {
        var round = 0
        while (round++ < MAX_PUSH_ROUNDS) {
            val keys = Adapter.dirtyKeys()
            if (keys.isEmpty()) return
            var progressed = false
            for (key in keys) {
                if (accountChanged(userId)) return
                val sep = key.indexOf(':')
                if (sep <= 0) {
                    Adapter.unmarkDirty(key)
                    continue
                }
                val kind = key.substring(0, sep)
                val id = key.substring(sep + 1)
                if (pushOne(auth, kind, id, key)) progressed = true
            }
            if (!progressed) return
        }
    }

    /** 返回 true 表示"有进展"（推上去了 / 合并过了），false 表示这一条这轮没得救 */
    private suspend fun pushOne(auth: Session.Auth, kind: String, id: String, key: String): Boolean {
        pushingVersions[key] = Adapter.dirtyVersion(key)
        val rev = synchronized(revs) { revs[key] } ?: 0L
        return try {
            when (kind) {
                SyncKind.CONV -> {
                    val conv = Relay.conversations().find { it.id == id }
                    if (conv == null) deleteRemote(auth, kind, id, rev, key)
                    else {
                        val wire = Wire.convToWire(conv)
                        ensureImagesPushed(wire)
                        putRemote(auth, kind, id, rev, wire, Wire.convUpdatedAt(conv), key)
                    }
                }
                SyncKind.MSGS -> {
                    if (!LocalStore.messagesFile(id).exists()) deleteRemote(auth, kind, id, rev, key)
                    else {
                        val msgs = Relay.messages(id)
                        val wire = Wire.msgsToWire(msgs)
                        // 1.0.99.3 图片上云：把引用的图片对象排进队列（本轮下一段推，
                        // 收端乱序到达由 ImageSync.healReferences 自愈）
                        ensureImagesPushed(wire)
                        putRemote(auth, kind, id, rev, wire, Wire.msgsUpdatedAt(msgs), key)
                    }
                }
                // 1.0.99.3 图片对象：缓存还在就推载荷，缓存没了（引用归零被清）就立墓碑
                SyncKind.IMG -> {
                    val cache = ImageSync.cacheFile(id)
                    if (cache.isFile && cache.length() > 0) {
                        val payload = runCatching {
                            android.util.Base64.encodeToString(cache.readBytes(), android.util.Base64.NO_WRAP)
                        }.getOrNull()
                        if (payload == null) { Adapter.unmarkDirty(key); false }
                        else putRemote(auth, kind, id, rev, com.google.gson.JsonPrimitive(payload), cache.lastModified(), key)
                    } else deleteRemote(auth, kind, id, rev, key)
                }
                SyncKind.MEMS -> {
                    if (!LocalStore.memoryFile(id).exists()) deleteRemote(auth, kind, id, rev, key)
                    else {
                        val mems = Relay.memories(id)
                        putRemote(auth, kind, id, rev, Wire.memsToWire(mems), Wire.memsUpdatedAt(mems), key)
                    }
                }
                SyncKind.PCSET -> pushPerConv(auth, id, rev, key)
                SyncKind.SETTINGS -> pushSettings(auth, rev, key)
                else -> {
                    Adapter.unmarkDirty(key)
                    false
                }
            }
        } catch (e: ApiError) {
            when {
                e.isConflict -> resolveConflict(auth, kind, id, key, e)
                e.status == 413 -> {
                    // 太大或云端空间满。**别再每轮重试同一个必然失败的请求**
                    notice(LocaleManager.strings().syncObjectTooLarge(describe(kind, id)))
                    Adapter.unmarkDirty(key)
                    false
                }
                else -> throw e
            }
        }
    }

    /**
     * 推一条对话的「新规则」（只推设备无关的那半）。
     *
     * 本地这份**整条没了**就是删（对话被删了，它的新规则在云端也该立墓碑）——
     * 跟 msgs/mems 那边"文件没了就删"同一条规矩。
     */
    /**
     * 1.0.99.3 图片上云：把 wire 里引用到、还没上过云的图片对象排进待推队列。
     *
     * 只「记一笔」不直接推 —— 推送走统一的 push 循环（IMG 分支），409/413/网络
     * 都有既定处置。消息本轮先推上去、图片下一轮跟上，收端乱序由
     * [ImageSync.healReferences] 自愈（引用就地解成缓存路径）。
     */
    private fun ensureImagesPushed(wire: com.google.gson.JsonElement) {
        val refs = mutableListOf<String>()
        collectRefs(wire, refs)
        for (hash in ImageSync.hashesIn(refs)) {
            if (ImageSync.cacheFile(hash).isFile) Adapter.markDirty(Adapter.objKey(SyncKind.IMG, hash))
        }
    }

    private fun collectRefs(el: com.google.gson.JsonElement, out: MutableList<String>) {
        when {
            el.isJsonPrimitive -> if (el.asString.startsWith(ImageSync.REF_PREFIX)) out += el.asString
            el.isJsonArray -> el.asJsonArray.forEach { collectRefs(it, out) }
            el.isJsonObject -> el.asJsonObject.entrySet().forEach { collectRefs(it.value, out) }
        }
    }

    private suspend fun pushPerConv(auth: Session.Auth, id: String, rev: Long, key: String): Boolean {
        val local = Relay.perConv(id) ?: return deleteRemote(auth, SyncKind.PCSET, id, rev, key)
        val ours = PerConvBridge.toWire(com.freechat.data.MessageDeletion.atmosphere(id, local))
        // 云端可能还有我们不认识的键（网页端以后加的）。PUT 是整对象替换，
        // 只推自己这几个键就会把它们抹掉，然后两端来回抹 —— 跟设置那边同一个坑（见 pushSettings）
        val payload = if (rev == 0L) ours else {
            val remote = fetchOne(auth, SyncKind.PCSET, id)?.data?.takeIf { it.isJsonObject }?.asJsonObject
            PerConvBridge.unionForPush(remote, ours)
        }
        return putRemote(auth, SyncKind.PCSET, id, rev, payload, System.currentTimeMillis(), key)
    }

    private suspend fun pushSettings(auth: Session.Auth, rev: Long, key: String): Boolean {
        val repo = settingsRepo ?: return false
        // 先记下"这一份要替云端做主的本地键"。推成功之前，任何一轮 pull 都不许用云端旧值
        // 覆盖它们 —— 否则就是那个「开关自己弹回去、要点第二次」的毛病（见 SettingsRepository.edit）
        val pushed = SettingsRepository.pendingLocalSnapshot()
        val ours = SettingsBridge.snapshot(repo)
        // 必须并上云端那份再推：PUT 是整对象替换，只推自己那几个键会把
        // 网页端那些安卓不认识的键（sidebarWidth 之类）从云端抹掉，
        // 然后两边就会永远互相抹、永远收敛不了。
        val remote = fetchOne(auth, SyncKind.SETTINGS, SyncKind.SETTINGS_ID)?.data
            ?.takeIf { it.isJsonObject }?.asJsonObject
        val payload = SettingsBridge.unionForPush(remote, ours)
        val ok = putRemote(auth, SyncKind.SETTINGS, SyncKind.SETTINGS_ID, rev, payload, System.currentTimeMillis(), key)
        // 推成功了才放行：从这一刻起远端那份才是"我们的"，后面拉回来的不会再是旧值
        if (ok) SettingsRepository.releasePendingLocal(pushed)
        return ok
    }

    private suspend fun putRemote(
        auth: Session.Auth,
        kind: String,
        id: String,
        rev: Long,
        payload: JsonElement,
        updatedAt: Long,
        key: String
    ): Boolean {
        val r = ApiClient.putObject(auth.token, kind, id, rev, updatedAt, payload)
        synchronized(revs) { revs[key] = r.rev }
        // 推成功了才算数。内容没变的话服务端原样返回、不顶版本号，一样是成功
        if (!r.deleted) Adapter.unmarkDirty(key, pushingVersions[key])
        return true
    }

    private suspend fun deleteRemote(auth: Session.Auth, kind: String, id: String, rev: Long, key: String): Boolean {
        if (rev == 0L) {
            // 云端压根没有过这一条 —— 没什么可删的，销账走人
            Adapter.unmarkDirty(key, pushingVersions[key])
            return true
        }
        val r = try {
            ApiClient.deleteObject(auth.token, kind, id, rev)
        } catch (e: ApiError) {
            // 云端说「这个对象不存在」—— 我们要的就是这个结果，销账走人。
            // 不能让它冒出去：一轮同步里任何一个对象抛错都会让整轮变成「同步错误」，
            // 而这里其实什么都没出错（可能只是之前那一列的墓碑被清过、或者本地那份从没上传成功过）
            if (e.status == 404 && e.code == "not_found") {
                Adapter.unmarkDirty(key, pushingVersions[key])
                return true
            }
            throw e
        }
        synchronized(revs) { revs[key] = r.rev }
        Adapter.unmarkDirty(key, pushingVersions[key])
        return true
    }

    /**
     * 版本号对不上 —— 说明另一台设备动过同一个对象。
     *
     * 处理方式是**就地合并**，然后把这一条继续留在待推队列里，下一轮带着新版本号重推。
     * 不需要额外的"合并后要不要推"判断：真的一字不差时服务端自己会认出来（字节比对后
     * 不顶版本号），多花一次往返而已。
     */
    private suspend fun resolveConflict(
        auth: Session.Auth,
        kind: String,
        id: String,
        key: String,
        e: ApiError
    ): Boolean {
        val remoteRev = e.current?.rev
        val deleted = e.current?.deleted == true

        if (deleted || remoteRev == null) {
            /**
             * 云端那份刚好被删了 —— 拿墓碑版本号重推一次就是**复活**。
             *
             * 复活是故意的（数据比删除意图重要），但不能不吭声：用户在另一台设备上
             * 删掉了这条对话，这边一改它又回来了，不说一声会以为是同步出了毛病。
             */
            synchronized(revs) { revs[key] = e.current?.rev ?: 0L }
            notice(LocaleManager.strings().syncResurrected(describe(kind, id)))
            return true
        }

        val remote = fetchOne(auth, kind, id) ?: run {
            Adapter.unmarkDirty(key)
            return false
        }
        if (remote.deleted) {
            synchronized(revs) { revs[key] = remoteRev }
            return true
        }

        val data = remote.data
        when (kind) {
            SyncKind.CONV -> if (data != null) applyConv(id, data)
            SyncKind.MSGS -> if (data != null) {
                Relay.applyMessages(id, Wire.msgsFromWire(data, Relay.messages(id)))
            }
            SyncKind.MEMS -> if (data != null) {
                Relay.applyMemories(id, Wire.memsFromWire(data) ?: return false)
            }
            SyncKind.PCSET -> if (data != null) {
                data.takeIf { it.isJsonObject }?.asJsonObject?.let { Relay.applyPerConv(id, it) }
            }
            SyncKind.SETTINGS -> {
                val repo = settingsRepo
                val obj = data?.takeIf { it.isJsonObject }?.asJsonObject
                if (repo != null && obj != null) {
                    SettingsRepository.suspendApply { SettingsBridge.applyRemote(repo, obj) }
                }
            }
        }
        synchronized(revs) { revs[key] = remoteRev }
        // 脏标记**留着**：合并的结果可能和云端不一样，下一轮带着新版本号再推一遍
        return true
    }

    private suspend fun fetchOne(auth: Session.Auth, kind: String, id: String): SyncObject? =
        ApiClient.fetchObjects(auth.token, listOf(kind to id)).firstOrNull()

    // ============================================================
    //  提示
    // ============================================================

    private suspend fun describe(kind: String, id: String): String {
        val conv = runCatching { Relay.conversations().find { it.id == id }?.title }.getOrNull()
        if (!conv.isNullOrBlank()) return conv
        val s = LocaleManager.strings()
        return when (kind) {
            SyncKind.CONV -> s.syncKindConv
            SyncKind.MSGS -> s.syncKindMsgs
            SyncKind.MEMS -> s.syncKindMems
            // 「新规则」是一条对话的一部分，用户面前不必出现 pcset 这个词
            SyncKind.PCSET -> s.syncKindConv
            SyncKind.SETTINGS -> s.syncKindSettings
            else -> id
        }
    }

    private fun notice(text: String) {
        _status.value = _status.value.copy(notices = (_status.value.notices + text).takeLast(8))
    }

    fun clearNotices() {
        _status.value = _status.value.copy(notices = emptyList())
    }

    /**
     * 首次登录：把这台设备上已有的东西**整个**推一遍。
     *
     * 不推的话，用户登录后会觉得"我的聊天记录不见了" —— 云端是空的，
     * 而本地那些东西从来没进过待推队列。队列只记「改动」，不记「存量」。
     */
    suspend fun seedEverything() {
        val convs = Relay.conversations()
        Adapter.markLocalEverything(convs)
    }

    /**
     * 补推存量：本机已有的角色头像 + 每对话新规则（每个账号只做一次，见 [Session.SyncState.seededExtras]）。
     *
     * **先排好再立标记**：万一排的过程出错，下次启动还能再来一遍 —— 重推是幂等的
     * （服务端认得出内容没变，不顶版本号，也就不会让别的设备白拉一趟）。
     */
    suspend fun seedExtras() {
        Adapter.markExtras(Relay.conversations())
        Session.update({ it.copy(seededExtras = true) })
    }

    /**
     * 补齐**云端缺件**（2026-10-01 加）：本地有、云里从没有过的东西才推。
     *
     * 背景（用户报「云端内容少了好多」查出来的洞）：队列只记**改动**，不记存量 ——
     * 老账号（游标不是 0）登录只走 [seedExtras]（头像+新规则），**从没推过的
     * 聊天记录/记忆会一直留在本地**，要等用户哪天恰好再动那条对话才轮得到。
     * 线上实测：有账号 51 条对话只有 3 个记录箱在云里，缺的就是这些「从没动过的」。
     *
     * 口径：拿云端**全部**对象清单做差（含墓碑 —— `changes` since=0 翻页拉全）：
     *  · 云里活着的 —— 不碰（以后的改动走正常队列）
     *  · 云里是**墓碑**的 —— **绝不补推**（别的设备删过的东西就该被拉回来删掉，
     *    推上去就成了「删除复活」，破坏跨设备删除）
     *  · 云里压根没有的 —— 排进队列补推（只追加，不清空用户正攒着的改动）
     *
     * 每个账号每台设备做一次（[Session.SyncState.seededMissing]）；**失败不立标记**，
     * 下次登录接着试。清单翻不完（超大账号撞上页数上限）也不立 —— 宁可重来，
     * 也不能拿半份清单当「云里没有」去推（那是给云端制造无谓的 409）。
     */
    suspend fun seedMissing() {
        val auth = Session.loadAuth() ?: return
        try {
            val known = mutableSetOf<String>()
            var since = 0L
            var pages = 0
            while (pages < MAX_SEED_PAGES) {
                val r = ApiClient.changes(auth.token, since, 200)
                for (c in r.changes) known.add(Adapter.objKey(c.kind, c.id)) // 墓碑也算「云里有过」
                if (!r.hasMore) break
                since = r.nextSeq
                pages++
            }
            if (pages >= MAX_SEED_PAGES) {
                Log.w(TAG, "seedMissing: cloud list too long, skip this round")
                return
            }
            Adapter.markLocalMissing(Relay.conversations(), known)
            Session.update({ it.copy(seededMissing = true) })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // 补缺是「下次还能再来」的锦上添花，绝不能把登录流程带崩 —— 不立标记，下回接着试
            Log.w(TAG, "seedMissing failed, will retry next login", e)
        }
    }

    /**
     * 退出登录 / 注销账号。
     *
     * [wipeProgress] 为真时连同步进度一起清掉（注销账号用，别留一个
     * 「账号已经不在了、游标还在」的残骸）。普通登出**不清** —— 换回来时还要接着用。
     */
    fun signOut(wipeProgress: Boolean = false) {
        Session.clearAuth()
        if (wipeProgress) Session.clearSyncState()
        _status.value = _status.value.copy(phase = Phase.OFF, error = null, lastSyncAt = 0)
    }

    private fun updatePending() {
        _status.value = _status.value.copy(pending = Adapter.pendingCount())
    }

    /** 让界面上那个「设置」也走一遍脏标记（改了同步开关之类的时候用） */
    fun markSettingsDirty() {
        Adapter.markDirty(Adapter.objKey(SyncKind.SETTINGS, SyncKind.SETTINGS_ID))
    }

    /** 给界面用：读一次设置快照 */
    fun settingsRepository(): SettingsRepository? = settingsRepo
}
