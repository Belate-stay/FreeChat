package com.freechat.core

import com.freechat.data.AppJson
import com.freechat.data.healed
import com.freechat.model.*
import org.junit.Assert.*
import org.junit.Test

class StoryTest {
    private fun cards(n: Int) = (1..n).map { CharacterCard("c$it", CharacterPersona(name = "角色$it",
        personalityText = "谨慎$it", personaPrompt = "只扮演角色$it", originalLearningText = "原文$it")) }

    @Test fun storyAcceptsOneToEightDistinctSnapshotsWithoutImportingConversationSettings() {
        val story = StoryPolicy.fromCards(cards(8))
        assertTrue(StoryPolicy.isValid(story))
        assertFalse(StoryPolicy.isValid(story.copy(members = emptyList())))
        assertFalse(StoryPolicy.isValid(story.copy(members = story.members + story.members.first())))
        assertThrows(IllegalArgumentException::class.java) { StoryPolicy.fromCards(cards(9)) }
        assertEquals("", story.languageModelId)
        assertEquals("", story.visualModelId)
        assertFalse(AppJson.gson.toJson(story).contains("replyBuffer"))
        assertFalse(AppJson.gson.toJson(story).contains("proactiveEnabled"))
        assertEquals("角色1", story.members.first().persona.name)
        assertNotEquals(story.members.first().id, StoryPolicy.fromCards(cards(1)).members.first().id)
    }

    @Test fun oldAndNewConversationFilesRoundTripWithoutCreatingAPrivateLeadOrGroup() {
        assertNull(AppJson.gson.fromJson("{\"id\":\"old\"}", Conversation::class.java).healed().storyProfile)
        val original = Conversation(mode = ChatMode.COMPANION, storyProfile = StoryPolicy.fromCards(cards(2)))
        val loaded = AppJson.gson.fromJson(AppJson.gson.toJson(original), Conversation::class.java).healed()
        assertEquals(original, loaded)
        assertNull(loaded.groupChat)
        assertNull(loaded.characterProfile)
        val partial = AppJson.gson.fromJson("{\"members\":null,\"background\":null}", StoryProfile::class.java).healed()
        assertEquals(emptyList<StoryMember>(), partial.members)
        assertEquals("", partial.background)
    }

    @Test fun narratorKeepsCastWorldKnowledgeAndMemorySeparateAndExcludesVisualOnlyRows() {
        val story = StoryPolicy.fromCards(cards(2)).copy(background = "风港", worldRules = "禁止魔法", openingSituation = "暴雨停电")
        val system = StoryPrompts.system(story)
        for (fact in listOf("角色1", "角色2", "谨慎1", "原文2", "风港", "禁止魔法", "暴雨停电",
            "第三人称", "不固定主角", "角色不知道", "不必每轮", "人物材料")) assertTrue(fact, system.contains(fact))
        val rows = listOf(Message(role = Role.USER, content = "沿河寻找"),
            Message(role = Role.ASSISTANT, content = "灯塔熄灭"),
            Message(role = Role.ASSISTANT, content = "图像不写剧情", sceneVisualization = true),
            Message(role = Role.ASSISTANT, content = "失败不写剧情", failed = true))
        val request = StoryPrompts.request(story, rows, "记忆：钥匙在阿青手中", "本轮从港口开始")
        val text = request.joinToString { it["content"].toString() }
        assertTrue(text.contains("灯塔熄灭"))
        assertTrue(text.contains("钥匙在阿青手中"))
        assertTrue(text.contains("本轮从港口开始"))
        assertFalse(text.contains("图像不写剧情"))
        assertFalse(text.contains("失败不写剧情"))
        assertFalse(text.contains("微信聊天"))
    }
}
