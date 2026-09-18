package com.freechat.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalWindowInfo
import com.freechat.model.ColorTheme
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

// ============================================================================
//  流动炫彩 —— 网页版背景的安卓原生移植
// ============================================================================
//
//  三层结构（与 Web 版 `__field` / `__glow` / `__bloom` 一一对应）：
//    L1 底色    不透明纯色，兜底 + 保证任何时刻画面都有确定的亮度基线
//    L2 场      158° 线性渐变（三停点），整幅缓慢漂移
//    L3 左柔光  左上 16% / 11% 的径向光，带一个「暖色桥接停点」
//    L4 右光晕  右下 78% / 88% 的径向光，负责「炫彩」的那口彩
//
//  为什么必须写这个文件而不是直接抄 Web 版 CSS：
//    1. CSS 的 `filter: blur()` / `mix-blend-mode` 在 Compose 里要么没有等价物，
//       要么需要离屏层 —— 那会让「每帧重录一次整幅背景」的开销变成几十毫秒。
//    2. CSS 的 `background-image` 是 GPU 在合成阶段算的，改 transform 不动像素；
//       Compose 里我们用「每帧只改 Brush 的 center/radius/端点」达到同等效果：
//       不重建 Modifier、不重组、不重排，只有绘制指令里的几个 float 在变。
//    3. **桥接停点**：Web 版踩过的坑。莫兰迪暖棕 ≈ 34°、莫兰迪浅蓝 ≈ 205°，两者在色环上
//       接近互补，sRGB 直接线性插值会从两端「掉」进中间的灰绿（hue 走短路），
//       看起来像一块脏抹布。所以中间额外插一个「亮而不灰」的暖白，把插值路径掰成明度轴。
//
//  性能：
//    · 动画相位来自 `withInfiniteAnimationFrameMillis`（全局动画时钟），
//      所以同一时刻「根背景 / 各页面的背景副本 / 侧滑页」算出的图案完全一致，
//      页面切换或抽屉滑出时背景不会「跳一下」。
//    · 所有 State 只在 `drawBehind` 的 lambda 里读 —— 每帧只重绘，不重组。
//    · 应用退到后台时 Compose 的帧时钟自动停摆，动画随之暂停，不空转耗电。
//    · 这是进阶选项，默认关闭；开启后以视觉效果优先（用户明确要求）。

/** 一次背景绘制所需的全部颜色。九套色板（棕/蓝/白 × 浅/深/纯黑）各一份，见文件末尾。 */
data class LiquidPalette(
    /** 不透明底色。与页面原 `colors.Background` 同明度，保证文字对比度不被削弱 */
    val base: Color,
    /** L2 线性渐变的三个停点（按 158° 方向依次经过） */
    val field: List<Color>,
    /** L3 左上柔光的三个停点：光源色 → 暖色桥接 → 透明 */
    val glow: List<Color>,
    /** L4 右下光晕的三个停点：光晕色 → 中间色 → 透明 */
    val bloom: List<Color>,
    /**
     * 侧滑页的罩色（半透明）。
     *
     * **这个值必须极淡（α≈0.07）**，只留一丝「这是一层抽屉」的暗示，不能让人看出色差。
     *
     * 侧滑页底下的流动背景与 Chat 页用的是同一幅、位置也对得上，本来严丝合缝；
     * 早先这里盖了 60% 不透明的罩色，于是抽屉与 Chat 页在接缝处成了两块颜色，
     * 而且罩色边缘就是一条直线，看起来像裂痕（用户的原话是「明显色差与裂痕」）。
     * 现在的做法是**几乎不留色**，靠 [frostedGlass] / 新拟态卡片自己的光影去分层，
     * 而不是靠给整块面板调色。
     *
     * 高级材质下完全不用它 —— 那时 LazyColumn 自己是模糊源，必须靠它自己画的副本。
     */
    val drawerVeil: Color,
)

/** 当前是否开启流动炫彩。为 true 时各页面不再铺不透明底色，改由根背景透上来。 */
val LocalLiquidMode = staticCompositionLocalOf { false }

/**
 * 全局动画时钟。由 MainActivity 建一次、往下传，全 App 的流动背景共用同一个帧回调 ——
 * 背景层数再多，每帧也只写一次 State、只多一个帧回调。
 */
val LocalLiquidClock = staticCompositionLocalOf<State<Long>?> { null }

/** 当前色板对应的流动炫彩配色（由 MainActivity 统一提供，避免各页面各算一套） */
val LocalLiquidPalette = staticCompositionLocalOf { liquidPalette(ColorTheme.BROWN, isDark = false, isOled = false) }

