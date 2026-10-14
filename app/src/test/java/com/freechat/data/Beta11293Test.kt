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
