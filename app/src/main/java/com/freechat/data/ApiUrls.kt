package com.freechat.data

import java.net.URI

/**
 * 模型 API 地址规整（2026-10-20 用户拍板）：用户加不加后缀都能用——**根路径填对就行**。
 *
 * 病根先例：中转站（New API 网关）要 /v1，DeepSeek 不要 /v1——同一表单两种体感；
 * 且「获取模型列表」「对话」「生图」三条链各拼各的（有的补 v1 有的不补），
 * 同一 URL 一条通一条挂，用户只能靠猜。
 *
 * 规则（**识别而非瞎猜**）：
 *  ① 误粘的端点后缀（/chat/completions、/images/generations…）剥掉再拼——粘全链接也对；
 *  ② 域名根（无路径）→ 自动补 /v1（OpenAI 兼容网关的共同惯例：DeepSeek / New API / OpenAI 都是根 + /v1 + 端点）；
 *  ③ 路径以版本段结尾（/v1、/v1beta、/api/v2…）→ 原样；
 *  ④ 路径以 /api 结尾 → 补 /v1（New API 型网关的 api 前缀形态）；
 *  ⑤ 其他自定义路径（/openai、/gateway…）→ 原样——路径是厂商定的，不猜；
 *  ⑥ 尾斜杠、大小写、误粘 /v1/ 端点前缀一并归一。
 */
object ApiUrls {

    private val ENDPOINT_TAIL = Regex(
        """/(?:chat/completions|images/(?:generations|edits)|audio/(?:speech|transcriptions)|models)/?$""",
        RegexOption.IGNORE_CASE
    )
    private val VERSION_TAIL = Regex("""/(?:api/)?v[0-9]+(?:[a-z]+)?$""", RegexOption.IGNORE_CASE)

    /** 用户输入的 base → 去空白/尾斜杠/误粘端点段。 */
    fun normalizeBase(raw: String): String =
        raw.trim().replace(ENDPOINT_TAIL, "").trimEnd('/')

    /**
     * 拼完整端点：[rawBase] 用户随便填（加不加 /v1、/api、端点后缀都行），
     * [suffix] 是端点段（"chat/completions"/"images/generations"/…，带不带 /v1/ 前缀都认）。
     */
    fun endpoint(rawBase: String, suffix: String): String {
        val base = normalizeBase(rawBase)
        val s = suffix.trim().replace(Regex("^/?v[0-9]+(?:[a-z]+)?/", RegexOption.IGNORE_CASE), "").trim('/')
        if (base.endsWith("/$s", ignoreCase = true)) return base
        val path = runCatching { URI(base).path?.trim('/') }.getOrNull().orEmpty()
        val root = when {
            path.isEmpty() -> "$base/v1"                                              // ② 裸域名根
            // ③ 已有版本段：原样，但版本段小写归一（/V2 → /v2，⑥ 大小写归一）
            VERSION_TAIL.containsMatchIn(base) ->
                base.replace(Regex("/(?:api/)?v[0-9]+(?:[a-z]+)?$", RegexOption.IGNORE_CASE)) { it.value.lowercase() }
            base.substringAfterLast('/').equals("api", ignoreCase = true) -> "$base/v1" // ④ /api 结尾
            else -> base                                                               // ⑤ 厂商自定义路径
        }
        return "$root/$s"
    }
}