/**
 * 根内容区的矩形（**屏幕矩形**，px，root 坐标系）。由 MainActivity 在根 Box 上量一次提供。
 *
 * 为什么要有它：流动背景的几何（圆心、半径、渐变端点）全部按参照矩形的宽高算，
 * 所以「根背景」和「页面里画的副本」必须用**同一个**矩形，逐像素才对齐。
 * 让页面自己量自己是靠不住的 —— 标题栏只有一条，量出来会是标题栏的宽高，
 * 那就画成了一幅「按标题栏比例压缩过」的渐变，跟根背景完全是两幅画。
 */
val LocalLiquidFrame = staticCompositionLocalOf<State<Rect>?> { null }

private const val TAU = 6.2831855f

/** 把毫秒时钟折算成 0..1 的相位；[offset] 用来让同一幅画里的几层错开节奏 */
private fun phase(ms: Long, periodMs: Long, offset: Float = 0f): Float {
    val p = (ms % periodMs).toFloat() / periodMs.toFloat()
    return (p + offset) % 1f
}

/**
 * 全局动画时钟（毫秒）。同一个进程里所有实例读到的值在任意一帧都相同，
 * 因此「根背景」与「页面内的背景副本」永远对得上。
 */
@Composable
fun rememberLiquidClock(): State<Long> {
    val clock = remember { mutableLongStateOf(0L) }
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        // 系统「动画时长缩放」= 0 表示用户在系统层关了动画（无障碍/省电），这时不该硬转。
        // 自己读，不交给 withInfiniteAnimationFrameMillis —— 见下面的说明。
        val cr = context.contentResolver
        val scale = android.provider.Settings.Global.getFloat(
            cr, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        )
        if (scale <= 0f) return@LaunchedEffect
        // ⚠️ 这里**故意**不用 withInfiniteAnimationFrameMillis：
        // 它把整段交给系统那个 InfiniteAnimationPolicy，某些 ROM / 模拟器上那个策略会在启动后
        // 悄悄抛 CancellationException（只取消这一条协程，不打任何日志、不影响别的动画），
        // 时钟就永远停在第一帧 —— 背景一动不动，看着像「渐变太慢」，实则是绘制被冻住了。
        // 真机实测（2026-09-18，Pixel_10_Pro AVD）：同一条 Recomposer 上，
        // withFrameNanos 连续 20 秒稳定拿帧（40fps），withInfiniteAnimationFrameMillis
        // 第一帧之后 1.8 秒静默退出。裸 withFrameNanos 不受那个策略管，且应用切后台时
        // 帧时钟自己会停（Recomposer 的可暂停时钟），不会空转。
        while (true) {
            androidx.compose.runtime.withFrameNanos { ns -> clock.longValue = ns / 1_000_000 }
        }
    }
    return clock
}

/**
 * 把整幅流动背景画到当前 [DrawScope] 里。
 *
 * 纯函数式：不引用任何 Compose 状态，只吃 [ms]，所以既能被 `drawBehind` 每帧调用，
 * 也能被任何自绘控件复用。
 *
 * @param frame **几何参照系**（默认为本节点的大小）。整幅图案的圆心、半径、渐变端点
 *        全都是按这个矩形的宽高算的 —— **它必须处处相等，各处副本才能逐像素对上**。
 *        侧滑页那个 LazyColumn 只有 0.9 屏宽，如果按自己的宽度算，它画出来的就是一幅
 *        「按 0.9 倍压缩过的」渐变，跟根背景永远对不齐，接缝上就是两块颜色。
 *        所以调用方一律传**屏幕矩形**，图案按屏幕算，只是画的位置不同。
 */
fun DrawScope.drawLiquidField(
    palette: LiquidPalette,
    ms: Long,
    frame: Rect = Rect(0f, 0f, size.width, size.height)
) {
    val w = frame.width
    val h = frame.height
    if (w <= 0f || h <= 0f) return
    val ox = frame.left
    val oy = frame.top

    // L1 底色。铺满整个参照系（不是本节点）—— 平移过的副本也要把屏幕矩形盖严
    drawRect(color = palette.base, topLeft = Offset(ox, oy), size = Size(w, h))

    // L2 场：端点点在画面之外（外扩 28% 最大边），漂移量只有 5~8%，
    // 所以无论怎么漂，画面四角都不会露出「渐变还没开始」的空白
    val drift = phase(ms, 17_000L) * TAU
    val dx = sin(drift) * w * 0.08f
    val dy = cos(drift) * h * 0.065f
    val over = max(w, h) * 0.28f
    drawRect(
        brush = Brush.linearGradient(
            colors = palette.field,
            start = Offset(ox - over + dx, oy - over + dy),
            end = Offset(ox + w + over + dx, oy + h + over + dy),
        ),
        topLeft = Offset(ox, oy),
        size = Size(w, h)
    )

    // L3 左上柔光：11s 一圈，半径与圆心各自呼吸，避免「一个圆在原地打转」的机械感
    val g = phase(ms, 11_000L) * TAU
    drawRect(
        brush = Brush.radialGradient(
            colors = palette.glow,
            center = Offset(
                ox + w * 0.16f + sin(g) * w * 0.13f,
                oy + h * 0.11f + cos(g) * h * 0.10f,
            ),
            radius = w * (0.94f + 0.10f * sin(g * 1.3f)),
        ),
        topLeft = Offset(ox, oy),
        size = Size(w, h)
    )

    // L4 右下光晕：13s 一圈（与 L3 的 11s 互质，合成周期 11×13×17 ≈ 40 分钟，看不出重复）
    val b = phase(ms, 13_000L) * TAU
    drawRect(
        brush = Brush.radialGradient(
            colors = palette.bloom,
            center = Offset(
                ox + w * 0.78f + cos(b) * w * 0.15f,
                oy + h * 0.88f + sin(b) * h * 0.11f,
            ),
            radius = w * (0.88f + 0.12f * cos(b * 1.2f)),
        ),
        topLeft = Offset(ox, oy),
        size = Size(w, h)
    )
}

