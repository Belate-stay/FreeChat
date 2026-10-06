package com.freechat.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import kotlin.math.ceil
import kotlin.math.roundToInt

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
//  1. **阴影只画在卡片外面**（凸起时），不能污染卡面与文字。
//  2. **卡面要会受光**：沿左上→右下走一道极轻的渐变（受光侧亮、背光侧暗）。
//     纯白主题下「比白更白」不存在，亮影无处可去，全靠这道渐变把卡片从白板上托起来。
//  3. **流光不改变材质**，卡面始终实体；用同一屏幕色场的裁片，而不是透出下面的文字。
//  4. **卡面 = 背景**，流光下共用根坐标与相位，阴影与受光保持同一块材料的观感。
//
//  凹进（`recessed`）＝ 同两条阴影**都画进卡片内侧**（CSS 的 inset）：
//  亮的那条落在右下内壁、暗的那条落在左上内壁，看上去就是一块被按下去的区域。
//
//  ## 怎么画的
//
//  阴影只在几何改变时生成一张软件模糊的 ALPHA_8 蒙版，随后复用蒙版、分别染亮/暗两色。
//  流光动画不参与缓存键，不会每帧重新高斯模糊；Android 8 起也不依赖硬件路径阴影支持。

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

internal val LiquidFaceHighlight = Color.White.copy(alpha = 0.025f)
internal val LiquidFaceShade = Color.Black.copy(alpha = 0.015f)

/** 小开关不沿用大卡片的 6dp 光晕：同色凹槽、1dp 位移、2dp 柔边，保住深色填充。 */
internal fun neumorphControlTone(fill: Color, raised: Boolean): NeumorphTone = NeumorphTone(
    light = lerp(fill, Color.White, if (raised) 0.10f else 0.16f),
    dark = lerp(fill, Color.Black, if (raised) 0.20f else 0.22f),
    faceTop = if (raised) fill else lerp(fill, Color.White, 0.035f),
    faceBottom = lerp(fill, Color.Black, if (raised) 0.02f else 0.035f),
    faceAlpha = 1f, offset = 1.dp, blur = 2.dp,
)

/**
 * 实体新拟态面：关闭流光时保持原受光面；开启时延续屏幕色场，仅加极轻的受光层。
 * State 在 drawWithCache 构造块读取，HazeSource 内也会持续更新；不重组、不创建独立时钟。
 * 调用方先裁好形状；双阴影的缓存独立于时钟，不会每帧重新生成蒙版。
 */
@Composable
internal fun Modifier.neumorphicFace(
    tone: NeumorphTone,
    plain: Color,
    strength: () -> Float,
    inheritLiquid: Boolean = true,
): Modifier {
    val liquid = LocalLiquidMode.current && inheritLiquid
    val palette = LocalLiquidPalette.current
    val clock = LocalLiquidClock.current
    val frame = LocalLiquidFrame.current
    val liquidProgress = LocalLiquidProgress.current
    val origin = remember { mutableStateOf(Offset.Zero) }
    return this
        .then(if (liquid) Modifier.onGloballyPositioned { origin.value = it.positionInRoot() } else Modifier)
        .drawWithCache {
            val opacity = strength().coerceIn(0f, 1f)
            val liquidOpacity = if (liquid) liquidProgress?.value ?: 1f else 0f
            val ms = if (opacity > 0f && liquidOpacity > 0f) clock?.value ?: 0L else 0L
            val screen = if (liquid) frame?.value else null
            val fieldFrame = screen?.takeIf { it.width > 0f && it.height > 0f }
                ?.let { liquidFrameAtOrigin(it, origin.value) }
            val face = Brush.linearGradient(listOf(tone.faceTop, tone.faceBottom))
            val lighting = Brush.linearGradient(listOf(LiquidFaceHighlight, Color.Transparent, LiquidFaceShade))
            onDrawBehind {
                drawRect(plain)
                val paintMaterial = {
                    drawRect(face)
                    if (fieldFrame != null && liquidOpacity > 0f) {
                        drawLiquidField(palette, ms, fieldFrame, alpha = liquidOpacity)
                        drawRect(lighting, alpha = liquidOpacity)
                    }
                }
                if (opacity >= 1f) paintMaterial() else if (opacity > 0f) drawIntoCanvas { canvas ->
                    // 整体淡入/淡出，避免圆面与流光各叠一次 alpha 导致切换时忽明忽暗。
                    canvas.saveLayer(Rect(Offset.Zero, size), Paint().apply { alpha = opacity })
                    paintMaterial()
                    canvas.restore()
                }
            }
        }
}

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
private val COMPACT_OFFSET = 3.dp
private val COMPACT_BLUR = 6.dp

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
 */
