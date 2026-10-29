package com.freechat.ui.theme

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class InputShape1224SourceTest {
    private val main = File("src/main/java/com/freechat")
    private fun source(path: String) = File(main, path).readText()

    @Test fun standardInputsShareOneShapeAcrossPagesAndSheets() {
        val fields = Regex("\\b(?:Outlined)?TextField\\(")
        val sharedShapes = Regex("\\bshape\\s*=\\s*FreeChatInputShape\\b")
        val inconsistent = main.walkTopDown().filter { it.extension == "kt" }.mapNotNull { file ->
            val text = file.readText()
            val count = fields.findAll(text).count()
            if (count == 0 || sharedShapes.findAll(text).count() == count) null else file.relativeTo(main).path
        }.toList()
        assertTrue("Every standard text field must use FreeChatInputShape: $inconsistent", inconsistent.isEmpty())
        assertTrue(source("ui/theme/Shape.kt").contains("val FreeChatInputShape = RoundedCornerShape(16.dp)"))
    }

    @Test fun customSearchAndVerificationInputsUseTheSameOutline() {
        val drawerSearch = source("ui/components/DrawerContent.kt").substringAfter("private fun SearchBox(").substringBefore("private fun NewChatButton(")
        assertTrue(drawerSearch.contains(".floatingSurface(hazeState, isDark, FreeChatInputShape,"))
        val verification = source("ui/components/WechatBindDialog.kt").substringAfter("BasicTextField(").substringBefore("TextButton(")
        assertTrue(verification.contains(".clip(FreeChatInputShape)"))
        assertTrue(source("ui/screens/ModelEditorScreen.kt").contains("Modifier.editorSurface(colors, FreeChatInputShape, recessed = true)"))
    }

    @Test fun chatComposerRetainsItsOwnCapsuleShape() {
        val composer = source("ui/components/ChatInput.kt")
        assertFalse(composer.contains("FreeChatInputShape"))
        assertEquals(3, Regex("\\bBasicTextField\\(").findAll(composer).count())
    }
}
