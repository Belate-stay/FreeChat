package com.freechat.data

import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 提示词里的「画面比例」识别与清理（1.1.21.3 白边根治）。
 *
 * 根因：① 请求体不带 size（供应商对扩展参数支持不一，刻意只发 model+prompt+image），
 * 画布常默认方形——模型把非方形构图垫上纯色边塞进画布；② 修图链把上一图 imagePrompt 原样
 * 拼在前面（模板「保留未要求改变的主体和细节」），旧的「3:4/高4宽3」和本次「5:3/长5高3」
 * 在同一 prompt 里打架，模型连旧画幅一起「保留」。这里负责：解析比例（**取最后提到的**，
 * 合并 prompt 里新要求在后）、本次有新比例时清掉旧描述、执行前补一句比例纪律。
 */
object ImageAspect {

    /**
     * 执行时附加、不入库不展示。1.1.21.4 前许可「画布不符就垫纯白」——等于教模型把非方构图
     * 塞进方形画布，是「无法生成 1:1 以外画幅」的教唆犯；现在画布已由 size 定成目标比例，
     * 纪律改为画布即比例、严禁任何垫边/裁切/拉伸。文字里不得出现可解析的比例数字（会污染
     * parse，见 fillInstructionIsOnlyForRatioRequestsAndNeverDuplicated 契约）。
     */
    const val FILL_INSTRUCTION =
        "输出图片的画布本身就是所要求的比例；严格按该画幅构图并呈现完整内容，严禁裁切或拉伸主体；参考图与画布中的白边、黑边、留白不是画面内容，不要保留；不要为凑其他画幅而加任何边。"

    private val square = Regex("正方形|正方|1\\s*[:：]\\s*1(?![0-9])")
    private val landscape = Regex("横幅|横屏|横版|横向|landscape", RegexOption.IGNORE_CASE)
    private val portrait = Regex("竖幅|竖屏|竖版|纵向|portrait", RegexOption.IGNORE_CASE)
    private val pair = Regex("(\\d{1,2})\\s*[:：xX×]\\s*(\\d{1,2})(?!\\d)")
    private val pixelPair = Regex("(\\d{2,5})\\s*[xX×]\\s*(\\d{2,5})")
    private val widthHeight = Regex("宽\\s*(\\d{1,4})\\s*[、,，和xX×:：]?\\s*高\\s*(\\d{1,4})")
    private val heightWidth = Regex("高\\s*(\\d{1,4})\\s*[、,，和xX×:：]?\\s*宽\\s*(\\d{1,4})")
    private val lengthHeight = Regex("长\\s*(\\d{1,4})\\s*[、,，和xX×:：]?\\s*高\\s*(\\d{1,4})")

    /** 画面语境词——把「3:45 的场景」这类时间排除在比例之外。 */
    private val aspectContext = Regex("比例|画幅|尺寸|幅|屏|版|方|图|画|构图|像|壁纸|背景|封面|photo|image|pic|ratio|portrait|landscape|wide|tall", RegexOption.IGNORE_CASE)
    private val clockHint = Regex("分|点|时|钟|会|上午|下午|晚上|早上")

    /** 取最后提到的比例——修图合并 prompt 里旧要求在前、新要求在后，以新为准。 */
    fun parse(text: String): Float? {
        val t = text.trim()
        if (t.isEmpty()) return null
        val hits = ArrayList<Pair<Int, Float>>()
        fun add(at: Int, w: String, h: String) {
            val width = w.toFloatOrNull() ?: return
            val height = h.toFloatOrNull() ?: return
            if (width > 0 && height > 0) hits.add(at to width / height)
        }
        heightWidth.findAll(t).forEach { add(it.range.first, it.groupValues[2], it.groupValues[1]) }
        lengthHeight.findAll(t).forEach { add(it.range.first, it.groupValues[1], it.groupValues[2]) }
        widthHeight.findAll(t).forEach { add(it.range.first, it.groupValues[1], it.groupValues[2]) }
        pixelPair.findAll(t).forEach { add(it.range.first, it.groupValues[1], it.groupValues[2]) }
        pair.findAll(t).forEach { m ->
            val w = m.groupValues[1].toInt()
            val h = m.groupValues[2].toInt()
            if (w !in 1..24 || h !in 1..24) return@forEach
            val near = t.replaceRange(m.range.first, m.range.last + 1, "")
            val window = t.substring((m.range.first - 4).coerceAtLeast(0), (m.range.last + 5).coerceAtMost(t.length))
            if (aspectContext.containsMatchIn(window) && !clockHint.containsMatchIn(near)) {
                val swapped = (portrait.containsMatchIn(t) && w > h) || (landscape.containsMatchIn(t) && w < h)
                add(m.range.first, if (swapped) "$h" else "$w", if (swapped) "$w" else "$h")
            }
        }
        return hits.maxByOrNull { it.first }?.second ?: if (square.containsMatchIn(t)) 1f else null
    }