/**
 * 整幅流动背景。正常情况下**全局只该有一个实例** —— MainActivity 根 Box 里那个。
 * 它不参与任何位移，所以侧滑、翻页、抽屉拖拽时背景纹丝不动，所有页面都是隔着它看的。
 *
 * 高级材质下**也只有这一个实例**（1.0.50 起）：页面里的模糊源节点一律透明，
 * 不再各自画副本 —— 源节点的绘制会被 Haze 冻住，副本画得出来但不会动（见 [hazeBackground]）。
 */
@Composable
fun LiquidBackdrop(
    palette: LiquidPalette = LocalLiquidPalette.current,
    modifier: Modifier = Modifier,
    clock: State<Long>? = null,
) {
    val ms = clock ?: rememberLiquidClock()
    val frame = LocalLiquidFrame.current
    Box(modifier.fillMaxSize().drawBehind {
        // 参照系取 MainActivity 量的那个根矩形（与页面里的副本同源）；量不到时退回自己的大小
        // —— 本节点就是 fillMaxSize 的根背景，两者恒等。
        val f = frame?.value?.takeIf { it.width > 0f } ?: Rect(0f, 0f, size.width, size.height)
        drawLiquidField(palette, ms.value, frame = f)
    })
}

/**
 * 页面底色：不开流动炫彩就照旧铺不透明纯色；开了就**什么都不铺**。
 *
 * 为什么是「不铺」而不是「铺一份流动背景」——这是整套设计里最关键的一步：
 * 页面所在的层会被侧滑平移（Chat 层右移最多 90% 屏宽），如果流动背景是画在页面自己身上，
 * 它就会跟着页面一起被搬走，屏幕右缘那条 10% 宽的 Chat 残条会显示「渐变左端」，
 * 而它左边的侧滑页显示「渐变右端」——两段颜色不同，缝上会出现一条肉眼可见的色阶。
 *
 * 所以流动背景**只由根背景层画一次**（[LiquidBackdrop]，钉在屏幕上、不参与任何位移），
 * 所有页面一律透明，隔着它看。这样无论页面怎么平移、抽屉拖到几分之几，
 * 露出来的永远是同一幅、位置正确的图案。
 *
 * 顺带解决了另一件事：接缝羽化（[seamFeather]）要求缝两侧的像素都退回同一个背景色。
 * 现在缝两侧退回的是同一张根背景，比原来「同一个纯色」还更稳。
 */
@Composable
fun Modifier.pageBackground(base: Color): Modifier =
    if (LocalLiquidMode.current) this else this.background(base)

