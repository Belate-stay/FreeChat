package com.freechat.ui.theme

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ============================================================================
//  新拟态（Neumorphism / Soft UI）
// ============================================================================
//
//  一句话：**卡片是从背景板上「挤」出来的一块**，不是贴在背景上的一张纸。
//
//  这件事的全部秘密是「**两条方向相反的阴影同时存在**」，少一条就散架：
//
//      光源在左上
//        ├─ 左上角投一条【亮】阴影（比背景亮）→ 受光的那半边被顶起来
//        └─ 右下角投一条【暗】阴影（比背景暗）→ 背光的那半边退到板子后面
//
//  只画暗的那条 = 普通的投影卡片（看着就是「浮」的）；只画亮的那条 = 一圈发光。
//  两条都在、而且**阴影颜色都从背景色推出来**（±13% 明度，不能用纯黑纯白），
//  卡片才会和背景「同一条线、同一个色」。
//
//  ## 四条来自实战的硬规矩
//
//  1. **阴影只画在卡片外面**（凸起时）。卡面在炫彩下是半透明的，画进卡片里的那半截阴影
//     会从卡面里透出来 —— 整张卡糊成一块亮斑或暗斑，这是「卡片没了面」的真正成因。
//  2. **卡面要会受光**：沿左上→右下走一道极轻的渐变（受光侧亮、背光侧暗）。
//     纯白主题下「比白更白」不存在，亮影无处可去，全靠这道渐变把卡片从白板上托起来。
//  3. **炫彩开着时，两条阴影的色相要拨到画面自己的那两盏灯上**（左上柔光 / 右下光晕），
//     明度不动。否则暖底上一圈冷灰影，卡片和背景就是「两块」而不是「一块」。
//  4. **卡面 = 背景色**（不炫彩时）。这是新拟态和「白卡片」的唯一区别。
//     炫彩开着时卡面只留一层纱（[FACE_ALPHA_LIQUID]），背景的光晕渐变清清楚楚穿过去。
//
//  凹进（`recessed`）＝ 同两条阴影**都画进卡片内侧**（CSS 的 inset）：
//  亮的那条落在右下内壁、暗的那条落在左上内壁，看上去就是一块被按下去的区域。
//
//  ## 怎么画的
//
//  阴影用**系统画布的真高斯模糊**（`Paint.setShadowLayer` + 透明的几何体：
//  只投阴影、不画实体），而不是叠好几层半透明色块。老实现（[softShadow]）叠 8 层是为了
//  绕开两个坑（`Modifier.shadow` 画不出左上方向、节点自绘时会退化成硬边灰环），
//  但叠出来的衰减带终究是台阶，凑近看能看出「一圈一圈」。真模糊只有一条平滑的羽化。
//
//  代价：硬件画布上的 `setShadowLayer` 要 **API 29(Android 10)** 才生效，
//  低版本退回叠层画法（[drawLayeredNeumorph]），观感略次但不会缺角。

/** 一套新拟态参数：两条阴影色 + 卡面的受光渐变 + 位移/模糊。全部由背景色推导，见 [neumorphTone]。 */
internal data class NeumorphTone(
    val light: Color,
    val dark: Color,
    val faceTop: Color,
    val faceBottom: Color,
    val faceAlpha: Float,
    val offset: Dp,
    val blur: Dp
)

/**
 * 位移 6dp、模糊 12dp（模糊 = 2× 位移，新拟态生成器的通用配比）：
 * 位移给「厚度」，模糊给「软度」。模糊再大一点会糊成一团 —— 设置列表的行距只有 8dp，
 * 18dp 的模糊会让上下两行的阴影在中缝里撞在一起，整列看起来是脏的。
 */
private val NEUMORPH_OFFSET = 6.dp
private val NEUMORPH_BLUR = 12.dp

/**
 * 紧凑档：小方块（选项 chip、标签）用。
 * 同一套位移放在 34dp 高的小 chip 上，厚度会占掉它自身的一半，像贴了块橡皮 ——
 * 所以位移减半模糊减四分之一，保证「厚」和「形」的比例跟大卡片一致。
 */
