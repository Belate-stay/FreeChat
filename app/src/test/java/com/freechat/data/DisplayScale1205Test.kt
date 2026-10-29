package com.freechat.data

import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freechat.model.FontSize
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class DisplayScale1205Test {
    private fun source(path: String) = File("src/main/java/com/freechat/$path").readText()

    @Test fun everyPresetScalesTextAndCardGeometryExactlyOnce() {
        val conversion = Class.forName("com.freechat.ui.theme.DisplayScaleKt")
            .getMethod("appDisplayDensity", Density::class.java, FontSize::class.java)
        val system = Density(2f, fontScale = 1.3f)
        for (preset in FontSize.entries) {
            val display = conversion.invoke(null, system, preset) as Density
            assertEquals(2f * preset.scale, display.density, .0001f)
            assertEquals(1f, display.fontScale, .0001f)
            with(display) {
                assertEquals(96f * preset.scale, 48.dp.toPx(), .001f)
                assertEquals(32f * preset.scale, 16.sp.toPx(), .001f)
            }
        }
        assertEquals("System density is never mutated", 2f, system.density, .0001f)
    }

    @Test fun availableDpTracksScaledDensityWithoutChangingPhysicalWindowSize() {
        val conversion = Class.forName("com.freechat.ui.theme.DisplayScaleKt")
            .getMethod("appDisplayDp", Int::class.javaPrimitiveType, FontSize::class.java)
        for (width in listOf(392, 800, 1280)) for (preset in FontSize.entries) {
            val dp = conversion.invoke(null, width, preset) as Int
            assertEquals(width.toFloat(), dp * preset.scale, preset.scale / 2f + .001f)
            if (preset == FontSize.MEDIUM) assertEquals(width, dp)
        }
    }

    @Test fun themeAndDrawerShareApplicationLocalGeometryAndDoNotDoubleScale() {
        val theme = source("ui/theme/Theme.kt")
        val main = source("MainActivity.kt")
        assertTrue(theme.contains("appDisplayDensity(density, fontSize)"))
        assertTrue(theme.contains("Configuration(configuration)"))
        assertTrue(theme.contains("screenWidthDp = appDisplayDp(configuration.screenWidthDp, fontSize)"))
        assertTrue(theme.contains("screenHeightDp = appDisplayDp(configuration.screenHeightDp, fontSize)"))
        assertTrue(theme.contains("LocalConfiguration provides displayConfiguration"))
        assertTrue(theme.contains("LocalFontScale provides displayDensity.fontScale"))
        assertTrue(main.contains("appDisplayDensity(LocalDensity.current, fontSize)"))
        assertTrue(main.contains("appDisplayDp(config.screenWidthDp, fontSize)"))
        assertFalse(theme.contains("updateConfiguration("))
        assertFalse(theme.contains("Settings.System"))
        assertFalse(theme.contains("resources.displayMetrics.density ="))
        val picker = source("ui/screens/SettingsScreen.kt")
            .substringAfter("visible = showFontSizePicker,").substringBefore("// 全局记忆编辑")
        assertTrue(picker.contains("previewFontSize.scale / fontSize.scale"))
    }

    @Test fun recommendationUsesReadableStylesAndScrollsInsteadOfClippingLargeDemoContent() {
        val page = source("ui/components/FeatureRecPage.kt")
        val tablet = page.substringAfter("private fun TabletFeatureRecContent(")
            .substringBefore("/** 演示片段文案")
        assertTrue(tablet.contains("fontSize = 28.sp"))
        assertTrue("功能名 18sp 加重、简介 14sp 弱化（2026-10-28 工单）",
            tablet.contains("fontSize = 18.sp") && tablet.contains("fontSize = 14.sp"))
        assertTrue(tablet.contains("lineHeight = 21.sp"))
        assertTrue("A landscape tablet at LARGE still has room for the two-column composition",
            tablet.contains("maxHeight >= 520.dp"))
        val demo = page.substringAfter("private fun FeatureRecDemo(")
        assertTrue("Revisiting a recommendation retains its reading position", demo.contains("val demoScroll = playback.scroll"))
        assertTrue(demo.contains("verticalScroll(demoScroll, enabled = active)"))
        assertTrue(demo.contains("demoScroll.animateScrollTo(demoScroll.maxValue"))
        assertTrue("Real ChatBubble text uses the larger tablet style instead of scaling down the card",
            demo.contains("val typography = MaterialTheme.typography") &&
                demo.contains("if (LocalTabletDevice.current) typography.bodyLarge else typography.bodyMedium"))
        assertTrue("Reduced motion still reveals the full scrollable demo without animated scrolling",
            page.contains("if (!motionOn)") && page.contains("playback.stage = DEMO_LAST_STAGE") &&
                demo.contains("else demoScroll.scrollTo(demoScroll.maxValue)"))
        assertTrue("No touch shield blocks horizontal swipes or vertical reading while a demo plays",
            page.contains("HorizontalPager(") && !page.contains("detectTapGestures") &&
                !page.contains("userScrollEnabled = false"))
    }

    @Test fun nativeWindowsReprovideTheOuterAppDensityInsideTheirNewComposeRoot() {
        val windows = source("ui/theme/DisplayScale.kt")
        assertTrue(windows.contains("fun AppDialog("))
        assertTrue(windows.contains("fun AppPopup("))
        assertTrue(windows.contains("LocalDensity provides density"))
        assertTrue(windows.contains("LocalConfiguration provides configuration"))
        assertTrue(source("dev/DevMode.kt").contains("com.freechat.ui.theme.AppAlertDialog("))
        assertTrue(source("ui/screens/VoiceDebugScreen.kt").contains("AppAlertDialog as AlertDialog"))
        val sources = File("src/main/java/com/freechat").walkTopDown().filter { it.extension == "kt" }
        for (file in sources) {
            if (file.name == "DisplayScale.kt") continue
            val text = file.readText()
            assertFalse("${file.name} must use the density-preserving Dialog wrapper",
                text.contains("import androidx.compose.ui.window.Dialog\n") || text.contains("import androidx.compose.ui.window.Dialog\r\n") ||
                    text.contains("androidx.compose.ui.window.Dialog("))
            assertFalse("${file.name} must use the density-preserving Popup wrapper",
                text.contains("import androidx.compose.ui.window.Popup\n") || text.contains("import androidx.compose.ui.window.Popup\r\n") ||
                    text.contains("androidx.compose.ui.window.Popup("))
        }
    }

    @Test fun narrowPhoneDrawerUsesTheSameCompactFooterAsNarrowTabletPanes() {
        val button = source("ui/components/DrawerContent.kt").substringAfter("private fun FloatingDrawerButton(")
        assertTrue(button.contains("LocalConfiguration.current.screenWidthDp.dp * 0.9f"))
        assertTrue(button.contains("val compact = availableWidth < 340.dp"))
        assertTrue(button.contains("if (!compact)"))
    }

    @Test fun avatarCropStaysSquareInsideTheScaledNativeWindowAndLeavesRoomForActions() {
        val conversion = Class.forName("com.freechat.ui.components.AvatarCropDialogKt")
            .getMethod("avatarCropFrameSideDp", Float::class.javaPrimitiveType, Float::class.javaPrimitiveType)
        for ((width, height) in listOf(392f to 872f, 800f to 360f, 320f to 360f, 800f to 1280f)) {
            for (preset in FontSize.entries) {
                // Column measures its real hint/actions first; the weighted preview receives only
                // the remaining space. Include a wrapped two-line hint in a narrow native window.
                for (hintHeight in listOf(26f, 52f)) {
                    val previewWidth = width / preset.scale - 48f
                    val previewHeight = height / preset.scale - 48f - hintHeight - 12f - 48f
                    val side = conversion.invoke(null, previewWidth, previewHeight) as Float
                    assertEquals(minOf(previewWidth, previewHeight, 320f), side, .001f)
                    assertTrue(side > 0f && side <= 320f)
                }
            }
        }
        val crop = source("ui/components/AvatarCropDialog.kt")
        assertTrue(crop.contains("avatarCropFrameSideDp(maxWidth.value, maxHeight.value)"))
        assertTrue(crop.contains("BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()"))
        assertTrue(crop.contains("frameSizePx = size.width.toFloat()"))
        assertTrue(crop.contains("pointerInput(sourceUri, frameSizePx)"))
        assertFalse(crop.contains("config.screenWidthDp - 48"))
    }

    @Test fun unchangedPixelConstraintsRecalculateDpWhenOnlyAppDensityChanges() {
        // A cached BoxWithConstraints scope still reports the previous density's dp values.
        // Reuse those exact same pixels while changing size back and forth, without rebuilding UI.
        val cachedScope = object : BoxWithConstraintsScope {
            override val constraints = Constraints.fixed(1920, 1200)
            override val minWidth = 1280.dp
            override val maxWidth = 1280.dp
            override val minHeight = 800.dp
            override val maxHeight = 800.dp
            override fun Modifier.align(alignment: Alignment) = this
            override fun Modifier.matchParentSize() = this
        }
        val constructor = Class.forName("com.freechat.ui.theme.AppBoxConstraintsScope")
            .getDeclaredConstructor(BoxWithConstraintsScope::class.java, Density::class.java)
        for (preset in listOf(FontSize.MEDIUM, FontSize.XXLARGE, FontSize.SMALL, FontSize.MEDIUM)) {
            val density = appDensity(preset)
            val scope = constructor.newInstance(cachedScope, density) as BoxWithConstraintsScope
            assertEquals(1920f / density.density, scope.maxWidth.value, .001f)
            assertEquals(scope.maxWidth, scope.minWidth)
            assertEquals(1200f / density.density, scope.maxHeight.value, .001f)
            assertEquals(scope.maxHeight, scope.minHeight)
            assertEquals(cachedScope.constraints, scope.constraints)
        }
        assertEquals(1280.dp, cachedScope.maxWidth)
        for (path in listOf("ui/adaptive/TabletChatCanvas.kt", "ui/adaptive/TabletWorkspace.kt",
            "ui/adaptive/TabletDestinationLayer.kt", "ui/components/FeatureRecPage.kt",
            "ui/components/NoticeDialog.kt", "ui/components/AvatarCropDialog.kt",
            "ui/components/FullScreenImagePreview.kt", "ui/components/ChatInput.kt", "ui/screens/ChatScreen.kt")) {
            assertTrue(path, source(path).contains("AppBoxWithConstraints as BoxWithConstraints"))
        }
    }

    private fun appDensity(preset: FontSize): Density = Density(1.5f * preset.scale, fontScale = 1f)

    @Test fun completeInputActionsCompactFromActualLabelWidthsInsteadOfCoveringTheMicrophone() {
        val method = Class.forName("com.freechat.ui.components.ChatInputKt").getMethod("inputActionsWidthDp",
            Float::class.javaPrimitiveType, Float::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
        fun width(deep: Float, search: Float, image: Boolean = false, emoji: Boolean = false) =
            method.invoke(null, deep, search, image, emoji) as Float
        assertEquals(262f, width(48f, 48f), .001f)
        assertTrue("Chinese labels fit at the default phone size", width(48f, 48f) < 320f)
        assertTrue("Large labels must compact before they reach the voice button", width(48f, 48f) > 220f)
        assertEquals(298f, width(48f, 48f, image = true), .001f)
        assertEquals(306f, width(48f, 48f, emoji = true), .001f)
        assertTrue(width(78f, 62f, emoji = true) > 320f)
        val input = source("ui/components/ChatInput.kt")
        assertTrue(input.contains("maxWidth.value < inputActionsWidthDp("))
        assertTrue(input.contains("compact = compactActions"))
        val chip = input.substringAfter("private fun InputStateChip(").substringBefore("private fun ImageCandidateArea(")
        assertTrue(chip.contains("contentDescription = label"))
        assertTrue(chip.contains("if (!compact)"))
        assertTrue(chip.contains("maxLines = 1"))
    }

    @Test fun longWelcomeLinesFitTheScaledWindowWithoutChangingShortGreetingTypography() {
        val method = Class.forName("com.freechat.ui.screens.ChatScreenKt").getMethod("greetingFitScale",
            Float::class.javaPrimitiveType, List::class.java, List::class.java)
        fun fit(width: Float, lines: List<Float>, indents: List<Float>) =
            method.invoke(null, width, lines, indents) as Float
        assertEquals(1f, fit(336f, listOf(140f, 210f), listOf(0f, 30f)), .001f)
        val longLines = listOf(210f, 340f)
        val indents = listOf(0f, 32f)
        val scale = fit(224f, longLines, indents)
        assertTrue(scale < 1f)
        for (i in longLines.indices) assertTrue(longLines[i] * scale + indents[i] <= 224f + .001f)
        assertEquals(1f, fit(800f, longLines, indents), .001f)
        val greeting = source("ui/screens/ChatScreen.kt").substringAfter("private fun WelcomeGreeting(")
            .substringBefore("private fun lerp(")
        assertTrue(greeting.contains("greetingFitScale("))
        assertTrue(greeting.contains("rememberTextMeasurer()"))
        assertTrue(greeting.contains("35.sp * fitScale"))
    }

    @Test fun emojiPickerUsesAvailableColumnsAndScrollsRatherThanClippingScaledCells() {
        val picker = source("ui/components/ChatInput.kt").substringAfter("visible = emojiAvailable && showEmojiPicker,")
            .substringBefore("// ──── 拖出取消环")
        assertFalse("Eight fixed columns clip in a scaled narrow phone", picker.contains("EMOJI_LIST.chunked(8)"))
        assertTrue(picker.contains("val columns = (maxWidth / 32.dp).toInt().coerceIn(1, 8)"))
        assertTrue(picker.contains("EMOJI_LIST.chunked(columns)"))
        assertTrue(picker.contains("heightIn(max = availableHeight * 0.4f)"))
        assertTrue(picker.contains("verticalScroll(rememberScrollState())"))
        assertTrue(picker.contains("if (emojiAvailable) { draftEditedByUser = true; editText(text + em) }"))
    }
}