/**
 * 当前所在层的**实时平移量**（px）—— 不存数字，交出一个**现算的 lambda**：
 * 层自己把「怎么算」放到这里，层内的背景副本每次绘制时现调一次。
 *
 * ⚠️ **1.0.50 修的就是这个类原来的形态**（「一个可变盒子」）：
 * 早先是层在绘制块里把当前位移 `写进 var`、副本在绘制块里 `读 var`。
 * 写和读确实都在绘制期、不进组合（这一点是对的），但中间那个**普通可变字段是不可观测的** ——
 * Compose 不知道副本的绘制依赖它，于是副本录好的绘制指令（display list）**不会因为位移变化而失效**。
 * 症状：抽屉滑出再收回后，层的位置回到 0，副本却还停在滑出时的位移上 ——
 * 副本只盖住 [0, 1113) 这一段，屏幕其余部分露的是根背景，两者在 x=167（= 1280−1113）处
 * 拼出一条贯穿全高的竖向色阶；而且什么时候失效取决于「这一帧还有没有别的东西让它重录」，
 * 所以看起来是「偶尔有、偶尔没有、还会跳」。用户报的「图层空白 / 断层 / 乱跳」就是它。
 *
 * 现在改成 lambda：副本在绘制期读到的是**抽屉动画本身**（snapshot state），位移一变，
 * 副本那一格立刻被失效、下一帧用新值重画。同时层和副本从此**同源**（同一个表达式），
 * 不会出现「层按 A 算、副本按 B 算」。副作用是抽屉拖动时每帧多几次失效重录 —— 只涉及这几条
 * 背景带和副本身上的那一格，规模固定，与列表长度无关。
 *
 * ⚠️ 1.0.50 后期：页面里的副本已经全部拆掉（[hazeBackground] 现在什么都不画），
 * 于是这个类**当前没有消费者** —— 层仍然提供它，但没人读。
 *
 * **1.0.50 尾巴上的一条硬教训（别再踩）**：磨砂面衬底（[liquidOpaqueBackground]）一开始
 * 真的读了它、把位移从副本矩形里减掉，结果真机/模拟器上都出现「滑动时有一条跟着走的色差带」。
 * 原因：`onGloballyPositioned` 给的 `positionInRoot()` **本来就含**祖先 graphicsLayer 的平移，
 * 再减一次就是双重抵消 —— 副本整体偏左 L，节点右侧空出宽 L 的一条没有底。
 * **结论：在层里画「按屏幕坐标定位」的东西，直接信 `positionInRoot()` 就够了，不要另减位移。**
 * 留着这个类只是因为「层要告诉里面的东西自己挪了多少」这个需求本身没消失；
 * 真要清理时，记得一并删掉 MainActivity 里的两处 provide。
 */
class LiquidPageShift(val value: () -> Float)

/** 由会被平移的那几层（Chat 层 / 侧滑层）提供；不在层里的节点拿到 null，当作 0 处理。 */
val LocalLiquidPageShift = staticCompositionLocalOf<LiquidPageShift?> { null }

/**
 * 模糊源（haze source）节点的底色 —— **1.0.50 起在流动炫彩下是空的，什么都不画**。
 *
 * 曾经这里铺的是一份「流动背景副本」：高级材质的顶部磨砂带靠 `hazeSource` 把节点自己画的
 * 东西抓成位图再模糊，节点透明就抓不到背景，于是当初让每个源节点自己再画一遍流光。
 *
 * **为什么拆掉**：Haze 1.6.10 的 `HazeSourceNode` 把内容录进 `GraphicsLayer` 时套了一层
 * `Snapshot.withoutReadObservation`（字节码里能直接看到 `withoutReadObservation` 的内联标记）。
 * 后果是：**在源节点的绘制块里读 snapshot state，不会被登记成依赖** ——
 * 我们的动画时钟（`ms.value`）就在这个绘制块里读，于是时钟怎么走都不会让这个节点失效，
 * 录好的那一屏永远不重录。而屏幕上看到的恰恰就是这份录制结果，
 * 所以真机实测：高级材质关 → 20 秒 67 万像素在变；高级材质开 → 20 秒 **0** 像素在变，整屏冻死。
 * 这不是「渐变太慢」，是绘制被冻住了。
 *
 * 现在流动背景只由根背景层（[LiquidBackdrop]）画一次 —— 它在 hazeSource 之外、每帧现画、
 * 不参与任何位移，页面全部透明，隔着它看。背景重新动起来，顺带省掉「每帧把整屏渐变
 * 录进一个图层」这笔开销。
 *
 * 代价：顶部模糊带不再模糊**背景本身**，只模糊滚进来的内容。背景是平滑渐变，
 * 模糊过的和没模糊的肉眼没有差别；而内容（气泡、卡片）照样被模糊，磨砂手感不变。
 *
 * ⚠️ 由此得出一条**通用规矩**：任何靠绘制期读状态来做动画的东西（`drawBehind` 里读
 * `Animatable.value` / snapshot state），都不能放进 `hazeSource` 节点内部，否则会冻住。
 * 动画要么在组合期读值，要么放在源节点外面。
 */
@Composable
fun Modifier.hazeBackground(base: Color): Modifier {
    // 不开流动炫彩：照旧铺不透明底色（模糊带抓到的就是它，颜色对得上）
    if (!LocalLiquidMode.current) return this.background(base)
    return this
}