private val COMPACT_OFFSET = 4.dp
private val COMPACT_BLUR = 9.dp

/**
 * 炫彩开着时卡面的不透明度：0.25 = 背景的光晕渐变原样穿过去，只在上面蒙一层薄纱。
 * 再高就把渐变糊平了（那就退回「一张贴着背景的卡」），再低则卡面失去「面」的感觉。
 */
private const val FACE_ALPHA_LIQUID = 0.25f

/**
 * 米白 / 暖灰参考色：纯白主题的卡面与暗影往它们偏一点，见 [neumorphTone] 的 whiteBoard 那一支。
 *
 * 这里用的是**直接混色**而不是 [blendHue]：纯白在高明度区几乎没有色度空间
 * （L=0.96 时 HLS 能给出的最大彩度只剩 0.07），只借色相是调不出米白的，
 * 必须连明度一起借过来才看得见那口暖。
 */
private val WARM_OFF_WHITE = Color(0xFFF3EDE4)
private val WARM_SHADOW = Color(0xFFC9BEB0)

/**
 * 由主题色推出这一套新拟态参数。
 *
 * 三条规矩：
 *  1. **阴影色从背景色推**（明度 ±13%、微调饱和度），不用纯黑纯白。纯黑阴影在浅色板上像脏印子，
 *     纯白高光在深色板上像一团雾。
 *  2. **卡面色 = 背景色**。这是新拟态和「白卡片」的唯一区别：卡片不是贴上去的，
 *     分色就露馅（早期版本卡面写死 `SurfaceVariant`，正是「怎么调都像一张白卡浮着」的病根）。
 *  3. 深色 / 纯黑 / 纯白三块板各有各的麻烦：纯黑板「更黑」不存在，只能靠左上那条亮阴影定形，
 *     卡面再抬一点点；纯白板反过来 —— 「更白」不存在，亮影画了也看不见，
 *     只能靠卡面自己的暖白渐变 + 暗影定形（用户原话：「白色主题色下拟物风卡片效果不明显」）。
 *
 * @param liquid 炫彩开着时传当前色板（[LiquidPalette]）：阴影的基准色改成流动底色、
 *        色相拨到画面的冷暖光晕上，卡片才跟会动的背景是一块料子。
 */
internal fun neumorphTone(
    colors: FreeChatColors,
    isDark: Boolean,
    selected: Boolean,
    compact: Boolean = false,
    liquid: LiquidPalette? = null
): NeumorphTone {
    val bg = colors.Background
    val lum = bg.luminance()
    val oled = lum < 0.02f
    // 白板：纯白主题（#FFFFFF）。「比白更白」不存在 —— 亮影画出来是白叠白，等于没有。
    val whiteBoard = !isDark && lum > 0.95f
    // 炫彩下拿流动底色当基准：它跟画面同源，阴影的明度才落在背景真实的明度上
    val base = liquid?.base ?: bg

    val lightShift = when {
        oled -> 0.06f
        isDark -> 0.12f
        whiteBoard -> 0.02f
        else -> 0.13f
    }
    val darkShift = when {
        oled -> -0.02f
        isDark -> -0.18f
        whiteBoard -> -0.11f
        else -> -0.13f
    }

    var light = base.shiftLightness(lightShift, satScale = 0.6f)
    var dark = base.shiftLightness(darkShift, satScale = 1.15f)

    if (liquid != null) {
        // 借色相不借明度：画面左上那盏暖光 / 右下那团光晕是什么色，影子就跟着偏什么色。
        // 直接拿光晕色去 lerp 会把影子整条提亮（那些颜色本身很亮），所以只动 H、S，L 是自己的。
        light = light.blendHue(liquid.glow.first(), 0.45f)
        dark = dark.blendHue(liquid.bloom.first(), 0.35f)
    }

    val faceBase = when {
        selected -> colors.AccentMuted
        oled -> base.shiftLightness(0.05f)
        else -> base
    }

    // 卡面的受光渐变：左上（受光）比底色亮一丁点，右下（背光）暗一丁点。
    // 幅度必须**很小** —— 大了就成了「贴上去的一张渐变卡」，那正是第一稿的白卡。
    val d = when {
        whiteBoard -> 0.05f
        isDark -> 0.05f
        else -> 0.035f
    }
    var top = faceBase.shiftLightness(d / 2f)
    var bottom = faceBase.shiftLightness(-d / 2f)

    if (whiteBoard && !selected) {
        // 纯白主题：#FFFFFF 的卡摆在 #FFFFFF 的板上，等于没有卡。
        // 按用户要求走「纯白 + 米白」：上半张与背景同为纯白（融进板子），
        // 下半张浮出一点暖白，卡片才立得住；暗影也一起偏暖，免得暖白卡下面压着一圈发青的影。
        top = lerp(top, WARM_OFF_WHITE, 0.35f)
        bottom = lerp(bottom, WARM_OFF_WHITE, 0.75f)
        dark = lerp(dark, WARM_SHADOW, 0.45f)
    }

    return NeumorphTone(
        light = light,
        dark = dark,
        faceTop = top,
        faceBottom = bottom,
        // 不炫彩时卡面就是背景色，全不透明（等于原来的纯色卡面）；炫彩时留一层纱
        faceAlpha = if (liquid != null) FACE_ALPHA_LIQUID else 1f,
        offset = if (compact) COMPACT_OFFSET else NEUMORPH_OFFSET,
        blur = if (compact) COMPACT_BLUR else NEUMORPH_BLUR
    )
}

