package com.freechat.data

import com.freechat.model.CharacterProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 图片预设契约（1.1.26.6）：无声明不写入；声明了就把画幅/主体提示词一并送出。 */
class ImagePresetPromptTest {

    private fun profile(aspect: Int = 0, subject: Int = 0) = CharacterProfile(
        name = "旅人", imagePresetAspect = aspect, imagePresetSubject = subject
    )

    @Test fun noDeclarationWritesNothing() {
        assertEquals(emptyList<String>(), SceneImagePrompt.imagePresetPrompts(profile()))
    }

    @Test fun declaredAspectAndSubjectTravelTogether() {
        val prompts = SceneImagePrompt.imagePresetPrompts(profile(aspect = 2, subject = 1))
        assertEquals(2, prompts.size)
        assertTrue(prompts[0].contains("4:3"))
        assertTrue(prompts[0].contains("横向"))
        assertTrue(prompts[1].contains("完整场景") || prompts[1].contains("整个场景"))
    }

    @Test fun eachOptionMapsToItsOwnLine() {
        assertEquals(1, SceneImagePrompt.imagePresetPrompts(profile(aspect = 5)).size)
        assertEquals(1, SceneImagePrompt.imagePresetPrompts(profile(subject = 2)).size)
        assertTrue(SceneImagePrompt.imagePresetPrompts(profile(subject = 2)).single().contains("特写"))
    }
}