    /** 有比例诉求（能解析出数字，或只提了横竖幅/正方形）——此时才做旧 prompt 清理与补纪律。 */
    fun mentionsAspect(text: String): Boolean =
        parse(text) != null || landscape.containsMatchIn(text) || portrait.containsMatchIn(text) || square.containsMatchIn(text)

    /**
     * 比例 → 请求里的 `size`——「一直输出 1:1」根治（1.1.21.5 实测网关后按厂商分流）。
     *
     * 根因链（2026-10-11 实测 Ark/Seedream 5.0 网关）：不带 size 时画布默认方形，prompt 写
     * 什么比例都改不动纸面；而 1.1.21.4 发算出来的生僻值、初稿改发 gpt-image 标准档
     * （1536x1024），全被「image size must be at least 3686400 pixels」拒收——该网关只认
     * 'WIDTHxHEIGHT'、'2k'、'3k'、'4k' 且 WxH 总像素不得低于 2560×1440，还不认 "auto"。
     * 被拒就回退不带 size = 永远方形，这就是「试几遍都 1:1」。
     *
     * 现在：Seedream/Ark 按目标比例**精确算 WxH**（总像素贴近下限、8 的倍数），没提比例发
     * "2k" 由模型自选画幅；自定义 OpenAI 兼容端走 gpt-image 标准三档 + "auto"。个别供应商
     * 仍拒收时 4xx 回退不带 size（宁可回默认画幅也不弄坏生图）。
     */
    fun requestSize(text: String, seedream: Boolean): String {
        // 只说「横幅/竖幅」没给数字时也定方向——折算成 3:2 / 2:3，别放给模型自选。
        val a = parse(text) ?: when {
            landscape.containsMatchIn(text) -> 1.5f
            portrait.containsMatchIn(text) -> 2f / 3f
            else -> null
        }
        if (!seedream) return when {
            a == null -> "auto"
            a >= 1.2f -> "1536x1024"
            a <= 0.83f -> "1024x1536"
            else -> "1024x1024"
        }
        if (a == null) return "2k"
        // 比例过极端时夹到 [0.4, 2.5]——长边 ≤3040 不碰长边上限，极端构图仍由 prompt 表达。
        val aspect = a.coerceIn(0.4f, 2.5f)
        val w = round8(sqrt(CANVAS_PIXELS * aspect))
        val h = round8(sqrt(CANVAS_PIXELS / aspect))
        return "${w}x${h}"
    }

    /** 网关画布像素下限 3_686_400（=2560x1440）；目标 3.72M 给 8 取整留余量。 */
    private const val CANVAS_PIXELS = 3_720_000.0

    private fun round8(v: Double): Int = ((v / 8.0).roundToInt() * 8).coerceAtLeast(8)

    fun withFillInstruction(prompt: String): String =
        if (parse(prompt) != null && !prompt.contains(FILL_INSTRUCTION)) "$prompt\n$FILL_INSTRUCTION" else prompt

    /**
     * 修图拼接的上一图 prompt 里残留的比例/尺寸描述会和本次要求打架——本次有新比例时清掉。
     * 本次没提比例（如「背景换成海边」）则原样保留，沿用上一张的画幅。
     */
    fun strip(text: String): String = text
        .replace(heightWidth, " ")
        .replace(lengthHeight, " ")
        .replace(widthHeight, " ")
        .replace(pixelPair, " ")
        .replace(Regex("(?:高|宽|长)\\s*\\d{1,4}"), " ")
        .replace(pair, " ")
        .replace(Regex("正方形|宽高比|长宽比|画面比例|比例|画幅|尺寸|分辨率|横幅|竖幅|横屏|竖屏|横版|竖版|横向|纵向"), " ")
        .replace(Regex("[ \\t]{2,}"), " ")
        .replace(Regex("(\\s*[、,，]\\s*)+"), "、")
        .trim(' ', '、', ',', '，')
}
