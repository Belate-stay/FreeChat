package com.freechat.data

import android.util.Log
import com.freechat.model.MemoryEntry
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * 对话记忆系统 —— 「硬盘 + 内存」分层：
 *
 * - 硬盘（记忆文件）：长期记忆，分两层
 *   - 主线/重大事件（kind="plot" / importance）：剧情走向、关系转变、关键承诺，长期不淘汰、检索时始终注入
 *   - 细节（kind="detail"）：普通闲聊细节，可滚动淘汰，按关键词命中才注入
 * - 内存（短期上下文）：当前对话的近期原始消息，由 ViewModel 直接喂给 LLM（callCompanionApi 的 takeLast(30)）
 */
class MemoryManager(private val filesDir: File) {

    private val gson = Gson()

    private fun memoryFile(convId: String): File =
        File(filesDir, "freechat_memory_$convId.json")

    /** 读取某对话的全部记忆 */
    fun load(convId: String): List<MemoryEntry> = try {
        val f = memoryFile(convId)
        if (f.exists()) {
            val type = object : TypeToken<List<MemoryEntry>>() {}.type
            gson.fromJson(f.readText(), type) ?: emptyList()
        } else emptyList()
    } catch (e: Exception) {
        Log.e("MemoryManager", "Failed to load memory", e)
        emptyList()
    }

    /** 追加一条记忆（主线记忆不随数量上限淘汰） */
    fun append(convId: String, entry: MemoryEntry) {
        try {
            val list = load(convId).toMutableList()
            list.add(entry)
            memoryFile(convId).writeText(gson.toJson(trim(list)))
        } catch (e: Exception) {
            Log.e("MemoryManager", "Failed to save memory", e)
        }
    }

    /** 删除对话记忆 */
    fun delete(convId: String) {
        try { memoryFile(convId).delete() } catch (_: Exception) {}
    }

    /**
     * 根据用户输入搜索相关记忆，摘要拼接为一段上下文。
     * 硬盘层（主线/重大事件）始终注入（最新 8 条）；细节层按关键词命中注入。
     */
    fun searchRelevant(convId: String, userInput: String, maxResults: Int = 5): String {
        val all = load(convId)
        if (all.isEmpty()) return ""

        val inputLower = userInput.lowercase()
        // 硬盘（主线/重大事件）：永远不忘，取最新 8 条
        val plot = all.filter { it.isPlot() }.takeLast(8)
        // 细节：关键词命中才注入
        val scoredDetail = all.filter { !it.isPlot() }.map { entry ->
            val matchCount = entry.keywords.count { kw -> inputLower.contains(kw.lowercase()) }
            entry to matchCount
        }.filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(maxResults)
            .map { it.first }

        val combined = (plot + scoredDetail).distinctBy { it.id }
        if (combined.isEmpty()) return ""
        return combined.joinToString("\n") { "- ${it.summary}" }
    }

    /** 构建用于 API 的记忆系统提示词片段 */
    fun buildMemoryContext(convId: String, userInput: String, highQuality: Boolean = false): String {
        val memories = searchRelevant(convId, userInput)
        if (memories.isEmpty()) return ""
        return if (highQuality) {
            "【你对这个用户已知的信息——以下是用户的原话摘录，要精准理解，不要脑补或篡改】\n$memories"
        } else {
            "你对这个用户已知的信息：\n$memories"
        }
    }

    /** 最多保留 30 条；硬盘（主线）优先保留（最多 20 条），细节占用剩余配额 */
    private fun trim(list: List<MemoryEntry>): List<MemoryEntry> {
        if (list.size <= 30) return list
        val plot = list.filter { it.isPlot() }.takeLast(20)
        val detailQuota = (30 - plot.size).coerceAtLeast(0)
        val detail = list.filter { !it.isPlot() }.takeLast(detailQuota)
        return (detail + plot).sortedBy { it.timestamp }
    }
}
