package com.freechat.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Beta1109UiRegressionTest {
    private fun source(path: String) = File("src/main/java/com/freechat/$path").readText()

    @Test fun generationPlaceholderKeepsAnAccessibleLabelWithoutVisibleCaption() {
        val placeholder = source("ui/components/ImageGenerationPlaceholder.kt")
        val generation = placeholder.substringAfter("fun ImageGenerationPlaceholder(")
            .substringBefore("internal fun ImageGenerationParticles(")

        assertFalse("Generation should show only the existing animated dot field",
            generation.contains("LoadingImageCaption(") || generation.contains("Text("))
        assertTrue("Screen readers still need the generating-image state",
            generation.contains("contentDescription = s.generatingImage"))
        assertTrue("The AGSL dot field remains the loading indicator",
            generation.contains("ImageGenerationParticles("))
    }

    @Test fun chatPreviewReceivesTheConversationGalleryAndPagesAtFitScale() {
        val chat = source("ui/screens/ChatScreen.kt")
        val bubble = source("ui/components/ChatBubble.kt")
        val preview = source("ui/components/FullScreenImagePreview.kt")

        assertTrue("Every conversation bubble must receive the same image gallery",
            chat.contains("imageGallery = conversationImages"))
        assertTrue("The clicked bubble must pass the gallery into its preview",
            bubble.contains("imageGallery: List<PreviewImage>"))
        assertTrue("Horizontal movement at fit scale switches gallery pages",
            preview.contains("HorizontalPager("))
        assertTrue("A zoomed image reserves horizontal gestures for panning",
            preview.contains("userScrollEnabled = scale <= 1.01f"))
        assertTrue("Download must use the currently selected image",
            preview.contains("currentImage = images[pagerState.currentPage") && preview.contains("onDownload(currentImage)"))
    }

    @Test fun allCharacterSettingsModesShareTheFunctionalDividerShortcut() {
        val setup = source("ui/screens/CharacterSetupScreen.kt")
        val secondPart = setup.substringAfter("// 人物设定 / 模拟设置")
            .substringBefore("// ──── 增强检索")
        val header = setup.substringAfter("// 悬浮标题栏")
            .substringBefore("SheetPanel(showImportMismatch")

        assertTrue("The shortcut target must be the Part 2 divider after persona fields",
            secondPart.contains("functionalSettingsTop"))
        assertTrue("The header shortcut needs a localized accessible label",
            header.contains("s.jumpToFunctionalSettings"))
        assertTrue("The scroll action needs the shared screen scroll state",
            header.contains("settingsScrollState.animateScrollTo("))
        assertTrue("Reduced motion uses an immediate scroll",
            header.contains("settingsScrollState.scrollTo("))
    }
}
