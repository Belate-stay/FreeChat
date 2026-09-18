package com.freechat.data

import com.freechat.model.CharacterProfile
import com.freechat.model.ChatMode
import com.freechat.model.Conversation
import com.freechat.model.MemoryEntry
import com.freechat.model.Message
import com.freechat.model.Role
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.InstanceCreator
import java.util.UUID

/**
 * 全项目共用的 Gson 实例。
 *
 * **为什么必须共用而不是各建各的** —— 这三个 `InstanceCreator` 是承重的：
 *
 * Gson 是**反射**造对象的，不走 Kotlin 构造器。一个 `String` 字段在 JSON 里缺键时，
 * 反射给它留 **null**，而 Kotlin 的非空声明**拦不住反射赋值** —— 于是对象造出来了、
 * 表面上类型正确，一碰就 NPE。这个坑这个项目已经踩过一次：
 * `MemoryEntry.eventDate` 是 1.0.30 才加的字段，老记忆文件里没有这个键，
 * 结果 `eventDate.ifBlank { … }` 在拼请求之前当场 NPE，被上层当成「请求失败」显示给用户，
 * 而请求根本没发出去（详见下面 [healed] 的注释）。
 *
 * 注册了 `InstanceCreator` 之后，Gson 会**先用它造一个带默认值的实例，再把 JSON 里
 * 存在的键盖上去** —— 缺的键就保持字段声明处的默认值，这正是我们要的语义。
 *
 * 这件事对**云端同步**比对本地读盘更要紧：本地文件是自己写的，字段总是齐的；
 * 而远端那份可能是**另一个平台**写的 —— 网页端的角色档案里就没有
 * `supportingCast` / `worldRules` / `userPersona` / `aiCreativity` / `proactiveEnabled` /
 * `referencePrototype` 这几个键（1.0.34 才加在安卓端）。拿一个不带 InstanceCreator 的
 * Gson 去解网页推上来的对话，这些字段全是 null，用户下一次开口就是崩溃。
 *
 * **所以：任何来路不明的 JSON（远端、老档案）都必须用这个实例解析。**
 */
object AppJson {
    val gson: Gson = GsonBuilder()
        .registerTypeAdapter(Message::class.java, InstanceCreator<Message> { _ ->
            Message(role = Role.USER, content = "")
        })
        .registerTypeAdapter(Conversation::class.java, InstanceCreator<Conversation> { _ ->
            Conversation()
        })
        .registerTypeAdapter(CharacterProfile::class.java, InstanceCreator<CharacterProfile> { _ ->
            CharacterProfile()
        })
        .create()
}

// ============================================================
//  兜底补全
//
//  InstanceCreator 挡的是「JSON 里根本没这个键」；下面这几个挡的是「键在、值是 null」
//  —— 网页端把 characterProfile 序列化成 null 是完全可能的，两件事都要挡。
//
//  判据写成 `x == null` 看着像废话（Kotlin 里它非空），但那正是问题所在：
//  **反射赋值绕过了类型系统**，运行时它真的可能是 null。
//
//  放在顶层而不是塞进 object：Kotlin 不允许导入 object 里的成员扩展函数，
//  而这三条要跨文件用（Wire、MemoryManager、同步引擎）。
// ============================================================

fun Conversation.healed(): Conversation {
    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
    val safe = if (id != null && title != null && mode != null) this
    else copy(
        id = id ?: "",
        title = title ?: com.freechat.i18n.LocaleManager.strings().newChat,
        mode = mode ?: ChatMode.STANDARD
    )
    // 老档案迁移照旧走一遍：远端那份可能是别人用旧版本写的
    return safe.copy(characterProfile = safe.characterProfile?.normalized())
}

fun Message.healed(): Message {
    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
    return if (
        id != null && role != null && content != null && mode != null &&
        reasoningContent != null && imagePaths != null && imageUrls != null
    ) this
    else copy(
        id = id ?: "",
        role = role ?: Role.USER,
        content = content ?: "",
        mode = mode ?: ChatMode.STANDARD,
        reasoningContent = reasoningContent ?: "",
        imagePaths = imagePaths ?: emptyList(),
        imageUrls = imageUrls ?: emptyList()
    )
}

/**
 * 记忆条目的补全 —— 修复「拟人模式所有请求都失败」的那个 bug。
 *
 * [MemoryEntry.eventDate] 是 1.0.30 才加的字段，而 1.0.29 及以前写下的记忆文件里没有这个键：
 * Gson 反射造对象（不调构造器），**缺的键就留成 null**，于是 `eventDate.ifBlank { … }`
 * 在拼请求之前当场 NPE，被上层当成「请求失败」显示给用户 —— 请求根本没发出去，
 * 模型/Key/网络全是好的。因为每一轮回复都要注入记忆，那个对话就会条条都失败；
 * 同一个 null 还会让写入路径里的 `copy(eventDate = null)` 抛异常，记忆从此再也写不进去。
 *
 * 读盘时补成默认值即根治：注入与写入都拿到干净数据，下次写盘落回文件、档案也随之治愈。
 *
 * 这段逻辑原本是 `MemoryManager` 的私有扩展，只修**本地老档案**；云端同步让同一类 JSON
 * 多了一个来源（另一台设备写的记忆）。提到顶层共用 —— 两份实现早晚会分叉，
 * 而分叉的那一份不会报错，只会让记忆静默写不进去。
 */
@Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
fun MemoryEntry.healed(): MemoryEntry =
    if (id != null && summary != null && keywords != null && kind != null && eventDate != null) this
    else copy(
        id = id ?: UUID.randomUUID().toString(),
        summary = summary ?: "",
        keywords = keywords ?: emptyList(),
        kind = kind ?: "detail",
        eventDate = eventDate ?: ""
    )