/**
 * **固定标题栏 / 悬浮条**的底色 —— 这些条必须**不透明**（正文滚上来要被挡住），
 * 而且开的又正是「页面全透明、背景由根层透上来」那一套，于是它们得自己画一份
 * **活着的、与根背景逐像素对齐的**流动背景。
 *
 * 为什么不能直接透过去：根背景画在**所有内容之下**。内容（正文、卡片）滚到标题栏背后，
 * 只要标题栏是透明的，就会从标题栏上直接穿出来 —— 像素叠像素，跟背景在不在动无关。
 * 所以这里必须真盖一层不透明的东西，而且它得**长得和根背景一模一样**，否则
 * 标题栏和它下面那条缝就是两块颜色（这正是 1.0.50 四组合自测里抓到的回归：
 * 流光炫彩开、高级材质关时，对话页标题栏透出正文，和「FreeChat」叠字）。
 *
 * 两个关键点：
 *  1. **坐标系**：[drawLiquidField] 的参照矩形必须还是**屏幕矩形**（[LocalLiquidFrame]）。
 *     本节点只有标题栏那么高，按自己的尺寸画就成了一幅压扁的渐变，接缝处一眼可见。
 *     做法是把参照矩形**反向平移到本节点的局部坐标系**里（`-origin`），
 *     于是画出来的就是「整幅背景在标题栏这一块上的裁片」。
 *  2. **层的位移**：不用管。本节点所在的层会被侧滑平移（Chat 层 / 侧滑层用 graphicsLayer 平移），
 *     而 `positionInRoot()` **已经把这个平移算进去了** —— 实测确认，早先「绘制期变换拿不到、
 *     要自己减 [LocalLiquidPageShift]」的说法是错的，照那样写反而双重抵消、滑出一条色差带。
 *
 * ⚠️ 只给「**确定不在 `hazeSource` 里**」的固定悬浮条用（各页标题栏都是内容节点的**兄弟**，
 * 不进模糊源）。放进源节点内部会和 [hazeBackground] 一样被冻住 —— 源节点录完那一屏就不再重录。
 * 实用判断：本 App 里标题栏都写在 `if (advancedMaterial) { …hazeEffect… } else { …这里… }`
 * 的 else 分支上，而页面的 hazeSource 只在 advancedMaterial 为真时才挂 —— 两者天然互斥。
 */
@Composable
fun Modifier.pageHeaderBackground(base: Color, overlay: Color = Color.Transparent): Modifier {
    // 不开流动炫彩：照旧铺不透明底色（就是原来那一套）
    if (!LocalLiquidMode.current) return this.background(base)
    return this.liquidOpaqueBackground(base, overlay)
}

/**
 * **流动炫彩下的一块不透明底色**：画一份与根背景逐像素对齐的流动背景副本。
 * 非炫彩时**什么都不画**（调用方自己铺底色，别拿它当 `background()` 用）。
 *
 * 和 [pageHeaderBackground] 是同一份实现，区别只在「非炫彩下要不要兜底铺纯色」：
 * 给**玻璃/磨砂材质**当衬底时不能兜底 —— 那些地方本来就没底色，凭空多一层实色会盖住
 * 不该盖的东西（非炫彩下模糊源本来就是不透明的，压根不需要衬底）。
 *
 * ## 为什么玻璃下面必须垫一层（1.0.50 修「哑光磨砂玻璃变成半透明 PPT 图层」）
 *
 * Haze 的模糊是「把**源节点那一层**抓出来磨一下、再盖回效果节点上」。所以：
 * **效果节点能不能挡住它下面的内容，取决于抓到的样本本身是不是不透明的。**
 * 开了流动炫彩之后页面一律透明（见 [pageBackground]），源节点也是透明的 ——
 * 于是抓到的样本只有字和卡片这几笔，四周全是透明像素，磨完之后盖下来等于什么都盖不住：
 * 底下的正文原样透出来，磨砂玻璃就成了一块半透明的 PPT 图层，底部那条渐变模糊带
 * （它连罩色都没有，`backgroundColor` 是全透明）更是整条消失。
 *
 * 非炫彩下没这个问题：源节点自己铺了一层不透明底色（[hazeBackground] 的 else 分支），
 * 样本是实的，模糊带/玻璃都挡得住 —— 这就是「四组合里只有炫彩那一档坏」的原因。
 *
 * 修法不是去动源节点（源节点里不能画会动的东西，[hazeBackground] 那条冻死的教训），
 * 而是**每个「要挡住内容」的磨砂面自己垫一层不透明底色**：这层垫在模糊之下，
 * 于是合成结果是「不透明的背景 + 磨掉的内容」—— 和非炫彩下抓到的那份样本**长得一模一样**，
 * 观感完全对齐，而它画在效果节点上（源节点的兄弟），不在源里面，不会被冻住。
 *
 * @param fadeEndPx >0 = 只在下半段渐显：从本节点顶部（透明）到 fadeEndPx（全不透明）线性过渡。
 *   底部模糊带用这个 —— Haze 的 progressive 会让带上缘的模糊强度归零（内容照常看得见），
 *   衬底要是整块不透明，内容一进带子就被硬切一刀，那条渐变带就成了一块实心板。
 */
