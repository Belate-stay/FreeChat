package com.freechat.data

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.freechat.i18n.buildStrings
import com.freechat.ui.components.MdBlock
import com.freechat.ui.components.parseMarkdown
import com.freechat.util.shareStyledLine
import org.junit.Assert.*
import org.junit.Test

class Beta98RegressionTest {
    @Test fun savingSimulationCannotOverwriteLockedPersonaOrItsLearnedPrompt() {
        val original = com.freechat.model.CharacterProfile(name = " 原角色 ", gender = "女", age = "20",
            personaPrompt = "已有的学习结果", relationshipText = "用户的关系原文",
            originalLearningText = "小说原文", openingLine = "旧开场白", mbtiType = "INTJ",
            avatarHash = "reference-hash", appearanceImagePath = "legacy-reference")
        val draft = original.copy(name = "误改的角色", personaPrompt = "不应写入",
            relationshipText = "不应覆盖", originalLearningText = "不应覆盖", mbtiType = "",
            replyBufferEnabled = false, deepThinkingMode = false, visualModelId = "image-model")
        val saved = CharacterPresentationPolicy.withSimulationSettings(original, draft)
        assertEquals(original.copy(replyBufferEnabled = false, deepThinkingMode = false, visualModelId = "image-model"), saved)
    }

    @Test fun auroraIsOptInButExistingExplicitChoicesSurviveUpgrades() {
        assertFalse(SettingsPresentationPolicy.liquidBackdrop(null))
        assertFalse(SettingsPresentationPolicy.liquidBackdrop(false))
        assertTrue(SettingsPresentationPolicy.liquidBackdrop(true))
    }

    @Test fun reasoningChildAndItsEffectFollowTheParentWithoutErasingThePreference() {
        assertFalse(SettingsPresentationPolicy.deepThinkingChildren(false, true))
        assertFalse(SettingsPresentationPolicy.deepThinkingChildren(true, false))
        assertTrue(SettingsPresentationPolicy.deepThinkingChildren(true, true))
        val savedPreference = true
        assertFalse(SettingsPresentationPolicy.reasoningVisible(savedPreference, false, true))
        assertTrue(SettingsPresentationPolicy.reasoningVisible(savedPreference, true, true))
        assertFalse(SettingsPresentationPolicy.reasoningVisible(savedPreference, true, false))
        assertFalse(SettingsPresentationPolicy.reasoningVisible(false, true, true))
    }

    @Test fun shareImageRecursivelyRendersUnderlineInsideBoldInsteadOfLeakingDelimiters() {
        val line = shareStyledLine("开头 **粗体 ++下划线++ 结束**。")
        assertEquals("开头 粗体 下划线 结束。", line.text)
        val start = line.text.indexOf("下划线")
        assertTrue(line.spanStyles.any { it.start <= start && it.end >= start + 3 &&
            it.item.textDecoration?.contains(TextDecoration.Underline) == true })
        assertTrue(line.spanStyles.any { it.start <= start && it.end >= start + 3 && it.item.fontWeight == FontWeight.Bold })
    }

    @Test fun nestedItalicStrikeAndHtmlUnderlineAreRenderedWithoutMarkers() {
        val line = shareStyledLine("**粗体 *斜体* ~~删除~~ <u>下划线</u>**")
        assertEquals("粗体 斜体 删除 下划线", line.text)
        assertTrue(line.spanStyles.any { it.item.textDecoration?.contains(TextDecoration.LineThrough) == true })
        assertTrue(line.spanStyles.any { it.item.textDecoration?.contains(TextDecoration.Underline) == true })
    }

    @Test fun inlineCodePreservesRealPlusSignsAndMarkdownSymbols() {
        val line = shareStyledLine("示例 `i++; **raw** ++raw++` 后文")
        assertEquals("示例 i++; **raw** ++raw++ 后文", line.text)
        assertTrue(line.spanStyles.any { it.item.fontFamily == FontFamily.Monospace })
        assertEquals("C++ 与 a+b", shareStyledLine("C++ 与 a+b").text)
    }

    @Test fun fencedCodeStillBypassesInlineRenderingEntirely() {
        val blocks = parseMarkdown("```cpp\ni++; // ++literal++\n```")
        assertEquals("i++; // ++literal++", (blocks.single() as MdBlock.CodeBlock).code)
    }

    @Test fun formattedLinksHaveLabelsAndPreserveCompleteUrls() {
        val line = shareStyledLine("访问 [**++官网++**](https://example.com/a_(b))")
        assertEquals("访问 官网", line.text)
        assertEquals(1, line.getLinkAnnotations(0, line.length).size)
        assertEquals("https://example.com/a_(b)",
            (line.getLinkAnnotations(0, line.length).single().item as androidx.compose.ui.text.LinkAnnotation.Url).url)
        assertEquals("https://example.com/a_b", shareStyledLine("https://example.com/a_b").text)
    }

    @Test fun headingAndTableCellsUseTheSameInlineResultsAsParagraphs() {
        val heading = parseMarkdown("## **++标题++**").single() as MdBlock.Heading
        assertEquals("标题", shareStyledLine(heading.text).text)
        val table = parseMarkdown("| **++表头++** |\n| --- |\n| ~~值~~ |").single() as MdBlock.Table
        assertEquals("表头", shareStyledLine(table.headers.single()).text)
        assertEquals("值", shareStyledLine(table.rows.single().single()).text)
    }

    @Test fun onlyPersonaInputsRequireEditMode() {
        assertFalse(CharacterPresentationPolicy.personaEditable(existing = true, editing = false))
        assertTrue(CharacterPresentationPolicy.personaEditable(existing = true, editing = true))
        assertTrue(CharacterPresentationPolicy.personaEditable(existing = false, editing = false))
    }

    @Test fun confirmationCopyAndInputFolderTitleAreAvailableInEveryLocale() {
        for (locale in listOf("zh-CN", "zh-TW", "en")) {
            val s = buildStrings(locale)
            assertTrue(s.inputBox.isNotBlank())
            assertTrue(s.liquidBackdropConfirm.isNotBlank())
            assertTrue(s.deepThinkingModeDesc.isNotBlank())
        }
        val s = buildStrings("zh-CN")
        assertEquals("开启动态渐变会增加性能占用、增加发热与功耗，确定开启？", s.liquidBackdropConfirm)
        assertFalse(s.deepThinkingModeDesc.contains("自动搜得更多"))
    }
}
