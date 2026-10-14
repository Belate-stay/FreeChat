package com.freechat.ui.animation

import androidx.compose.animation.core.animate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 果冻边界冗余（1.1.26.6 方向返修）：列表滑到顶/底后继续拖，页面**跟着手指方向**带阻尼位移
 * ——上拽到底部→页面上移、下方见空白；下拽到顶部→页面下移、上方见空白；松手弹回固定位置。
 * 对齐微信聊天页/手机系统设置页的通用做法。
 *
 * **符号纪律（实测定案，别再凭直觉翻）**：本机 nested scroll 的 available.y **跟手指同向**
 * （>0=下拽、<0=上拽）——依据 ChatScreen 手势检测 `available.y > 0 && canScrollBackward`
 * （=往回翻=下拽）的实机正确行为。1.1.26.1 的果冻按相反假设写，整体方向就是那时反掉的。
 *
 * 接线：挂在**滚动容器外层**（`Modifier.jellyOverscroll().verticalScroll(...)`）；
 * 内容不溢出时剩余位移全数喂进来，效果照常。Chat 聊天页**不挂**（用户点名保持不动）。
 */
internal object JellyPullPolicy {
    /**
     * 橡皮筋映射：手指位移 raw(px) → 页面位移 (px)。tanh 形——小幅 1:1 跟手、
     * 大幅渐近 dimensionPx 封顶不撞墙；奇函数对称，回弹过冲平滑穿过零点。
     */
    fun offset(rawPx: Float, dimensionPx: Float): Float {
        val d = dimensionPx.coerceAtLeast(1f)
        return d * kotlin.math.tanh(rawPx / d)
    }
}

/**
 * 页面级果冻边界冗余（双向）。[enabled] 门控；系统减动效时整个效果关闭。
 *
 * [offsetPx] 可选读数：与 graphicsLayer 的 translationY 同值同帧回写（1.1.26.9）——
 * 浮在滚动容器外、却要跟内容对齐的浮层（侧滑页「当前对话」竖条）补同一份位移才不掉队。
 */
@Composable
fun Modifier.jellyOverscroll(
    enabled: Boolean = true,
    offsetPx: androidx.compose.runtime.MutableFloatState = remember { mutableFloatStateOf(0f) }
): Modifier {
    if (!enabled || !LocalMotionEnabled.current) return this
    val scope = rememberCoroutineScope()
    val dimensionPx = with(LocalDensity.current) { 260.dp.toPx() }
    var raw by remember { mutableFloatStateOf(0f) }
    val connection = remember(scope, dimensionPx) {
        object : NestedScrollConnection {
            private var settleJob: Job? = null

            private fun settle() {
                settleJob?.cancel()
                settleJob = scope.launch {
                    animate(initialValue = raw, targetValue = 0f,
                        animationSpec = FreeChatAnimation.jellyReturn) { value, _ -> raw = value }
                }
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                // 断触修复（1.1.26.8）：手指一动就叫停回弹 spring——否则 settle 每帧覆写 raw
                // 与手指抢值，快速反复滑动时吃掉的位移被旧帧回填，列表滚不动、突然停。
                if (raw != 0f) settleJob?.cancel()
                // 拉伸中途反向拖动：先收回拉出的量（吃掉的量带上 available 的符号），再轮到列表
                if (raw != 0f && available.y * raw < 0f) {
                    val maxEat = minOf(abs(available.y), abs(raw))
                    val eat = if (available.y > 0f) maxEat else -maxEat
                    raw += eat
                    return Offset(0f, eat)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // 到顶/到底继续拖的剩余量：吃掉，转成跟手位移（raw 与手指同向）
                if (source == NestedScrollSource.UserInput && available.y != 0f) {
                    settleJob?.cancel()
                    raw += available.y
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                // 断触修复（1.1.26.8）：吞 fling 会让反复快滑时列表突然停住。
                // 回弹和列表惯性并行——页面 spring 归位，列表 fling 照常走，互不抢。
                if (raw != 0f) settle()
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (raw != 0f) settle()
                return Velocity.Zero
            }
        }
    }
    // raw 与手指同向：下拽 raw>0 → 页面下移；上拽 raw<0 → 页面上移——跟手不反向
    return nestedScroll(connection).graphicsLayer {
        val y = JellyPullPolicy.offset(raw, dimensionPx)
        // 读数回写同值（结构相等不触发重绘）；浮层在布局期读它跟位移
        offsetPx.floatValue = y
        translationY = y
    }
}
