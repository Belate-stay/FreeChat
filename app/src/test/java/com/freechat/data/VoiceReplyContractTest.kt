package com.freechat.data

import com.freechat.core.CompanionPrompts
import com.freechat.model.CharacterProfile
import java.io.File
import org.junit.Assert.*
import org.junit.Test

/**
 * 允许语音回复（2026-10-19）契约：
 *  · <voice> 标记解析（模型自决语音的出口）；正文普通回复不误判；
 *  · 戒律 3：withSimulationSettings 白名单带 allowVoiceReply（两份 diff 自动跟上）；
 *  · 源码钉：提示词教学/段落解析/微信连接自动关闭/设置页闸门文案都在。
 */
class VoiceReplyContractTest {

    @Test fun voiceMarkerExtractsSpokenLineOnly() {
        assertEquals("好呀，那我等你。", CompanionPrompts.extractVoice("<voice>好呀，那我等你。</voice>")?.text)
        assertEquals("嗯。", CompanionPrompts.extractVoice("  <voice>  嗯。  </voice>  ")?.text)
        assertNull(CompanionPrompts.extractVoice("这就是普通回复，<voice>不该被误判。"))
        assertNull(CompanionPrompts.extractVoice("<voice></voice>"))
        assertNull(CompanionPrompts.extractVoice(""))
        // 语态（style）随台词走——语气/语态由模型按语境写，停顿走台词标点
        val styled = CompanionPrompts.extractVoice("<voice style=\"撒娇、轻快\">好呀……才怪！</voice>")
        assertEquals("好呀……才怪！", styled?.text)
        assertEquals("撒娇、轻快", styled?.style)
        assertEquals("", CompanionPrompts.extractVoice("<voice>没有语态</voice>")?.style)
    }

    @Test fun simulationWhitelistCarriesAllowVoiceReply() {
        // 戒律 3：模拟设置白名单漏字段=保存时被当「人设改动」误判/丢弃
        val original = CharacterProfile(name = "a", allowVoiceReply = false)
        val draft = original.copy(allowVoiceReply = true, voiceTimbre = "冰糖")
        val merged = CharacterPresentationPolicy.withSimulationSettings(original, draft)
        assertTrue("白名单必须带 allowVoiceReply", merged.allowVoiceReply)
        assertTrue("白名单必须带 voiceTimbre", merged.voiceTimbre == "冰糖")
        assertTrue("模拟设置 diff 必须能看见它", CharacterSavePolicy.hasSimulationChanges(original, draft))
        assertFalse("人设 diff 不许被它污染", CharacterSavePolicy.hasPersonaChanges(original, draft))
        assertFalse("语音模型字段已撤（只选音色）", CharacterProfile::class.java.declaredFields.any { it.name == "voiceModelId" })
    }

    @Test fun wiringPins() {
        val vm = File("src/main/java/com/freechat/viewmodel/ChatViewModel.kt").readText()
        assertTrue("段落提交处解析语音标记", vm.contains("CompanionPrompts.extractVoice(seg)"))
        assertTrue("提示词按开关教学", vm.contains("allowVoiceReply = (character?.allowVoiceReply == true || forceVoice)"))
        assertTrue("连接微信自动关闭", vm.contains("fun disableVoiceReplyForWechat"))
        val setup = File("src/main/java/com/freechat/ui/screens/CharacterSetupScreen.kt").readText()
        assertTrue("设置页开关在", setup.contains("s.allowVoiceReply"))
        assertTrue("连接微信置灰+说明", setup.contains("s.allowVoiceReplyWechatGate") && setup.contains("enabled = !wechatConnected"))
        val bubble = File("src/main/java/com/freechat/ui/components/ChatBubble.kt").readText()
        assertTrue("微信式语音气泡", bubble.contains("VoiceReplyBubble"))
        val bind = File("src/main/java/com/freechat/ui/components/WechatBindDialog.kt").readText()
        assertTrue("绑定成功才关（bound 状态透传）", bind.contains("onBoundChanged(true)") && bind.contains("onBoundChanged(false)"))
        val core = File("../freechat-core/src/main/kotlin/com/freechat/core/CompanionPrompts.kt").readText()
        assertTrue("开发者强制语音的教学档", core.contains("voiceReplyAlways"))
        assertTrue("强制档解析层兜底", vm.contains("forceVoiceReply"))
        assertTrue("跟随全局的设计/复刻常量直通（不在模型池的 DESIGN/CLONE 不许被池校验丢弃）",
            vm.contains("BuiltInVoiceModel.DESIGN_ID, BuiltInVoiceModel.CLONE_ID ->"))
        // 只选音色（2026-10-19 二稿）：角色不再选语音模型——引擎由音色自带
        // （MiMo 预设名→MiMo-V2.5-TTS；自建音色→复刻回放；已删音色→复刻链精确报「本机音色已不存在」）
        assertTrue("引擎由音色推导", vm.contains("if (timbre in ttsVoices) BuiltInVoiceModel.STOCK_ID else BuiltInVoiceModel.CLONE_ID"))
        assertTrue("自建音色走复刻回放同链", vm.contains("voicePresetRepo.playbackConfig(timbre ?: _ttsPresetId.value)"))
        assertFalse("角色设置页不再有语音模型选择", setup.contains("voiceModelId") || setup.contains("showVoiceModelSheet"))
    }

