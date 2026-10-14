package com.freechat.data

import androidx.compose.ui.graphics.Color
import com.freechat.ui.components.modelDescAnnotated
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Beta 1.1.28：默认值换代（老用户钉旧值/新装吃新值）、内置模型备注红尾、选中态动画的契约钉子。 */
class DefaultsAndModels1128Test {
    private fun source(path: String) = File("src/main/java/com/freechat/$path").readText()

    @Test fun defaultsMigrationIsVersionedAndPinsOnlyOldUsers() {
        val repo = source("data/SettingsRepository.kt")
        assertTrue("迁移按版本块跑，绝不重放老块的钉子", repo.contains("if (version < 2)") && repo.contains("if (version < 3)"))
        assertTrue("v3 给改动默认值的键钉**旧**默认值（延续已有设置）",
            repo.contains("mutable[KEY_SHOW_THINKING] = true") &&
                repo.contains("mutable[KEY_COLOR_THEME] = 2") &&
                repo.contains("mutable[KEY_INPUT_STYLE] = 0") &&
                repo.contains("mutable[KEY_HEADER_BAR_STYLE] = 0") &&
                repo.contains("mutable[KEY_SELECTED_VISUAL_MODEL] = \"ep-20260629143810-ffvjl\""))
        assertTrue("新装默认：松绿4/完整1/镂空1/显示思考关",
            repo.contains("DEFAULT_COLOR_THEME_ORDINAL = 4") &&
                repo.contains("DEFAULT_INPUT_STYLE_ORDINAL = 1") &&
                repo.contains("DEFAULT_HEADER_BAR_STYLE_ORDINAL = 1") &&
                repo.contains("DEFAULT_SHOW_THINKING = false"))
    }

    @Test fun builtinNotesShareOneTailAndRenderItRed() {
        val en = source("i18n/AppStrings.kt")
        val vm = source("viewmodel/ChatViewModel.kt")
        assertTrue("全内置模型尾句统一「作者自用 API，不保证随时在线」，无句号",
            en.contains("作者自用 API，不保证随时在线\"") && !en.contains("不保证随时在线。"))
        assertTrue("新内置识图/ASR 进池", vm.contains("builtInVisionModels") && vm.contains("builtInAsrModels"))
        assertTrue("音色设计/复刻不进模型池", vm.contains(".filter { it.id == BuiltInVoiceModel.STOCK_ID }"))
        // 尾句拆色 + 两行显示（1.1.29.3 ③：逗号前一句第一行去尾逗号，红尾第二行）
        val out = modelDescAnnotated("DeepSeek 快速推理模型，作者自用 API，不保证随时在线", Color.Black, Color.Red)
        assertEquals("DeepSeek 快速推理模型\n作者自用 API，不保证随时在线", out.text)
        assertTrue("尾句带红色 span", out.spanStyles.any { it.item.color == Color.Red && it.item.color.alpha == 1f })
        assertEquals(
            "DeepSeek 快速推理模型\n作者自用 API，不保证随时在线",
            com.freechat.ui.components.modelDescTwoLines("DeepSeek 快速推理模型，作者自用 API，不保证随时在线")
        )
    }

    @Test fun selectionColorsTransitionInsteadOfHardSwitch() {
        val sheet = source("ui/components/SheetPanel.kt")
        val frosted = source("ui/theme/Frosted.kt")
        val page = source("ui/animation/PageMotion.kt")
        assertTrue("SheetOption 选中态走 colorTween 过渡", sheet.contains("animateColorAsState") && sheet.contains("FreeChatAnimation.colorTween"))
        assertTrue("frostedCard 选中铺面同节奏", frosted.contains("animateColorAsState"))
        // 1.1.29：飞行期不再整卡降级（阴影闪一下才出来的元凶），只停流光采样 —— 阴影与稳态同链
        assertTrue("页面飞行期阴影常驻：只停流光（inheritLiquid），不换阴影链",
            page.contains("LocalPageFlight") &&
                frosted.contains("LocalPageFlight.current") &&
                frosted.contains("inheritLiquid = face == null && !selected && !flying") &&
                !frosted.contains("一律实色卡"))
    }
}
