package com.freechat.sync

import com.freechat.data.AppJson
import com.freechat.data.LocalStore
import com.freechat.data.PerConvStore
import com.freechat.data.healed
import com.freechat.model.Conversation
import com.freechat.model.MemoryEntry
import com.freechat.model.Message
import com.freechat.model.PerConvSettings
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken

/**
 * 同步引擎读本地数据、把云端结果落回本地的**唯一通道**。
 *
 * ## 为什么不直接读写磁盘
 *
 * 因为磁盘不是唯一真相。`ChatViewModel` 把**全部对话**放在内存的
 * `_conversations` 里，改完立刻整份覆盖写回 `freechat_conversations.json`；
 * 当前对话的消息也有一份在 `_messages` 里。如果同步引擎绕开它直接改盘，
 * 内存里那份旧的会在下一次改动时把同步刚写的东西整个盖掉 ——
 * 而且是静默的：用户看到的是"同步完成"，然后改动凭空消失。
 *
 * ## 交出去的是一批操作，不是一份结果
 *
 * 注意 [Impl.applyConversations] 收的是 [ConvOp] 列表而不是"新的对话数组"。
 * 这是关键：引擎在 IO 线程上算合并用的是**读盘那一刻**的本地数据，
 * 而用户完全可能在这中间又建了一条对话。要是让引擎直接覆盖整个数组，
 * 那条新对话就没了。所以引擎只交出「要把哪几条改成什么样」，
 * 由持有内存的那一方在**自己的线程上、对着自己那一刻的数据**去应用。
 *
 * 消息同理：落到本地时再并一次集，中途新发的那条自然就被保住了。
 *
 * ## 没有 ViewModel 的时候
 *
 * `ChatViewModel` 是 Compose 的 `viewModel()` 按需创建的，而同步可能在它存在之前
 * 就跑（比如 App 刚起来、界面还没组）。那时内存里没有第二份真相，直接读盘写盘
 * 就是对的 —— 那份兜底实现见下面几个 `…FromDisk`。
 */
object Relay {

    /** 对对话数组的一次改动 */
    sealed class ConvOp {
        data class Upsert(val conv: Conversation) : ConvOp()
        data class Remove(val id: String) : ConvOp()
    }

    interface Impl {
        /** 内存里那份对话数组（通常是 `ChatViewModel._conversations`） */
        suspend fun conversations(): List<Conversation>

        /** 把这批改动应用到内存 + 磁盘上 */
        suspend fun applyConversations(ops: List<ConvOp>)

        /** 某对话的消息；返回 null 表示"这个我管不着，你读盘吧" */
        suspend fun messages(convId: String): List<Message>?

        /** 把云端来的消息并进本地（**本地那份也要参与并集**，见类注释） */
        suspend fun applyMessages(convId: String, incoming: List<Message>)

        /**
         * 云端把这条对话的消息删了（墓碑）——本地也要清掉。
         *
         * 单独一个动作而不是"并进一个空列表"：并集跟空列表并等于没变，
         * 而这里要的是**删**。两件事形状像、语义反。
         */
        suspend fun dropMessages(convId: String)

        /** 这条对话的「新规则」；null = 压根没有定制记录（跟"定制了但全是跟随全局"不是一回事，见 PerConvBridge） */
        suspend fun perConv(convId: String): PerConvSettings?

        /** 云端那份覆盖进来。**只覆盖参与同步的那半**，模型 id 那半保持本地不变 */
        suspend fun applyPerConv(convId: String, incoming: JsonObject)

        /** 云端把这条对话的新规则删了（墓碑）——本地那条记录也摘掉 */
        suspend fun removePerConv(convId: String)
    }

    @Volatile
    private var impl: Impl? = null

    fun install(i: Impl) {
        impl = i
    }

    /** 只在还是自己的时候摘 —— ViewModel 重建（比如配置变更）时旧的不该把新的摘掉 */
    fun uninstall(i: Impl) {
        if (impl === i) impl = null
    }

    private val convListType = object : TypeToken<List<Conversation>>() {}.type
    private val msgListType = object : TypeToken<List<Message>>() {}.type
    private val memListType = object : TypeToken<List<MemoryEntry>>() {}.type

    // ============================================================
    //  读
    // ============================================================

    suspend fun conversations(): List<Conversation> =
        impl?.conversations() ?: conversationsFromDisk()

    fun conversationsFromDisk(): List<Conversation> = runCatching {
        AppJson.gson.fromJson<List<Conversation>>(
            LocalStore.readText(LocalStore.conversationsFile()) ?: "[]", convListType
        )
    }.getOrNull()?.map { it.healed() } ?: emptyList()

