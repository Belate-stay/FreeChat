package com.freechat.core

import com.freechat.model.CharacterProfile
import com.freechat.model.DialogueMode
import java.util.Calendar

/**
 * 主动智能的编解码与到点开口提示词（纯函数，1.0.91 M1 从 ChatViewModel 平移，行为一字不变）。
 * 闹钟调度（AlarmManager/ProactiveStore）留在 Android 层；这里只管「指令长什么样、怎么解析、到点说什么」。
 */

/**
 * 模型在回复末尾附加的隐藏指令（用户永远看不到这一行）。
 * cancel=true 表示撤销待触发的计划；atMillis 非空表示新定一个时刻；两者都空 = 空指令（跳过，什么都不改）。
 */
data class ProactiveSignal(val cancel: Boolean, val atMillis: Long?, val reason: String)

/**
 * 一次「到点唤醒」的上下文：模型自己定的时间到了，要它现在决定开不开口。
 * sinceCount = 定下这个时间之后用户又说过几条（喂回去让它自己判断这事还该不该提）。
 * forceNotify = 冷启动路径，前台标记不可信，无条件发通知。
 */
data class ProactiveFire(val reason: String, val sinceCount: Int = 0, val forceNotify: Boolean = false)

/** 指令标记本身（不要求顶格）：模型经常把它接在正文后面，或者被 markdown 的 ** 包着 */
private val PROACTIVE_TAG = Regex("""[\[【]\s*主动\s*智能\s*[\]】]""")

/** 「只剩序号/标点，没有真正的内容」的行首残留（如「1.」「-」），不值得显示 */
private val PROACTIVE_ONLY_MARKER = Regex("""^[\d\s.、,，)）:：]*$""")

/**
 * 从模型输出里剥掉主动智能指令，返回（干净正文, 指令）。
 * 必须在解析情绪标签/分条之前调用 —— 否则 [主动智能] 会被正文的方括号清洗规则当标签删掉、
 * 只留下「15:00 | 约好了三点」这种半截指令显示在气泡里（理由是说给模型自己听的私房话，
 * 漏进气泡就等于把「我打算三点找他」这个心思直接摊给用户看）。
 *
 * 标记**不要求单独一行**：写成「那我三点找你 [主动智能] 15:00 | 约好了」时，
 * 按整行匹配就漏过去了 —— 后面的通用方括号清洗只会吃掉 [主动智能] 这六个字，
 * 剩下的「15:00 | 约好了」照样显示。所以规则是：一行里只要出现标记，
 * 从标记到行尾全部当指令，标记之前的正文保留。
 */
fun stripProactiveDirective(raw: String): Pair<String, ProactiveSignal?> {
    var signal: ProactiveSignal? = null
    val kept = mutableListOf<String>()
    for (line in raw.split("\n")) {
        val tag = PROACTIVE_TAG.find(line)
        if (tag == null) { kept.add(line); continue }
        // 标记前面可能只剩 markdown/列表符号（**、-、>）或一个序号（「1. 」），
        // 清完为空或只剩序号就整行不留（否则气泡里会冒出一个孤零零的「1.」）
        val before0 = line.substring(0, tag.range.first)
            .trim().trim('*', '_', '`', '-', '>', '—', '·', '　').trim()
        val before = if (PROACTIVE_ONLY_MARKER.matches(before0)) "" else before0
        val payload = line.substring(tag.range.last + 1).trim().trim('*', '_', '`').trim()
        // 多行指令取「信息量最大」的那条：模型想说「这次先不开口，但改到 30 分钟后」
        // 时容易写成两行（[主动智能] 跳过 + [主动智能] +30m），只认第一行会把新时间丢掉。
        // 优先级：定时 > 取消 > 空指令（定时是一次具体的新意图，取消只是否定）
        val s = decodeProactivePayload(payload)
        if (proactiveRank(s) > proactiveRank(signal)) signal = s
        if (before.isNotBlank()) kept.add(before)
    }
    return kept.joinToString("\n").trim() to signal
}

/** 指令的分量：定时 > 取消 > 空指令（同一回复里写了多行时，取分量最大的那条） */
fun proactiveRank(x: ProactiveSignal?): Int {
    if (x == null) return -1
    if (x.atMillis != null) return 2
    return if (x.cancel) 1 else 0
}

/** 解析指令内容：「取消」「15:00」「+45m」「明天 9:00」，竖线后面是给自己的理由 */
fun decodeProactivePayload(payload: String, nowMillis: Long = System.currentTimeMillis()): ProactiveSignal {
    val parts = payload.split('|', '｜', limit = 2)
    val head = parts[0].trim().lowercase().replace("：", ":")
    val reason = parts.getOrNull(1)?.trim().orEmpty()
    val cancelWords = listOf("取消", "撤销", "算了", "cancel", "none", "无", "不需要")
    if (cancelWords.any { head.startsWith(it) }) return ProactiveSignal(cancel = true, atMillis = null, reason = reason)
    // 「跳过」= 这次不开口，但别动已有的计划（它和「取消」是两回事）
    val at = parseProactiveTime(head, nowMillis) ?: return ProactiveSignal(cancel = false, atMillis = null, reason = reason)
    return ProactiveSignal(cancel = false, atMillis = at, reason = reason)
}