/**
 * 画两条阴影（凸起画在卡片**外面**、凹进画在卡片**里面**）。
 *
 * 顺序是**先暗后亮** —— 反了亮的那条会被暗的盖掉（CSS 的 box-shadow 同一条规矩）。
 */
internal fun DrawScope.drawNeumorph(shape: Shape, tone: NeumorphTone, recessed: Boolean) {
    if (size.width <= 0f || size.height <= 0f) return
    val path = shapePath(shape)
    val dx = tone.offset.toPx()
    val blur = tone.blur.toPx()

    if (android.os.Build.VERSION.SDK_INT < 29) {
        drawLayeredNeumorph(shape, path, tone, recessed)
        return
    }

    val canvas = drawContext.canvas.nativeCanvas
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        // 几何体本身是透明的：这一笔只为「投出一条阴影」，不画实体
        color = android.graphics.Color.TRANSPARENT
    }
    val ap = path.asAndroidPath()
    val ring = outsideRing(ap, size)

    if (!recessed) {
        // 只画在卡片外面。卡面在炫彩下只有 25% 不透明，阴影一旦画进卡里就会透出来，
        // 把整张卡糊成一块亮斑（亮影的芯）或暗斑（暗影的芯）—— 卡面等于白做了。
        canvas.save()
        canvas.clipPath(ring)
        paint.setShadowLayer(blur, dx, dx, tone.dark.toArgb())
        canvas.drawPath(ap, paint)
        paint.setShadowLayer(blur, -dx, -dx, tone.light.toArgb())
        canvas.drawPath(ap, paint)
        canvas.restore()
        return
    }

    // 凹进：拿「卡片外面那一大圈」当几何体去投阴影，再把整个结果裁进卡片里 ——
    // 阴影只能从边框往卡内渗，于是暗的落在左上内壁、亮的落在右下内壁。
    canvas.save()
    canvas.clipPath(ap)
    paint.setShadowLayer(blur, dx, dx, tone.dark.toArgb())
    canvas.drawPath(ring, paint)
    paint.setShadowLayer(blur, -dx, -dx, tone.light.toArgb())
    canvas.drawPath(ring, paint)
    canvas.restore()
}

