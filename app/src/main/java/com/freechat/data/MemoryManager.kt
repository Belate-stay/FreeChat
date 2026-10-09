package com.freechat.data

import android.util.Log
import com.freechat.core.MemoryLogic
import com.freechat.model.MemoryEntry
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * 对话记忆系统 —— 「硬盘 + 内存」分层：
 *
 * - 硬盘（记忆文件）：长期记忆，分两层
 *   - 主线/重大事件（kind="plot"）：剧情走向、关系转变、关键承诺，长期不淘汰、检索时始终注入
 *   - 细节（kind="detail"）：普通闲聊细节，可滚动淘汰，按关键词命中才注入
 * - 内存（短期上下文）：当前对话的近期原始消息，由 ViewModel 直接喂给 LLM
 *
 * 「增强检索」（highQuality）打开后换成另一套策略：不筛关键词、主线全量 + 最近细节按时间线整块注入、
 * 写入前相似度去重（同一件事改写旧条目不堆新条目）。
 *
 * **本类现在只是存储适配器**（1.0.91 M1 第二刀）：读写走 LocalStore（进程级锁/原子写/写监听），
 * 全部检索与合并策略在 [MemoryLogic]（freechat-core，与服务器 JVM 共用同一份）。
 * 例外说明见各方法注释。
 */
class MemoryManager {

    private val gson = AppJson.gson

    // 文件位置与读写一律走 LocalStore：它持进程级锁、写盘是原子的，
    // 而且写完会通知同步引擎「这个对话的记忆变了」。这里自己 File.writeText
    // 就等于绕开了那把锁 —— 同步把云端记忆落盘的同时，这一轮总结正在写同一个文件。
    private fun memoryFile(convId: String): File = LocalStore.memoryFile(convId)

    /** 读取某对话的全部记忆（读出来一律先过 [healed] 补全老档案，见那里的说明） */
    fun load(convId: String): List<MemoryEntry> = if (ConversationDeletion.contains(convId)) emptyList() else try {
        val text = LocalStore.readText(memoryFile(convId))
        if (text != null) {
            val type = object : TypeToken<List<MemoryEntry>>() {}.type
            MessageDeletion.memories(convId, gson.fromJson<List<MemoryEntry>>(text, type)?.map { it.healed() }.orEmpty())
        } else emptyList()
    } catch (e: Exception) {
        Log.e("MemoryManager", "Failed to load memory", e)
        emptyList()
    }

    /**
     * 追加一条记忆。与已有条目高度相似时不新增，而是**改写那一条**（合并策略见 [MemoryLogic.upsert]）。
     * [highQuality] 为真时不做 30 条的激进淘汰。
     */
    fun append(convId: String, entry: MemoryEntry, highQuality: Boolean = false) {
        try {
            // 整段读-改-写包在锁里：中间被云端同步插进来一条记忆，这份结果就会把它盖掉。
            // 锁可重入，里面那些 readText/writeText 自己再拿一次没问题。
            LocalStore.locked {
                if (ConversationDeletion.contains(convId)) return@locked
                if (MessageDeletion.memories(convId, listOf(entry)).isEmpty()) return@locked
                val list = MemoryLogic.upsert(load(convId), entry)
                LocalStore.writeText(memoryFile(convId), gson.toJson(MemoryLogic.trim(list, highQuality)))
            }
        } catch (e: Exception) {
            Log.e("MemoryManager", "Failed to save memory", e)
        }
    }

    /** 删除对话记忆 */
    fun delete(convId: String) {
        LocalStore.deleteFile(memoryFile(convId))
    }

    /** Rewrite even when empty: cloud must receive the removal, not merge a missing summary back. */
    fun purgeDeleted(convId: String) = LocalStore.locked {
        LocalStore.writeText(memoryFile(convId), gson.toJson(load(convId)))
    }

    /**
     * 删除 [sinceMs] 之后写入的记忆条目，返回删掉几条。
     *
     * 用途：叙事单发模式下改写最后一条提示词重发时，那一轮（旧提示词 + 旧回复）整轮作废，
     * 由它总结出来的记忆也必须一并撤掉——否则用户明明把那句话改掉了，AI 后面还是会
     * 记得「他当时说过 XXX」，就是「旧提示词被删除且不计入记忆」没做到的典型表现。
     *
     * 判定依据是**写入时间**（[MemoryEntry.timestamp]，即总结完成落盘的时刻）：
     * 一轮回复的记忆一定在该轮用户消息之后才写盘，所以「晚于这条用户消息」的条目就属于
     * 这一轮及之后。理论上的边界情况是上一轮的总结异步还没跑完、用户已经发了下一条——
     * 此时上一轮的记忆会被顺带删掉。叙事单发模式下两条消息之间必然隔着一整轮回复，
     * 撞上这个窗口的概率极低，且删掉的也只是上一轮的一条摘要，可以接受。
     */
    fun removeSince(convId: String, sinceMs: Long): Int {
        return try {
            LocalStore.locked {
                val list = load(convId)
                val kept = list.filter { it.timestamp < sinceMs }
                val removed = list.size - kept.size
                if (removed > 0) LocalStore.writeText(memoryFile(convId), gson.toJson(kept))
                removed
            }
        } catch (e: Exception) {
            Log.e("MemoryManager", "Failed to remove recent memory", e)
            0
        }
    }

    /** 构建用于 API 的记忆系统提示词片段（记忆是「增强」不是「必需」：这里出任何岔子都只等于本轮没有记忆） */
    fun buildMemoryContext(convId: String, highQuality: Boolean = false): String = try {
        ModelManagedMemory.buildContext(load(convId), highQuality)
    } catch (e: Exception) {
        // 兜底：这段代码曾经把整个拟人模式拖垮过 —— 老档案里 eventDate 为 null 时 NPE，
        // 异常在拼请求之前抛出，用户看到的是「请求失败」，其实请求根本没发出去。
        // 记忆注入失败该退化成「这一轮没有记忆」，绝不能连回复一起拖死。
        Log.e("MemoryManager", "buildMemoryContext failed，本轮跳过记忆注入", e)
        ""
    }
}