@Composable
fun Modifier.liquidOpaqueBackground(
    base: Color,
    overlay: Color = Color.Transparent,
    fadeEndPx: Float = 0f,
): Modifier {
    if (!LocalLiquidMode.current) return this
    val palette = LocalLiquidPalette.current
    val clock = LocalLiquidClock.current
    val frameState = LocalLiquidFrame.current
    // ⚠️ 这几个 CompositionLocal 必须在**组合期**取出来（`current` 是 @Composable 取值，
    //    放进绘制块里编译不过）
    // 本节点在 root 里的位置。**含**祖先层的 graphicsLayer 平移（实测，见下面 drawBehind 里的说明）
    val origin = remember { mutableStateOf(Offset.Zero) }
    return this
        .onGloballyPositioned { origin.value = it.positionInRoot() }
        // ⚠️ 必须裁到自己这一条里！`drawBehind` 画到节点外**不会被自动裁掉**，而下面那份
        //    流动背景是按屏幕矩形铺的（底色那一层就是整屏 1440×3200）——
        //    标题栏从屏幕左上角开始，于是整屏背景就从它这儿又画了一遍，把**所有先画的内容
        //    全盖住**（页面看起来只剩背景和标题）。1.0.50 四组合自测就是这么撞上的：
        //    流光炫彩开、高级材质关时，设置页/对话页内容整片消失。
        .clipToBounds()
        .drawBehind {
            val frame = frameState?.value
            if (frame == null || frame.width <= 0f) {
                // 根矩形还没量到（理论上只有第一帧）：宁可铺一层纯底色，也不能透
                drawRect(color = base)
                return@drawBehind
            }
            val o = origin.value
            // 整块现画一份（副本是「屏幕矩形搬到本节点坐标系里」，见文件头）
            //
            // ⚠️ 这里**不能**再减 `LocalLiquidPageShift` 的位移 —— 1.0.50 后期实测结论：
            //    `onGloballyPositioned` 给的 `positionInRoot()` **已经包含**了祖先 `graphicsLayer`
            //    的绘制期平移（Chat 层右移 / 侧滑层左移都算在里面），再减一次就成了双重抵消：
            //    副本被整体推左 L 像素，节点右侧空出**宽 L 的一条**没有不透明底 ——
            //    那条带子跟着滑动一起动、和旁边差十几个色阶，就是用户说的
            //    「滑动过程中有图层在跟随滑动，而且色差明显」。
            //    实测（Pixel_10_Pro，1280 宽）：抽屉偏移 L≈470 时缺口从 x=810（=1280−L）开始，
            //    另一帧 L≈310 时缺口从 960 开始 —— 缺口宽度恒等于 L，正是双重抵消的特征。
            //    所以 `LocalLiquidPageShift` 在当前实现下**不需要**，也就没有消费者了。
            val paintField = {
                drawLiquidField(
                    palette = palette,
                    ms = clock?.value ?: 0L,
                    frame = Rect(
                        left = -o.x,
                        top = -o.y,
                        right = -o.x + frame.width,
                        bottom = -o.y + frame.height,
                    )
                )
                // 罩色：侧滑页整块面板上有一层极淡的罩色（drawerVeil，α≈0.07），
                // 标题带要跟它下面的面板同色，就得把同一层罩色也叠上，
                // 否则带的边界会显出一条极淡的横线（用户对「接缝/色差」很敏感）。
                if (overlay.alpha > 0f) drawRect(color = overlay)
            }
            if (fadeEndPx <= 0f) {
                paintField()
            } else {
                // 渐显版：把整块底色画进一个离屏层，再用「上透明→下不透明」的竖向蒙版
                // 抠掉上半段（DstIn）。曲线与 Haze 的 progressive 同形（同为线性），
                // 于是「内容被吃掉」和「模糊压上来」是同一条斜率，看不出两层。
                drawIntoCanvas { canvas ->
                    canvas.saveLayer(Rect(Offset.Zero, size), Paint())
                    paintField()
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black),
                            startY = 0f,
                            endY = fadeEndPx,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                    canvas.restore()
                }
            }
        }
}

