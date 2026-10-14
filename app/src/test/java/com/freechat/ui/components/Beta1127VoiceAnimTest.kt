package com.freechat.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Beta1127VoiceAnimTest {
    private fun source(path: String) = File("src/main/java/com/freechat/$path").readText()

    @Test fun voiceCreationModeSwitchUsesAnimatedContentAndHousePageTransition() {
        val page = source("ui/screens/MiMoVoiceSettingsPage.kt")
        val modeSwitch = page.substringAfter("AnimatedContent(")
            .substringBefore("private fun MotionOutlinedButton")

        assertTrue("Cards and the two form modes must switch inside AnimatedContent",
            page.contains("AnimatedContent(") && page.contains("targetState = mode"))
        assertTrue("Form entry/return must use FreeChatAnimation.pageTransition with direction from targetState",
            modeSwitch.contains("FreeChatAnimation.pageTransition(targetState != null"))
        assertTrue("Travel distance comes from MotionPolicy.PageTravelDp, not a raw pixel constant",
            page.contains("MotionPolicy.PageTravelDp"))
        assertFalse("The flash-prone hard branch must not key the UI on live mode during the transition",
            page.contains("if (mode == null) {"))
    }

    @Test fun advancedVoiceChoiceCarriesSharedPressScaleInsteadOfBareClickable() {
        val page = source("ui/screens/MiMoVoiceSettingsPage.kt")
        val choice = page.substringAfter("private fun AdvancedVoiceChoice")

        assertTrue("The choice card needs the shared press scale (pressMotion or MotionIndication)",
            choice.contains("pressMotion(") || choice.contains("MotionIndication"))
        assertFalse("Bare clickable without a press source is what made these cards feel dead",
            choice.contains(".clickable(onClick = onClick)"))
    }

    @Test fun creationFormActionsUseTheMotionButtonKit() {
        val page = source("ui/screens/MiMoVoiceSettingsPage.kt")
        val form = page.substringAfter("} else {")
            .substringBefore("private fun MotionOutlinedButton")

        assertTrue("Create and save use MotionButton", form.contains("MotionButton("))
        assertTrue("Text actions use MotionTextButton", form.contains("MotionTextButton("))
        assertTrue("Outlined actions keep their border via a pressMotion wrapper", form.contains("MotionOutlinedButton("))
        assertTrue("The create action returns through MotionButton, not a plain Button label",
            form.contains("return@MotionButton"))
    }
}
