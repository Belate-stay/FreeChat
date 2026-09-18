package com.freechat.data

import com.freechat.model.PerConvSettings
import com.google.gson.reflect.TypeToken

/**
 * 每对话「新规则」覆盖设置的文件层：`freechat_perconv.json`，一个 map（convId → 覆盖项）。
 *
 * 为什么值得单独一层：这份数据原来只有 `ChatViewModel` 一个使用方（内存那份是真相，改完整份写回），
 * 现在**同步引擎也要读写它**了（见 `sync/PerConvBridge`），两个入口各写各的格式早晚分叉。
 * 而且它的「整份 map」形态决定了删除一条对话时不能只删文件 —— 得把 map 里那个键摘掉，
 * 这种读-改-写必须在锁里做（同步的合并正好可能同时插进来）。
 *
 * 字段语义没变：**null = 这条对话没定制这一项、跟随全局**。
 */
object PerConvStore {

    private val type = object : TypeToken<Map<String, PerConvSettings>>() {}.type

    /** 读整份。文件不在/解析不出来就是空 map（**不是** null —— 调用方一律当"没有定制"处理） */
    fun load(): Map<String, PerConvSettings> = runCatching {
        LocalStore.readText(LocalStore.perConvFile())
            ?.let { AppJson.gson.fromJson<Map<String, PerConvSettings>>(it, type) }
    }.getOrNull() ?: emptyMap()

    /** 整份覆盖写。`ChatViewModel` 用它 —— 它内存里那份就是当前完整状态 */
    fun save(map: Map<String, PerConvSettings>) {
        LocalStore.writeText(LocalStore.perConvFile(), AppJson.gson.toJson(map, type))
    }

    /** 只改一条（读-改-写整段在锁里）。没有 ViewModel 时同步引擎走这条路 */
    fun put(convId: String, settings: PerConvSettings) {
        LocalStore.locked { save(load() + (convId to settings)) }
    }

    /** 删一条（键不存在就什么都不做 —— 别为一次空删除重写整个文件） */
    fun remove(convId: String) {
        LocalStore.locked {
            val m = load()
            if (m.containsKey(convId)) save(m - convId)
        }
    }
}