/**
 * 「卡片外面」那一圈（EVEN_ODD：一大块矩形挖掉卡片本身）。
 *
 * 它是这套画法的枢纽：凸起时拿它当**裁剪区**（阴影只许落在卡外），
 * 凹进时拿它当**几何体**（让阴影从边框往卡里渗）。同一块路径，两个方向。
 *
 * 外框取 (-w,-h)~(2w,2h)：足够远，它自己那条边的阴影永远够不着画面里看得见的地方。
 */
private fun outsideRing(card: android.graphics.Path, size: Size): android.graphics.Path =
    android.graphics.Path().apply {
        fillType = android.graphics.Path.FillType.EVEN_ODD
        addRect(
            -size.width, -size.height, size.width * 2f, size.height * 2f,
            android.graphics.Path.Direction.CW
        )
        addPath(card)
    }

/** [outsideRing] 的 Compose 版（退路画法用 DrawScope 的 clipPath，只认 Compose 的 Path）。 */
private fun outsideRing(card: Path, size: Size): Path = Path().apply {
    fillType = PathFillType.EvenOdd
    addRect(androidx.compose.ui.geometry.Rect(-size.width, -size.height, size.width * 2f, size.height * 2f))
    addPath(card)
}

/**
 * API < 29 的退路：叠层画法（同 [softShadow] 的手感）。
 *
 * 那边的硬件画布只支持文字阴影，`setShadowLayer` 对图形不生效 —— 不退回叠层的话，
 * 老机器上会**一条阴影都看不到**（卡片直接变平）。
 */
private fun DrawScope.drawLayeredNeumorph(
    shape: Shape,
    path: Path,
    tone: NeumorphTone,
    recessed: Boolean
) {
    val corner = when (val o = shape.createOutline(size, layoutDirection, this)) {
        is Outline.Rounded -> o.roundRect.topLeftCornerRadius
        else -> CornerRadius.Zero
    }
    val dx = tone.offset.toPx()
    val spread = tone.blur.toPx() * 0.9f

    fun paintStack(color: Color, sx: Float, sy: Float) {
        for (i in 0 until LAYERED_STEPS) {
            val t = i / (LAYERED_STEPS - 1f)
            val alpha = 0.95f * (1f - t) * (1f - t)
            if (alpha < 0.004f) continue
            val grow = spread * t
            drawRoundRect(
                color = color.copy(alpha = alpha),
                topLeft = Offset(sx - grow, sy - grow),
                size = Size(size.width + grow * 2f, size.height + grow * 2f),
                cornerRadius = CornerRadius(
                    (corner.x + grow).coerceAtLeast(0f),
                    (corner.y + grow).coerceAtLeast(0f)
                )
            )
        }
    }

    if (!recessed) {
        // 同样只画在卡外：叠层画法是一层层的实心圆角矩形，不裁的话整张卡都会被盖住
        clipPath(outsideRing(path, size)) {
            paintStack(tone.dark, dx, dx)
            paintStack(tone.light, -dx, -dx)
        }
        return
    }
    // 凹进的老路：贴着边框描一层内沿光（左上深、右下亮），靠对角渐变笔分方向
    for (i in 0 until LAYERED_STEPS) {
        val t = i / (LAYERED_STEPS - 1f)
        val alpha = 0.9f * (1f - t) * (1f - t)
        if (alpha < 0.004f) continue
        val width = dx * 0.6f + spread * 0.35f * t
        drawRoundRect(
            brush = diagonalNeumorphFade(tone.dark.copy(alpha = alpha), towardsBottomRight = false),
            cornerRadius = corner,
            style = Stroke(width = width)
        )
        drawRoundRect(
            brush = diagonalNeumorphFade(tone.light.copy(alpha = alpha), towardsBottomRight = true),
            cornerRadius = corner,
            style = Stroke(width = width)
        )
    }
}

/** 退路用的叠层层数：8 层在手机上已经看不出台阶，再多只是白烧 GPU。 */
private const val LAYERED_STEPS = 8