/**
 * **底部渐进模糊带的「采样垫底」** —— 一块与根背景逐像素对齐的不透明流光副本，
 * 垫在页面内容源节点**下面**，并且自己也注册成同一个 haze 状态的源（`zIndex` 更小 = 合成时更靠下）。
 *
 * ## 为什么 [liquidOpaqueBackground] 那种衬底救不了这条带子
 *
 * 衬底能救磨砂玻璃、标题栏 —— 那些面要的是「一整块不透明贴片把下面的内容盖住」。
 * 但底部这条带子是 `backgroundColor = 全透明` 的**纯渐进模糊**，它要的是
 * 「下面的正文被糊掉、看不清」，而不是「被一块底色盖住」：
 * Haze 磨出来的那层依旧是半透明的（炫彩下源里只有字的笔画、没有底），盖下来挡不住下面那份
 * **清晰**的正文 —— 两者一叠，用户看到的就是「文字加粗加浓像墨汁晕开、但内容还是可读」，
 * 而不是非炫彩下那种「糊掉、读不出来」。
 *
 * ## 修法：让样本自己带上底
 *
 * Haze 磨的是**源节点那一层**。非炫彩下源节点自己铺了不透明底色（[hazeBackground] 的 else 分支），
 * 样本是实的，同一套参数糊出来就是对的；炫彩下页面全透明，样本就只剩几笔字。
 * 所以这里把那层「底」补给炫彩模式：这块小节点画一份不透明流光副本，作为
 * **第二个源**一起被磨 —— 磨出来的层变成不透明的「流光底 + 正文」，
 * 与下面那份清晰正文一叠就把它**替换**掉了，观感与非炫彩对齐。
 *
 * 三个关键点：
 *  1. **合成顺序**：Haze 1.6.10 把同一状态里的多个源按 `zIndex` 升序合成
 *     （字节码里能直接看到 `sortBy { it.zIndex }`，且全程没有 reversed/downTo），
 *     小的先画 = 垫在底下。所以调用处必须传 `zIndex = -1f`（页面内容源用的是默认 0）——
 *     否则副本会盖在字上面，整条带子变成一块实心板。
 *  2. **必须是内容节点的兄弟、且画在它前面**：源只抓自己那一层，所以它不会污染内容源的样本；
 *     而它画的就是「背景」，本就该在内容底下。
 *  3. **每帧都得重画**：副本比根背景慢一帧，边界就是一条色带。但源节点的绘制块被 Haze 套了
 *     `Snapshot.withoutReadObservation`，在里面读快照状态**登记不上依赖**
 *     （[hazeBackground] 那条「整屏冻死」的教训）。所以这里不能用 `drawBehind`，
 *     要用 `drawWithCache`：它的构造块走的是**显式观察**（`observeReads`），
 *     能穿透那层 withoutReadObservation —— 时钟一变就作废重算、重录。
 */
@Composable
fun Modifier.liquidSourceBackdrop(
    base: Color,
    overlay: Color = Color.Transparent,
): Modifier {
    if (!LocalLiquidMode.current) return this
    val palette = LocalLiquidPalette.current
    val clock = LocalLiquidClock.current
    val frameState = LocalLiquidFrame.current
    val origin = remember { mutableStateOf(Offset.Zero) }
    return this
        .onGloballyPositioned { origin.value = it.positionInRoot() }
        // 与 liquidOpaqueBackground 同理：节点外的部分不会被自动裁掉，必须自己裁
        .clipToBounds()
        .drawWithCache {
            // 这三个读数都放在构造块里（= 绘制期），走显式观察 → 每帧作废、重录
            val ms = clock?.value ?: 0L
            val frame = frameState?.value
            val o = origin.value
            onDrawBehind {
                if (frame == null || frame.width <= 0f) {
                    drawRect(color = base)
                    return@onDrawBehind
                }
                drawLiquidField(
                    palette = palette,
                    ms = ms,
                    frame = Rect(
                        left = -o.x,
                        top = -o.y,
                        right = -o.x + frame.width,
                        bottom = -o.y + frame.height,
                    )
                )
                if (overlay.alpha > 0f) drawRect(color = overlay)
            }
        }
}

// ============================================================================
//  九套色板
// ============================================================================
//
//  取色原则：
//   1. 整幅背景的**明度**必须钉在原来那个 `Background` 附近（浅色主题 ±6%、深色主题 ±4%）。
//      页面上的正文、时间分割线、空态问候语都是直接趴在背景上的，明度一飘对比度就崩。
//   2. 彩度只给到「看得出来有颜色」的程度，不做霓虹。用户要的是自然舒适美观，不是赛博朋克。
//   3. 每个光晕的第二个停点是**桥接色**：它必须亮，不能灰。见文件头第 3 条。

fun liquidPalette(colorTheme: ColorTheme, isDark: Boolean, isOled: Boolean): LiquidPalette =
    when (colorTheme) {
        ColorTheme.BROWN -> when {
            isOled -> BrownOledLiquid
            isDark -> BrownDarkLiquid
            else -> BrownLightLiquid
        }
        ColorTheme.BLUE -> when {
            isOled -> BlueOledLiquid
            isDark -> BlueDarkLiquid
            else -> BlueLightLiquid
        }
        ColorTheme.WHITE -> when {
            isOled -> WhiteOledLiquid
            isDark -> WhiteDarkLiquid
            else -> WhiteLightLiquid
        }
    }

// ---- 莫兰迪暖棕 ----

/** 暖棕·浅：奶油底 + 蜂蜜光（左上）+ 玫瑰光（右下）。整体比原底色暖半档，不脏。 */
private val BrownLightLiquid = LiquidPalette(
    base = Color(0xFFF7F2EC),
    field = listOf(Color(0xFFFCF8F2), Color(0xFFF6EDE3), Color(0xFFF1E7DC)),
    glow = listOf(Color(0xFFFFF0D2), Color(0xFFFBE4D8), Color(0x00FBE4D8)),
    bloom = listOf(Color(0xFFF2DCE6), Color(0xFFF6E8DC), Color(0x00F6E8DC)),
    drawerVeil = Color(0x12EDE4DB),
)