/**
 * 时间解析：相对（+45m / +2h / +1d）或绝对（15:00 / 明天 9:00），解析不了返回 null。
 * 不锚定整串 —— 模型常写成「跳过 +30m」这种同行混写，锚定就会整条丢掉。
 */
fun parseProactiveTime(t: String, nowMillis: Long = System.currentTimeMillis()): Long? {
    val s = t.trim().replace(" ", "")
    if (s.isEmpty()) return null
    // 相对时间必须带 +：裸的「45m」在正文里太容易误伤
    Regex("""\+(\d+)(m|min|分钟|分|h|小时|时|d|天)""").find(s)?.let { m ->
        val n = m.groupValues[1].toLongOrNull() ?: return null
        val ms = when (m.groupValues[2]) {
            "m", "min", "分钟", "分" -> n * 60_000L
            "h", "小时", "时" -> n * 3_600_000L
            else -> n * 86_400_000L
        }
        if (ms < 60_000L) return null
        // 上限 24 小时（与 ProactiveScheduler 的视界一致）：再远就该由未来的自己重新判断。
        // 压到上限而不是返回 null —— 返回 null 等于把模型「+48h」的意图整个吞掉，它还以为定上了
        return nowMillis + ms.coerceAtMost(24 * 3_600_000L)
    }
    val tomorrow = s.contains("明天") || s.contains("明日")
    Regex("""(\d{1,2}):(\d{2})""").find(s)?.let { m ->
        var h = m.groupValues[1].toIntOrNull() ?: return null
        val min = m.groupValues[2].toIntOrNull() ?: return null
        if (h !in 0..23 || min !in 0..59) return null
        // 「下午3:00」的 3 是 12 小时制的 3 点。不认这个前缀的话会被当成凌晨 3 点，
        // 而凌晨 3 点已经过去 → 顺延到明天 → 再被静默时段推到明早 7:30：差了大半天。
        // 提示词里要求一律写 24 小时制，但模型偶尔还是会顺手写成「下午3:00」，这里兜住。
        h = when {
            s.contains("下午") || s.contains("傍晚") || s.contains("晚") || s.contains("夜") ->
                if (h < 12) h + 12 else h
            s.contains("凌晨") || s.contains("清晨") || s.contains("早上") ||
                s.contains("上午") || s.contains("早") -> if (h == 12) 0 else h
            else -> h
        }
        if (h !in 0..23) return null
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            if (tomorrow) add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, h)
            set(Calendar.MINUTE, min)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            // 说的是今天、但那个点已经过了（模型常把「今晚」写成「9:00」）→ 顺延到明天
            if (!tomorrow && timeInMillis <= nowMillis + 60_000L) add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }
    return null
}

fun buildProactiveFirePrompt(fire: ProactiveFire, character: CharacterProfile?): String {
    val reason = if (fire.reason.isBlank()) "你当时觉得该找对方说说话" else "你当时的理由是：${fire.reason}"
    // 定完之后对方又说过话：得告诉它一声 —— 可能那件事已经聊完了、也可能正好还作数
    //（「三点聊新番」的约定不会因为对方中途回了个「好」就作废）。判断权在它，我们只提供事实
    val since = if (fire.sinceCount > 0) {
        "注意：你定下这个时间之后，对方又主动发过 ${fire.sinceCount} 条消息（都在上面的上下文里）。" +
            "如果那件事已经聊过了、或者你现在不想说了，就按下面的 2 处理，这很正常。\n"
    } else ""
    // 开口的形式按当前对话模式走（三种模式都支持主动智能）
    val openLine = when (character?.dialogueMode ?: DialogueMode.WECHAT) {
        DialogueMode.ACTION -> "1. 想开口：按「动作演绎」档写——写你自己的动作、神态、心理与你亲口说的台词（台词用「」括起来），不写旁白与环境；第三方角色只在必要时少量出现。"
        DialogueMode.PLOT -> "1. 想开口：按「剧情补足」档写——像小说正文一样推进这一段，可以有旁白、环境描写和你生活里的其他人。"
        else -> "1. 想开口：就像平常一样自然说话。可以一条，也可以按你的性格连着发几条；符合你的人设与此刻的情绪。"
    }
    return "【现在是你自己定的时间】\n" +
        "你在上一次聊天时给自己定了个时间，打算现在找对方——$reason。现在时间到了。\n" +
        since +
        "现在由你决定：**要不要真的开口**。\n" +
        openLine + "\n" +
        "★ 说什么由你定：接着上次没说完的事往下说，或者开一个你自己的新话题（你这边新发生的事、你突然想起的事）——哪个自然选哪个。\n" +
        "2. 不想开口：只输出一行 [主动智能] 跳过，不要输出任何正文。这不是失败，是很正常的选择——也许你困了、在忙、气还没消、或者就是觉得没什么可说的。真人不总是有话说。\n" +
        "3. 想「这次先不说、但改个时间再说」：写成同一行——[主动智能] 跳过 +30m | 待会儿再讲。\n" +
        "4. 只定下一次也行：[主动智能] +2h | 等他忙完。什么都不定也可以。\n" +
        "★ 别发「在吗」「忙吗」这种空话：要么真的有事说事，要么就别开口。\n" +
        "★ 对方可能正在忙、在睡、不想理你——你开口之后对方没回，也是正常的，不要追问「为什么不回我」。\n" +
        "★ 这一轮对方并没有说话，所以你是在主动开口；不要问「你刚说什么」「在吗」这类需要对方先说过话才成立的问题。"
}
