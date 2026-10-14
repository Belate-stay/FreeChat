package com.freechat.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString

/**
 * 内置模型备注（1.1.28）：尾句「作者自用 API，不保证随时在线」统一**红色**显示（全内置模型）。
 * 三语各有一版尾句，命中哪版染哪版；命中不了原样输出（自定义模型备注不参与）。
 * 无句号是口径的一部分（用户点名「不要句号」），检测串里自然也不带。
 */
private val RED_TAILS = listOf(
    "作者自用 API，不保证随时在线",
    "作者自用 API，不保證隨時在線",
    "Author's own API — not guaranteed to be online."
)

/** 尾句起点；没尾句返回 null */
private fun redTailHit(desc: String): Pair<Int, String>? = RED_TAILS.mapNotNull { t ->
    val i = desc.indexOf(t)
    if (i > 0) i to t else null
}.minByOrNull { it.first }

/**
 * 把备注拆成「简介 + 红色尾句」两行（1.1.29.3 ③）：逗号前的简介做第一行（去掉尾逗号），
 * 红尾独立第二行。没尾句的整段按 [base] 原样输出（自定义模型备注不参与）。
 */
fun modelDescAnnotated(desc: String, base: Color, red: Color): AnnotatedString = buildAnnotatedString {
    val hit = redTailHit(desc)
    if (hit == null) {
        pushStyle(SpanStyle(color = base))
        append(desc)
        pop()
        return@buildAnnotatedString
    }
    pushStyle(SpanStyle(color = base))
    append(desc.substring(0, hit.first).trimEnd('，', ',', ' '))
    append("\n")
    pop()
    pushStyle(SpanStyle(color = red))
    append(hit.second)
    pop()
    val after = hit.first + hit.second.length
    if (after < desc.length) {
        pushStyle(SpanStyle(color = base))
        append(desc.substring(after))
        pop()
    }
}

/** 备注两行化的纯文本版（字幕/副标题槽位用）：与 [modelDescAnnotated] 同一拆法 */
fun modelDescTwoLines(desc: String): String {
    val hit = redTailHit(desc) ?: return desc
    return desc.substring(0, hit.first).trimEnd('，', ',', ' ') + "\n" + desc.substring(hit.first)
}
