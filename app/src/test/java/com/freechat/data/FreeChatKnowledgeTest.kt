package com.freechat.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 产品知识库（1.1.27）：检索命中、恒带章节、隐私红线的契约钉子。 */
class FreeChatKnowledgeTest {

    @Test fun wechatQuestionGetsTheFullGuide() {
        val out = FreeChatKnowledge.forPrompt("FreeChat怎么接入微信？")
        assertTrue("讲清技术原理（三端直连、不依赖本地后台）",
            out.contains("三端直连") && out.contains("不依赖本地后台"))
        assertTrue("限定使用范围（仅拟人「微信聊天」+ 需登录）",
            out.contains("微信聊天") && out.contains("登录"))
        assertTrue("给出操作路径（标题栏绿色对话图标 → 二维码扫码）",
            out.contains("二维码") && out.contains("绿色对话图标"))
    }

    @Test fun updateQuestionPointsToTheInAppPath() {
        val out = FreeChatKnowledge.forPrompt("如何获取最新版本？")
        assertTrue("导航路径完整可照做", out.contains("关于FreeChat") && out.contains("检查更新"))
    }

    @Test fun introAndAuthorAlwaysTravelTogether() {
        val out = FreeChatKnowledge.forPrompt("怎么导入角色卡？")
        assertTrue("无论问什么都带产品简介", out.contains("产品简介与定位"))
        assertTrue("无论问什么都带作者与初衷", out.contains("作者与开发初衷"))
        assertTrue("导入章节被检索命中", out.contains("分享与角色卡"))
    }

    @Test fun vagueQuestionFallsBackToTheWholeLibrary() {
        val out = FreeChatKnowledge.forPrompt("随便问点没写过的词：紫色斑马")
        listOf("对话模式", "连接微信", "生成当前场景图", "记忆系统", "账号与云同步", "个性化")
            .forEach { assertTrue("全库兜底应含「$it」", out.contains(it)) }
    }

    @Test fun authorFactsArePinnedAgainstHallucination() {
        val out = FreeChatKnowledge.forPrompt("作者是谁？")
        assertTrue(out.contains("胡勃阳") && out.contains("belate_coder"))
        assertTrue("一人开发不是团队、无自研大模型的纠偏必须在",
            out.contains("不是团队") && out.contains("没有自研大模型"))
    }

    @Test fun privacyRedLinesNeverEnterTheKnowledge() {
        // 全库文本（取全库兜底的输出）不得出现：开发者模式/后门/使用信标/内部标识
        val out = FreeChatKnowledge.forPrompt("紫色斑马")
        listOf("开发者模式", "开发者选项", "DevMode", "后门", "信标", "UsageBeacon", "installId", "app_usage")
            .forEach { assertFalse("知识库不得包含「$it」", out.contains(it)) }
    }
}
