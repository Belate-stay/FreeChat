package com.freechat.ui.animation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.*
import androidx.compose.animation.core.Spring.DampingRatioNoBouncy
import androidx.compose.animation.core.Spring.StiffnessMedium
import androidx.compose.animation.core.Spring.StiffnessMediumLow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.IntOffset

/**
 * 统一动画系统
 *
 * 设计原则：
 * - Spring 用自然物理感的手势交互（抽屉推拉、页面切换）
 * - Tween 用于入场/退场（消息气泡、组件显隐）
 * - 所有动画必须引用此文件中的规格，禁止硬编码数值
 * - 新增功能一律沿用此系统，保持视觉语言一致
 *
 * 层级：
 *   L1 页面级 → 300-400ms Spring，强调空间关系
 *   L2 组件级 → 200-300ms Tween，强调出现/消失
 *   L3 微交互 → 150-250ms Spring/Tween，强调反馈
 */
object FreeChatAnimation {

    // ==================== 时长标记 (ms) ====================
    const val DURATION_INSTANT = 100   // 瞬时反馈
    const val DURATION_FAST = 180      // 微交互：按钮按压、图标切换
    const val DURATION_NORMAL = 280    // 常规：气泡入场、卡片显隐
    const val DURATION_SLOW = 360      // 强调：页面过渡
    const val DURATION_PAGE = 350      // 页面切换