    @Test fun replyShapeFollowsContextAndDotsAreThrottled() {
        // 2026-10-19 ①③：点点点节制（满屏「···」=人机感）+ 回复形态跟着上下文活（短碎/长文/语音/文字不锁死）
        val core = File("../freechat-core/src/main/kotlin/com/freechat/core/CompanionPrompts.kt").readText()
        assertTrue("省略号口癖节制条款", core.contains("【标点口癖——点点点省着用】"))
        assertTrue("省略号随角色习惯，不给每句挂口癖", core.contains("按语气、情绪和角色习惯自然使用") &&
            core.contains("不要无缘由地把省略号挂在每句话前后"))
        assertFalse("不再规定每条消息的标点次数", core.contains("一条回复里最多出现一次"))
        assertTrue("长短跟着内容走且不设固定条数", core.contains("按语义自然分条，不设固定条数") &&
            core.contains("完整的一段也可单独发"))
        assertTrue("语音长短也活（长语音正当）", core.contains("几秒的短句到一分钟的长语音都自然"))
        assertTrue("偏好频率看人设", core.contains("你更偏爱哪种由人设决定"))
    }

    @Test fun perMessageCarrierAndWechatVoiceNotice() {
        // 2026-10-19 晚：每条消息独立选载体（多条语音/语音文字交替）+ 语音标签是微信档唯一符号例外
        // + 绑定弹窗须知补语音不支持告知
        val core = File("../freechat-core/src/main/kotlin/com/freechat/core/CompanionPrompts.kt").readText()
        assertTrue("每条消息独立选载体", core.contains("每条消息独立决定用语音还是打字"))
        assertTrue("可连发多条语音", core.contains("可以连发好几条语音"))
        assertTrue("可语音文字交替", core.contains("「文字→语音→文字」交替"))
        assertTrue("标签写在一行里", core.contains("标签写在一行里"))
        assertTrue("情绪标签不算消息", core.contains("它不算一条消息"))
        assertTrue("微信档符号禁令给语音开口子", core.contains("唯一例外：**语音消息**"))
        assertTrue("强制档也是逐条语音", core.contains("每条消息都必须用语音"))
        val chat = File("src/main/java/com/freechat/ui/screens/ChatScreen.kt").readText()
        val bind = File("src/main/java/com/freechat/ui/components/WechatBindDialog.kt").readText()
        val strings = File("src/main/java/com/freechat/i18n/AppStrings.kt").readText()
        assertTrue("绑定弹窗须知带语音告知（开着语音回复才追加）",
            bind.contains("wechatNoticeVoice") && bind.contains("voiceReplyActive") &&
                chat.contains("voiceReplyActive = currentCharacter?.allowVoiceReply == true"))
        assertTrue("告知文案三语", strings.contains("连接微信不支持语音发送与AI语音回复") &&
            strings.contains("連接微信不支持語音發送") && strings.contains("WeChat does not support voice input"))
    }

    @Test fun longPressVoiceOffersTranscript() {
        // 2026-10-19：长按语音=转文字菜单（AI 口播台词/用户识别文本本就随消息存着，展开即可、不跑识别）
        val bubble = File("src/main/java/com/freechat/ui/components/ChatBubble.kt").readText()
        val chat = File("src/main/java/com/freechat/ui/screens/ChatScreen.kt").readText()
        val strings = File("src/main/java/com/freechat/i18n/AppStrings.kt").readText()
        assertTrue("语音气泡接菜单入口", bubble.contains("onVoiceMenu"))
        assertTrue("转文字展开态参数", bubble.contains("voiceTranscriptVisible"))
        // 2026-10-20 病根二进钉：点按/长按必须**同一层**——内层 clickable 会消费 down，
        // 外层 combinedClickable 的 awaitFirstDown(要求未消费) 空转，长按挂外层永远收不到。
        assertTrue("点按长按同层（VoiceReplyBubble 内层 combinedClickable 转发 onLongClick）",
            bubble.contains("Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)"))
        assertTrue("AI 语音长按进菜单（内层转发）", bubble.contains("onLongClick = { onVoiceMenu?.invoke() ?: onQuote?.invoke() }"))
        assertTrue("用户语音长按进菜单（内层转发）", bubble.contains("onLongClick = { onVoiceMenu?.invoke() }"))
        assertTrue("语音容器外层不挂手势（挂了也是空转）",
            bubble.contains("message.voicePath != null && onSpeak != null -> Modifier"))
        assertTrue("文字气泡不挂长按（保留系统文字选中）", bubble.contains("onEdit != null -> Modifier.combinedClickable(onClick = { onEdit() })"))
        assertTrue("菜单=转文字/收起+引用", chat.contains("s.voiceToTextHide else s.voiceToText") && chat.contains("SheetActionRow(s.quoteAction, colors)"))
        assertTrue("AI 与用户语音共用（voiceText 或 voicePath 都出菜单）",
            chat.contains("onVoiceMenu = if (msgItem.msg.voiceText != null || msgItem.msg.voicePath != null)"))
        assertTrue("三语词条", strings.contains("转文字") && strings.contains("轉文字") && strings.contains("Transcribe"))
    }
}