    suspend fun messages(convId: String): List<Message> =
        impl?.messages(convId) ?: messagesFromDisk(convId)

    fun messagesFromDisk(convId: String): List<Message> = runCatching {
        LocalStore.readText(LocalStore.messagesFile(convId))?.let {
            AppJson.gson.fromJson<List<Message>>(it, msgListType)
        }
    }.getOrNull()?.map { it.healed() } ?: emptyList()

    /**
     * 记忆一律走磁盘：`MemoryManager` 没有内存缓存，每次都是现读现写，
     * 所以这里没有"两份真相"的问题。整段包在锁里 —— 合并是基于当前内容算的，
     * 中途被 `MemoryManager.append` 插进来一条，合并结果就会把它盖掉。
     */
    fun memories(convId: String): List<MemoryEntry> = LocalStore.locked {
        runCatching {
            LocalStore.readText(LocalStore.memoryFile(convId))?.let {
                AppJson.gson.fromJson<List<MemoryEntry>>(it, memListType)
            }
        }.getOrNull()?.map { it.healed() } ?: emptyList()
    }

    // ============================================================
    //  写（都是"并进去"，不是"覆盖成"）
    // ============================================================

    suspend fun applyConversations(ops: List<ConvOp>) {
        val i = impl
        if (i != null) {
            i.applyConversations(ops)
            return
        }
        LocalStore.suspendApply {
            LocalStore.locked {
                val list = conversationsFromDisk().toMutableList()
                for (op in ops) when (op) {
                    is ConvOp.Upsert -> {
                        val idx = list.indexOfFirst { it.id == op.conv.id }
                        if (idx >= 0) list[idx] = op.conv else list.add(op.conv)
                    }
                    is ConvOp.Remove -> list.removeAll { it.id == op.id }
                }
                LocalStore.writeText(
                    LocalStore.conversationsFile(),
                    AppJson.gson.toJson(list, convListType)
                )
            }
        }
    }

    suspend fun applyMessages(convId: String, incoming: List<Message>) {
        val i = impl
        if (i != null) {
            i.applyMessages(convId, incoming)
            return
        }
        LocalStore.suspendApply {
            LocalStore.locked {
                val merged = Merge.mergeMessages(messagesFromDisk(convId), incoming)
                LocalStore.writeText(
                    LocalStore.messagesFile(convId),
                    AppJson.gson.toJson(merged, msgListType)
                )
            }
        }
    }

    /** 云端那条是墓碑 —— 本地这份也删掉 */
    suspend fun dropMessages(convId: String) {
        val i = impl
        if (i != null) {
            // 走 VM 的路子：它会顺手把内存里那份也清掉，免得界面还挂着一屏旧消息
            i.dropMessages(convId)
            return
        }
        LocalStore.suspendApply { LocalStore.deleteFile(LocalStore.messagesFile(convId)) }
    }

    suspend fun applyMemories(convId: String, incoming: List<MemoryEntry>) {
        LocalStore.suspendApply {
            LocalStore.locked {
                val merged = Merge.mergeMemories(memories(convId), incoming)
                LocalStore.writeText(
                    LocalStore.memoryFile(convId),
                    AppJson.gson.toJson(merged, memListType)
                )
            }
        }
    }

    suspend fun removeMemoryFile(convId: String) {
        LocalStore.suspendApply { LocalStore.deleteFile(LocalStore.memoryFile(convId)) }
    }

    // ============================================================
    //  每对话「新规则」（只同步设备无关的那半，见 PerConvBridge）
    // ============================================================

    /**
     * 内存里那份由 `ChatViewModel` 持有（`_perConvSettings`），所以照样得从它过一道 ——
     * 绕开它直接改盘，下一次用户在本机改任何一项时就会整份覆盖写回，把同步刚放进来的行弄丢。
     */
    suspend fun perConv(convId: String): PerConvSettings? =
        impl?.perConv(convId) ?: PerConvStore.load()[convId]

    suspend fun applyPerConv(convId: String, incoming: JsonObject) {
        val i = impl
        if (i != null) {
            i.applyPerConv(convId, incoming)
            return
        }
        LocalStore.suspendApply {
            LocalStore.locked {
                val cur = PerConvStore.load()[convId] ?: PerConvSettings()
                PerConvStore.put(convId, PerConvBridge.mergeIntoLocal(cur, incoming))
            }
        }
    }

    suspend fun removePerConv(convId: String) {
        val i = impl
        if (i != null) {
            i.removePerConv(convId)
            return
        }
        LocalStore.suspendApply { LocalStore.locked { PerConvStore.remove(convId) } }
    }
}