    // ==================== 缓动曲线 ====================
    val easeOut = FastOutSlowInEasing
    val easeEnter = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)    // 弹性入场
    val easeExit = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)      // 加速退场

    // ==================== iOS 手感曲线（1.0.49） ====================
    // 参考 UIKit / Core Animation 的默认缓动。人眼对「匀速」和「收尾撞墙」最敏感：
    // 位移类动画一律「起步轻—中段快—收尾稳」，入场用减速曲线（稳稳落位），
    // 退场用加速曲线（干脆走掉、别拖尾巴）。下面是三种基本形，别再各写各的三次贝塞尔。
    /** 慢—快—慢（对应 `CAMediaTimingFunction(0.42, 0, 0.58, 1)`）：位移/换位用它 */
    val iosEaseInOut = CubicBezierEasing(0.42f, 0.0f, 0.58f, 1.0f)
    /** 减速入位（对应 `CAMediaTimingFunction(0.25, 0.1, 0.25, 1)`）：淡入/展开/滑入用它 */
    val iosEaseOut = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1.0f)
    /** 加速离场（对应 `CAMediaTimingFunction(0.4, 0, 1, 1)`）：淡出/收起/滑出用它 */
    val iosEaseIn = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f)

    // ==================== 侧滑页「当前对话」竖条 ====================
    /**
     * 竖条换行滑行：慢—快—慢，190ms。
     * 距离可能横跨整屏（从列表头滑到列表尾），时长写死不用 Spring ——
     * 距离一长，Spring 会在收尾处拖出可见的物理尾巴，看着「弹」而不「稳」。
     * 190ms 是「要快」定的：点完对话是**马上要进 Chat 页**的，动画拖过 0.2s 就成了等待。
     * 慢—快—慢的曲线在这个时长里照样读得出来（起步轻、中段冲、收尾稳），
     * 长度上再叠「先拉长再缩小」（[DrawerIndicatorBar] 的绘制块）补足灵动感。
     */
    const val INDICATOR_SLIDE_MS = 190
    val indicatorSlideTween = tween<Float>(INDICATOR_SLIDE_MS, easing = iosEaseInOut)

    /**
     * 抽屉延时关闭的时长（= 竖条的滑行与抽屉起滑的**重叠**量）。
     *
     * 为什么不能立刻关：竖条贴在面板左缘（屏幕 x≈45，宽 12px），面板往左一滑 56px 它就被
     * 屏幕裁掉了；而关闭曲线 [drawerCloseTween] 是加速的（iosEaseIn），起步后 ~38ms 就能滑出
     * 70px —— 立刻关＝竖条只走到全程的百分之几就走了，动画等于没做。
     * 为什么也不能等它滑完：用户要的是「点了就进 Chat 页」，等 190ms 就是肉眼可见的卡顿。
     *
     * 取值就卡在中间：延时 + 38ms（出屏时刻）≈ 滑行时长的 72% 处 —— 抽屉起滑的那一瞬间
     * 竖条已经走完约九成，剩下的零头跟面板一起滑出屏幕左缘，看着是「交接」不是「消失」。
     * 两处必须同源，改一个就得改另一个（写死两个数字迟早对不上）。
     */
    const val INDICATOR_DRAWER_DELAY_MS = 100

    // ==================== 菜单 / 浮层卡片（全 App 一套） ====================
    /**
     * 菜单卡片入场：88% 放大到 100% + 淡入，200ms 减速入位。
     *
     * 三个参数都是「手感最贵」的位置，别在各处再各写一套：
     * - 起始缩放 0.88：比 0.9 更有「从锚点长出来」的感觉，但不会糊成一团；
     * - 200ms：再快像闪现，再慢像卡顿（人眼对 150–250ms 的缩放最不敏感于时长、最敏感于曲线）；
     * - 减速曲线：起步就快、收尾稳稳停住；用 Material 那条中段发飘的曲线，收尾会有一下「顿住」。
     */
    fun menuEnter(origin: TransformOrigin): EnterTransition =
        scaleIn(initialScale = 0.88f, transformOrigin = origin, animationSpec = tween(200, easing = iosEaseOut)) +
            fadeIn(tween(200, easing = iosEaseOut))

    /**
     * 菜单卡片退场：缩到 92% + 淡出，150ms 加速离场。
     * 退场一定要比入场快 —— 用户已经做完了决定，多留一毫秒都是拖沓。
     */
    fun menuExit(origin: TransformOrigin): ExitTransition =
        scaleOut(targetScale = 0.92f, transformOrigin = origin, animationSpec = tween(150, easing = iosEaseIn)) +
            fadeOut(tween(150, easing = iosEaseIn))

    // ==================== Tween 规格 ====================
    val fastTween = tween<Float>(DURATION_FAST, easing = easeOut)
    val normalTween = tween<Float>(DURATION_NORMAL, easing = easeOut)
    val slowTween = tween<Float>(DURATION_SLOW, easing = easeOut)
    val pageTween = tween<Float>(DURATION_PAGE, easing = easeOut)

    /** 消息气泡入场：垂直滑入 + 淡入 — iOS 风格从容入位 */
    val messageEnterTween = tween<Float>(
        durationMillis = 420,
        easing = CubicBezierEasing(0.17f, 0.84f, 0.22f, 1.0f)  // iOS style: quick start, gentle settle
    )
    /** 消息淡入 — 与滑动同步 */
    val messageFadeTween = tween<Float>(
        durationMillis = 360,
        easing = CubicBezierEasing(0.17f, 0.84f, 0.22f, 1.0f)
    )
    /** 打字指示器 — 圆点呼吸 */
    val dotBreathTween = tween<Float>(
        durationMillis = 420,
        easing = easeOut
    )
    /** 页面淡入 */
    val pageFadeIn = tween<Float>(DURATION_PAGE, easing = easeOut)
    /** 页面淡出 */
    val pageFadeOut = tween<Float>(DURATION_FAST, easing = easeExit)
    /** 遮罩淡入 */
    val scrimFadeIn = tween<Float>(280, easing = easeOut)
    /** 遮罩淡出 */
    val scrimFadeOut = tween<Float>(200, easing = easeExit)

    // — IntOffset 滑动规格（slideInHorizontally / slideInVertically）
    /** 页面水平滑动 */
    val pageSlide = tween<IntOffset>(DURATION_PAGE, easing = easeOut)
    /** 页面快速滑出 */
    val pageSlideOut = tween<IntOffset>(DURATION_FAST, easing = easeExit)
    /** 消息垂直入场 */
    val messageSlideIn = tween<IntOffset>(
        durationMillis = 260,
        easing = easeEnter
    )

    // ==================== Spring 规格 ====================

    /** 抽屉打开 — 用快速 Tween 代替 Spring，避免首次 Spring 计算的卡顿 */
    val drawerOpenSpring = tween<Float>(
        durationMillis = 280,
        easing = CubicBezierEasing(0.22f, 0.0f, 0.0f, 1.0f) // 模拟 Spring 手感但无物理计算
    )
    /**
     * 抽屉关闭 — 加速离场。
     * 原来是 150ms 的 Material 曲线：起步就慢，看着像「被人拽回去」。
     * 改成 220ms + 加速曲线：先轻轻动、越走越快，和 iOS 收起侧栏一个手感。
     * （抽屉上那根「当前对话」竖条的滑行 190ms 只比它早 [INDICATOR_DRAWER_DELAY_MS] 起跑，
     *   两者是**同时进行**的：竖条落位 ≈ 面板刚滑出竖条那点宽度，然后一起出屏）
     */
    val drawerCloseTween = tween<Float>(
        durationMillis = 220,
        easing = iosEaseIn
    )
    /** 页面切换 — 无弹跳 */
    val pageSpring = spring<Float>(
        dampingRatio = DampingRatioNoBouncy,
        stiffness = StiffnessMedium
    )
    /** 发送按钮 — 干脆的按压反馈 */
    val sendPressSpring = spring<Float>(
        dampingRatio = DampingRatioNoBouncy,
        stiffness = StiffnessMedium
    )
    /** 组件弹性入场（建议配合 scale/offset 使用） */
    val bounceIn = spring<Float>(
        dampingRatio = DampingRatioNoBouncy,
        stiffness = StiffnessMediumLow
    )

    // ==================== 常用动画常量 ====================
    /** 气泡初始上浮偏移比例（相对自身高度） */
    const val BUBBLE_RISE_FRACTION = 0.5f
    /** 抽屉手势触发阈值比例 */
    const val DRAWER_SWIPE_THRESHOLD = 0.2f
    /** 遮罩最大不透明度 */
    const val SCRIM_MAX_ALPHA = 0.4f
    /** 发送按钮按压缩放 */
    const val SEND_SCALE_PRESSED = 0.88f

    // ==================== 悬浮输入框动画 ====================
    /** 输入框滑出屏幕（向下隐藏） */
    val inputSlideOut = tween<IntOffset>(
        durationMillis = 200,
        easing = easeExit
    )
    /** 输入框滑入屏幕（向上出现） */
    val inputSlideIn = tween<IntOffset>(
        durationMillis = 280,
        easing = easeEnter
    )

    // ==================== 加载动画 ====================
    /** 非线性旋转 — 一会快一会慢，参考 Windows 启动动画 */
    val loadingRotation = tween<Float>(
        durationMillis = 1800,
        easing = CubicBezierEasing(0.42f, 0.0f, 0.58f, 1.0f)
    )
    /** 加载环缩放呼吸 */
    val loadingPulse = tween<Float>(
        durationMillis = 1200,
        easing = CubicBezierEasing(0.33f, 0.0f, 0.67f, 1.0f)
    )

    // ==================== 思考 / 加载动画 ====================
    /** 思考波浪流动周期 — 首尾无缝循环 */
    val thinkingWaveCycle = tween<Float>(
        durationMillis = 2400,
        easing = LinearEasing
    )
    /** 流体圆点轨道周期 — 一圈无缝循环 */
    val fluidOrbCycle = tween<Float>(
        durationMillis = 6000,
        easing = LinearEasing
    )

    // ==================== 图片全屏预览 ====================
    /** 图片从缩略图展开到全屏 — 非线性弹簧 */
    val imageExpandSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
    /**
     * 图片展开位置/尺寸动画 — 减速入位。
     * 原来用的是 (0.32, 0, 0.67, 0)：收尾速度为零点几，是「越到后面越快」的加速曲线，
     * 展开到位那一下像撞上去。iOS 放大图片是减速入位，这里改成 [iosEaseOut]。
     */
    val imageExpandTween = tween<Float>(
        durationMillis = 350,
        easing = iosEaseOut
    )
    /** 图片关闭缩回动画 — 略微加速 */
    val imageShrinkTween = tween<Float>(
        durationMillis = 280,
        easing = CubicBezierEasing(0.33f, 0.0f, 0.67f, 1.0f)
    )
    /** 图片预览缩放范围 */
    const val IMAGE_MIN_SCALE = 0.5f
    const val IMAGE_MAX_SCALE = 5f
    const val IMAGE_DOUBLE_TAP_SCALE = 2.5f

    // ==================== 设置页展开动画 ====================
    /** 设置页打开 — Tween 代替 Spring，首帧零计算 */
    val settingsExpandSpring = tween<Float>(
        durationMillis = 320,
        easing = CubicBezierEasing(0.22f, 0.0f, 0.0f, 1.0f)
    )
    /** 设置页缩回动画 */
    val settingsShrinkTween = tween<Float>(
        durationMillis = 250,
        easing = CubicBezierEasing(0.33f, 0.0f, 0.67f, 1.0f)
    )

    // ==================== 问候语键盘动画 ====================
    /** 问候语缩放 — 键盘弹出/收起 */
    val greetingScaleTween = tween<Float>(
        durationMillis = 280,
        easing = CubicBezierEasing(0.33f, 0.0f, 0.67f, 1.0f)
    )
    /** 问候语位置偏移 — 键盘弹出/收起 */
    val greetingBiasTween = tween<Float>(
        durationMillis = 300,
        easing = CubicBezierEasing(0.33f, 0.0f, 0.67f, 1.0f)
    )
    /** 问候语通用动画 — Bug 5: 统一平滑过渡 */
    val greetingKbTween = tween<Float>(
        durationMillis = 300,
        easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1.0f)
    )

    // ==================== 输入框键盘/滚动动画 ====================
    /** 输入框键盘回弹动画 — Bug 5: 平滑收回 */
    val inputKbTween = tween<Float>(
        durationMillis = 260,
        easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1.0f)
    )

    // ==================== 页面过渡动画优化 ====================
    /** 页面淡入 — Bug 5: 统一快速过渡 */
    val pageFadeInFast = tween<Float>(280, easing = FastOutSlowInEasing)
    /** 页面淡出 — Bug 5: 统一快速过渡 */
    val pageFadeOutFast = tween<Float>(200, easing = FastOutSlowInEasing)

    // ==================== 输入框滚动动画 ====================
    /** 输入框滑动速度映射 — 最小动画时长 */
    const val INPUT_SLIDE_MIN_MS = 80
    /** 输入框滑动速度映射 — 最大动画时长 */
    const val INPUT_SLIDE_MAX_MS = 300
    /** 输入框滑动速度灵敏度 */
    const val INPUT_VELOCITY_FACTOR = 0.4f

    // ==================== 语音输入动画 ====================
    /** 按住录音时输入框向右收起 — 非线性"灵动"曲线，参考 Gemini/DeepSeek 语音交互 */
    val voiceCollapseTween = tween<Float>(
        durationMillis = 280,
        easing = CubicBezierEasing(0.32f, 0.0f, 0.67f, 0.0f)
    )
    /** 波形条电平采样后的平滑过渡（消除电平跳变的毛刺） */
    val voiceLevelTween = tween<Float>(
        durationMillis = 90,
        easing = FastOutSlowInEasing
    )
}
