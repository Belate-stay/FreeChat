package com.freechat.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Beta 1.1.29.3 契约：①添加/导入卡片悬浮置顶+分页线撤+模糊带下延（同侧滑页固定卡片）；
 * ③内置备注两行化；④简介对话删思考过程。（②等非开源组件契约在 test/dev/，不随 push 上 GitHub。）
 */
class Beta11293Test {
    private fun source(path: String) = File("src/main/java/com/freechat/$path").readText()

    @Test fun poolHeaderFloatsOverContentUnderExtendedBlur() {
        val settings = source("ui/screens/SettingsScreen.kt")
        val editor = source("ui/screens/ModelEditorScreen.kt")
        assertTrue("模糊带下延到卡片下方（drawer 同款公式）",
            settings.contains("HazeSpec.drawerTopBandHeightDp(statusBarHeightDp, PoolHeaderBandDp)") &&
                editor.contains("HazeSpec.drawerTopBandHeightDp(statusBarHeightDp, ImportHeaderBandDp)"))
        assertTrue("「添加模型」画在模糊带之上覆盖滚动池", settings.contains("AddModelButton({ onOpenModelEditor(poolAddType, null) }, colors, s)"))
        assertTrue("导入卡片同款悬浮", editor.contains(".clickable { showImportHint = true }"))
        assertFalse("模型池分页线已撤", settings.contains("ModelListDivider"))
        assertFalse("导入卡分页线已撤", editor.contains("HorizontalDivider(color = colors.Divider.copy(alpha = 0.4f)"))
        assertTrue("池子头部留占位，内容从卡片下钻上去", settings.contains("Spacer(modifier = Modifier.height(64.dp))"))
        // 2026-10-19 返修钉：带子下延时 solidHeight 默认值会把实底板一起拉高（卡片区被实色盖死的根因），
        // 两页都必须显式钉回标题栏；悬浮卡走透光磨砂玻璃，不许实色卡面挡滚动内容。
        assertTrue("实心底板钉回标题栏（默认=带高-渐隐区会跟着下延长高）",
            settings.contains("solidHeight = HazeSpec.titleBarHeightDp(statusBarHeightDp)") &&
                editor.contains("solidHeight = HazeSpec.titleBarHeightDp(statusBarHeightDp)"))
        assertTrue("悬浮卡=透光不透物磨砂玻璃（floatingSurface）",
            editor.contains("Modifier.floatingSurface(hazeState, isDark, RoundedCornerShape(14.dp))") &&
                settings.contains("Modifier.floatingSurface(hazeState, isDark, RoundedCornerShape(14.dp))"))
        // 2026-10-19 返修二进：头部占位挂滚动区外=行被视口硬裁（「API Key」齐腰切断）、钻不进高斯渐变——
        // 占位必须在垂直滚动 Column 内部（模型池页同款结构）
        val scrollAt = editor.indexOf(".verticalScroll(com.freechat.ui.animation.rememberTopOnEntryScrollState())")
        val spacerAt = editor.indexOf("if (isAdd) {\n                Spacer(Modifier.height(60.dp))")
        assertTrue("添加页头部占位在滚动区内部", scrollAt in 1..spacerAt)
        // 2026-10-19 返修三进（图4→图5）：顶留白也必须在滚动区内——挂外层=视口缩在标题栏下，
        // 上滑内容被顶缘硬裁且进不了模糊带（顶部糊不出残影）；外层不许再带 top 留白。
        assertTrue("添加页顶留白在滚动区内（图5 顶部高斯残影）",
            editor.contains(".padding(top = if (isAdd) HazeSpec.topContentPaddingDp(statusBarHeightDp, 16.dp)") &&
                editor.indexOf("topContentPaddingDp(statusBarHeightDp, 16.dp)") > scrollAt)
        assertTrue("编辑页避开初始模糊带，添加页保留导入悬浮头布局",
            editor.contains("else HazeSpec.newPageContentPaddingDp(statusBarHeightDp, 16.dp), bottom = 40.dp)"))
    }

    @Test fun builtinNotesAreTwoLinesAndIntroHasNoThinking() {
        val modelDesc = source("ui/components/ModelDescText.kt")
        assertTrue("逗号前一句第一行（去尾逗号），红尾第二行",
            modelDesc.contains("trimEnd('，', ',', ' ')") && modelDesc.contains("append(\"\\n\")"))
        val msgs = com.google.gson.JsonParser.parseString(BuiltInIntro.MESSAGES_JSON).asJsonArray
        assertEquals(12, msgs.size())
        assertEquals("内置对话内容不含思考过程", 0, msgs.count {
            it.asJsonObject["reasoningContent"]?.asString?.isNotEmpty() == true
        })
    }
}