/** 对角渐变笔：让一束光只出现在受光方向的对侧（左上透明、右下最深，反之亦然）。 */
private fun DrawScope.diagonalNeumorphFade(tone: Color, towardsBottomRight: Boolean): Brush {
    val from = if (towardsBottomRight) Offset.Zero else Offset(size.width, size.height)
    val to = if (towardsBottomRight) Offset(size.width, size.height) else Offset.Zero
    return Brush.linearGradient(
        colors = listOf(tone, tone.copy(alpha = 0f)),
        start = from,
        end = to
    )
}

/** 把 [Shape] 摊成一条路径（圆角/矩形/自定义三种 Outline 都能画）。 */
private fun DrawScope.shapePath(shape: Shape): Path {
    val path = Path()
    when (val o = shape.createOutline(size, layoutDirection, this)) {
        is Outline.Rounded -> path.addRoundRect(o.roundRect)
        is Outline.Rectangle -> path.addRect(o.rect)
        is Outline.Generic -> path.addPath(o.path)
    }
    return path
}

/**
 * 明度平移（HLS 空间）。
 *
 * 为什么不直接在 RGB 上乘系数：那样会连**饱和度**一起改 —— 米色底乘一下会发灰发脏，
 * 深色底乘一下会偏色。只动 L、S 单独给个缩放，九套配色（棕/蓝/白 × 浅/深/纯黑）才对得上。
 */
private fun Color.shiftLightness(delta: Float, satScale: Float = 1f): Color {
    val (h, l, s) = toHsl()
    return fromHsl(h, l + delta, s * satScale)
}

/**
 * 借色相（和一点饱和度），**明度保持自己的**。
 *
 * 给「跟背景的冷暖光晕对齐」用：光晕色本身都很亮（#FFF0D2 / #E3EFFF），
 * 直接 lerp 会把暗影整条提亮成一块奶糖 —— 只有 H、S 是该借的。
 */
private fun Color.blendHue(other: Color, amount: Float): Color {
    val (h1, l1, s1) = toHsl()
    val (h2, _, s2) = other.toHsl()
    if (s2 < 0.04f) return this          // 对方本身没色相（纯灰/纯白），借无可借
    var dh = (h2 - h1) % 360f
    if (dh > 180f) dh -= 360f
    if (dh < -180f) dh += 360f
    var h = h1 + dh * amount
    if (h < 0f) h += 360f
    if (h >= 360f) h -= 360f
    return fromHsl(h, l1, s1 + (s2 - s1) * amount * 0.5f)
}

/** RGB → (色相 0..360, 明度 0..1, 饱和度 0..1) */
private fun Color.toHsl(): Triple<Float, Float, Float> {
    val mx = maxOf(red, green, blue)
    val mn = minOf(red, green, blue)
    val l = (mx + mn) / 2f
    val d = mx - mn
    val s = if (d == 0f) 0f else d / (1f - kotlin.math.abs(2f * l - 1f)).coerceAtLeast(1e-5f)
    val hue = when {
        d == 0f -> 0f
        mx == red -> 60f * (((green - blue) / d) % 6f)
        mx == green -> 60f * (((blue - red) / d) + 2f)
        else -> 60f * (((red - green) / d) + 4f)
    }.let { if (it < 0f) it + 360f else it }
    return Triple(hue, l, s.coerceIn(0f, 1f))
}

/** (色相, 明度, 饱和度) → Color */
private fun fromHsl(hue: Float, lightness: Float, saturation: Float): Color {
    val l = lightness.coerceIn(0f, 1f)
    val s = saturation.coerceIn(0f, 1f)
    val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
    val x = c * (1f - kotlin.math.abs((hue / 60f % 2f) - 1f))
    val m = l - c / 2f
    val rgb = when {
        hue < 60f -> Triple(c, x, 0f)
        hue < 120f -> Triple(x, c, 0f)
        hue < 180f -> Triple(0f, c, x)
        hue < 240f -> Triple(0f, x, c)
        hue < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(
        red = (rgb.first + m).coerceIn(0f, 1f),
        green = (rgb.second + m).coerceIn(0f, 1f),
        blue = (rgb.third + m).coerceIn(0f, 1f),
        alpha = 1f
    )
}