/** 暖棕·深：底色不动，两块光把暗部推开一点，避免大屏纯暗显闷。 */
private val BrownDarkLiquid = LiquidPalette(
    base = Color(0xFF1C1A17),
    field = listOf(Color(0xFF232019), Color(0xFF1C1916), Color(0xFF16130F)),
    glow = listOf(Color(0x6B4A3A1F), Color(0x3D4A3A2A), Color(0x004A3A2A)),
    bloom = listOf(Color(0x5C3A2130), Color(0x333A2A28), Color(0x003A2A28)),
    drawerVeil = Color(0x1A24211D),
)

/** 暖棕·纯黑：OLED 上大面积发光会毁掉「纯黑省电」的意义，所以光压到最低，只留一丝气息。 */
private val BrownOledLiquid = LiquidPalette(
    base = Color(0xFF040404),
    field = listOf(Color(0xFF0C0B09), Color(0xFF070706), Color(0xFF000000)),
    glow = listOf(Color(0x4A2E2415), Color(0x242E2415), Color(0x002E2415)),
    bloom = listOf(Color(0x3D241423), Color(0x1F241423), Color(0x00241423)),
    drawerVeil = Color(0x1A0D0D0D),
)

// ---- 莫兰迪浅蓝 ----

/** 浅蓝·浅：雾底 + 天光（左上）+ 丁香紫（右下）。冷色主题给一点互补的暖紫才不死板。 */
private val BlueLightLiquid = LiquidPalette(
    base = Color(0xFFEFF3F7),
    field = listOf(Color(0xFFF8FBFD), Color(0xFFEBF1F7), Color(0xFFE3EBF3)),
    glow = listOf(Color(0xFFD9ECFF), Color(0xFFE6EFF8), Color(0x00E6EFF8)),
    bloom = listOf(Color(0xFFE4DEFB), Color(0xFFE9E9F6), Color(0x00E9E9F6)),
    drawerVeil = Color(0x12E5EBF0),
)

private val BlueDarkLiquid = LiquidPalette(
    base = Color(0xFF191C21),
    field = listOf(Color(0xFF1F242B), Color(0xFF191D22), Color(0xFF131619)),
    glow = listOf(Color(0x7A1E3A55), Color(0x40243646), Color(0x00243646)),
    bloom = listOf(Color(0x6B2B2340), Color(0x332B2A3A), Color(0x002B2A3A)),
    drawerVeil = Color(0x1A1E2228),
)

private val BlueOledLiquid = LiquidPalette(
    base = Color(0xFF030405),
    field = listOf(Color(0xFF0A0D11), Color(0xFF050709), Color(0xFF000000)),
    glow = listOf(Color(0x4A16304A), Color(0x2416304A), Color(0x0016304A)),
    bloom = listOf(Color(0x3D1F1A33), Color(0x1F1F1A33), Color(0x001F1A33)),
    drawerVeil = Color(0x1A0D0D0D),
)

// ---- 纯白 ----

/** 纯白·浅：纸底 + 极淡的天蓝与藕荷，比原纯白「有空气感」，仍然干净。 */
private val WhiteLightLiquid = LiquidPalette(
    base = Color(0xFFFCFCFD),
    field = listOf(Color(0xFFFFFFFF), Color(0xFFF8F9FC), Color(0xFFF3F5FA)),
    glow = listOf(Color(0xFFE3EFFF), Color(0xFFEFF4FC), Color(0x00EFF4FC)),
    bloom = listOf(Color(0xFFEFE8FC), Color(0xFFF3F1FB), Color(0x00F3F1FB)),
    drawerVeil = Color(0x12F5F5F5),
)

private val WhiteDarkLiquid = LiquidPalette(
    base = Color(0xFF1B1B1C),
    field = listOf(Color(0xFF212124), Color(0xFF1B1B1D), Color(0xFF151516)),
    glow = listOf(Color(0x6B2A303C), Color(0x332A303C), Color(0x002A303C)),
    bloom = listOf(Color(0x5C2C2733), Color(0x2E2C2733), Color(0x002C2733)),
    drawerVeil = Color(0x1A242424),
)

private val WhiteOledLiquid = LiquidPalette(
    base = Color(0xFF000000),
    field = listOf(Color(0xFF0A0A0A), Color(0xFF050505), Color(0xFF000000)),
    glow = listOf(Color(0x3D22262E), Color(0x1F22262E), Color(0x0022262E)),
    bloom = listOf(Color(0x33241F29), Color(0x1A241F29), Color(0x00241F29)),
    drawerVeil = Color(0x1A0D0D0D),
)
