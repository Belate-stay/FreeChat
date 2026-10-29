package com.freechat.data

import com.freechat.model.*
import org.junit.Assert.*
import org.junit.Test

class StorySceneTest {
    @Test fun sceneContainsAllCharacterDescriptionsAndLatestWorldButNotPriorImagesOrFailures() {
        val cast = listOf(StoryMember("a", CharacterPersona(name = "阿青", appearanceText = "银发")),
            StoryMember("b", CharacterPersona(name = "阿蓝", appearanceText = "蓝衣")))
        val prompt = SceneImagePrompt.build(StoryProfile(members = cast, background = "港口", worldRules = "雨季"),
            listOf(Message(role = Role.USER, content = "他们来到灯塔"),
                Message(role = Role.ASSISTANT, content = "旧图不是剧情", sceneVisualization = true)),
            listOf(MemoryEntry(summary = "钥匙遗失", keywords = emptyList())))
        for (word in listOf("阿青", "银发", "阿蓝", "蓝衣", "港口", "雨季", "灯塔", "钥匙遗失", "不固定主角"))
            assertTrue(word, prompt.contains(word))
        assertFalse(prompt.contains("旧图不是剧情"))
    }
}