internal fun neumorphTone(
    colors: FreeChatColors,
    isDark: Boolean,
    selected: Boolean,
    compact: Boolean = false,
): NeumorphTone {
    val bg = colors.Background
    val lum = bg.luminance()
    val oled = lum < 0.02f
    // 白板：纯白主题（#FFFFFF）。「比白更白」不存在 —— 亮影画出来是白叠白，等于没有。
    val whiteBoard = !isDark && bg == Color.White
    val base = bg

    val lightShift = when {
        oled -> 0.11f
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

    val light = base.shiftLightness(lightShift, satScale = 0.30f)
    val dark = base.shiftLightness(darkShift, satScale = 0.35f)

    // 与所在背景同一块材料，不能改成独立 Surface 色阶再描一圈边框。
    // OLED 需要轻抬卡面，否则纯黑没有可见的背光侧。
    val faceBase = when {
        selected -> colors.AccentMuted
        oled -> colors.Surface
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
        top = lerp(Color.White, colors.Surface, 0.12f)
        bottom = lerp(Color.White, colors.Surface, 0.60f)
    }

    return NeumorphTone(
        light = light,
        dark = dark,
        faceTop = top,
        faceBottom = bottom,
        faceAlpha = 1f,
        offset = if (compact) COMPACT_OFFSET else NEUMORPH_OFFSET,
        blur = if (compact) COMPACT_BLUR else NEUMORPH_BLUR
    )
}

/**
 * 缓存双向柔光阴影。凸起只画外侧，凹进只画内侧，不污染半透明卡面与文字。
 * 路径、颜色过滤器、蒙版均在 drawWithCache 内缓存，流光时钟不在这里读取。
 */
internal fun Modifier.neumorphShadow(
    shape: Shape, tone: NeumorphTone, recessed: Boolean, strength: () -> Float = { 1f },
): Modifier = drawWithCache {
    val path = shapePath(shape.createOutline(size, layoutDirection, this))
    val dx = tone.offset.toPx()
    // 柔光没有高频细节，最多 1.5 像素/dp 足够；只缓存单通道，不为每张卡存 ARGB 全彩图。
    val scale = minOf(1f, 1.5f / density)
    val padding = ceil((tone.blur.toPx() * 2f + dx + 2f) * scale).toInt()
    val key = ShadowMaskKey(shape, size, layoutDirection, density, fontScale, tone.blur.value, padding, recessed)
    val mask = if (size.width > 0f && size.height > 0f) ShadowMasks.cache.get(key)
        ?: createShadowMask(path, size, scale, padding, tone.blur.toPx(), recessed).also { ShadowMasks.cache.put(key, it) }
    else null
    val darkFilter = ColorFilter.tint(tone.dark)
    val lightFilter = ColorFilter.tint(tone.light)
    val destination = mask?.let { IntSize((it.image.width / scale).roundToInt(), (it.image.height / scale).roundToInt()) }
    val origin = -padding / scale
    // Haze 的源录制关闭普通绘制块的状态观察；在 cache 构造块读取，才能保留材质过渡。
    val opacity = strength().coerceIn(0f, 1f)
    onDrawBehind {
        if (mask != null && destination != null && opacity > 0f) {
            clipPath(path, if (recessed) ClipOp.Intersect else ClipOp.Difference) {
                drawImage(mask.image, dstOffset = IntOffset((origin + dx).roundToInt(), (origin + dx).roundToInt()),
                    dstSize = destination, alpha = opacity, colorFilter = darkFilter, filterQuality = FilterQuality.Low)
                drawImage(mask.image, dstOffset = IntOffset((origin - dx).roundToInt(), (origin - dx).roundToInt()),
                    dstSize = destination, alpha = opacity, colorFilter = lightFilter, filterQuality = FilterQuality.Low)
            }
        }
    }
}

/** 把 [Shape] 摊成一条路径（圆角/矩形/自定义三种 Outline 都能画）。 */
private fun shapePath(outline: Outline): Path {
    val path = Path()
    when (val o = outline) {
        is Outline.Rounded -> path.addRoundRect(o.roundRect)
        is Outline.Rectangle -> path.addRect(o.rect)
        is Outline.Generic -> path.addPath(o.path)
    }
    return path
}

private data class ShadowMaskKey(
    val shape: Shape, val size: Size, val layoutDirection: LayoutDirection,
    val density: Float, val fontScale: Float, val blur: Float, val padding: Int, val recessed: Boolean
)

private data class ShadowMask(val image: ImageBitmap, val bytes: Int)

/** 同尺寸的设置行共用蒙版。按实际字节限制缓存，页面多也不会无限保留阴影资源。 */
private object ShadowMasks {
    val cache = object : android.util.LruCache<ShadowMaskKey, ShadowMask>(8 * 1024 * 1024) {
        override fun sizeOf(key: ShadowMaskKey, value: ShadowMask) = value.bytes
    }
}

private fun createShadowMask(
    path: Path, size: Size, scale: Float, padding: Int, blur: Float, recessed: Boolean
): ShadowMask {
    val width = ceil(size.width * scale).toInt() + padding * 2
    val height = ceil(size.height * scale).toInt() + padding * 2
    val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ALPHA_8)
    val card = android.graphics.Path(path.asAndroidPath()).apply {
        transform(android.graphics.Matrix().apply { setScale(scale, scale) })
        offset(padding.toFloat(), padding.toFloat())
    }
    val geometry = if (recessed) android.graphics.Path().apply {
        fillType = android.graphics.Path.FillType.EVEN_ODD
        addRect(-width.toFloat(), -height.toFloat(), width * 2f, height * 2f, android.graphics.Path.Direction.CW)
        addPath(card)
    } else card
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        maskFilter = android.graphics.BlurMaskFilter((blur * scale).coerceAtLeast(0.1f), android.graphics.BlurMaskFilter.Blur.NORMAL)
    }
    android.graphics.Canvas(bitmap).drawPath(geometry, paint)
    return ShadowMask(bitmap.asImageBitmap(), bitmap.allocationByteCount)
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
