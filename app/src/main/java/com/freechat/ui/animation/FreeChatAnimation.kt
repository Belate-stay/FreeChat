package com.freechat.ui.animation

import androidx.compose.animation.core.*
import androidx.compose.animation.core.Spring.DampingRatioNoBouncy
import androidx.compose.animation.core.Spring.StiffnessMedium
import androidx.compose.animation.core.Spring.StiffnessMediumLow
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
    /** 抽屉关闭 — 快速无后摇 */
    val drawerCloseTween = tween<Float>(
        durationMillis = 150,
        easing = FastOutSlowInEasing
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
    /** 图片展开位置/尺寸动画 — 缓出曲线 */
    val imageExpandTween = tween<Float>(
        durationMillis = 350,
        easing = CubicBezierEasing(0.32f, 0.0f, 0.67f, 0.0f)  // 强调加速感
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
