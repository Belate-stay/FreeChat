package com.freechat.data

import com.freechat.core.CompanionPrompts

import com.freechat.model.CharacterProfile
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2026-10-20 云端主动智能契约：角色自主判断何时主动发消息（仅微信档）。
 * 用户拍板口径：主动度从人设推导、**重点参考人物关系**（亲密=可频繁+日常/私密，暧昧/同学=收敛）；
 * 开新话题每天最多 2 条（占额度）；时间约定兑现=对话间歇**不占额度**；不要太频繁；看板留痕。
 */
class ProactiveDecideContractTest {

    private fun prompt(
        ch: CharacterProfile = CharacterProfile(name = "测试", relationshipPreset = "女朋友"),
        quota: Int = 2
    ): String = CompanionPrompts.proactiveDecide(
        ch = ch,
        recentLines = "[TA 2026-10-20T10:00:00Z] 等我10分钟\n[你 2026-10-20T10:00:05Z] 行",
        nowText = "2026-10-20 10:12",
        newTopicQuotaLeft = quota,
        lastProactiveText = "30 分钟前",
        sleepWindow = "23:00-07:00"
    )

    @Test fun relationshipDrivesProactiveStyle() {
        val p = prompt()
        // 人物关系是主动分寸的重点参考（工单核心）
        assertTrue("关系段进决策", p.contains("你和 TA 的关系") && p.contains("女朋友"))
        assertTrue("亲密度推导主动度", p.contains("关系定分寸") && p.contains("越亲") &&
            p.contains("分享日常和私密"))
        assertTrue("暧昧/同学档收敛口径", p.contains("暧昧") && p.contains("收敛"))
        assertTrue("人设全文进决策", p.contains("【你的人设】"))
    }

    @Test fun topicQuotaAndKindSemantics() {
        val p = prompt()
        assertTrue("开新话题占额度且每日上限口径", p.contains("今天开新话题的剩余条数") &&
            p.contains("new_topic：前文已经自然收尾"))
        assertTrue("约定兑现/话题间歇不占额度", p.contains("anchor") && p.contains("不占额度") &&
            p.contains("continuation"))
        assertTrue("额度耗尽禁开新话题", p.contains("剩 0 条时不准用"))
    }

    @Test fun timeAwarenessAndGuardrails() {
        val p = prompt()
        assertTrue("时间约定到点兑现（弹性拖几分钟）", p.contains("等我10分钟") && p.contains("晚几分钟完全没问题"))
        // 2026-10-20 用户口径：**深夜不是禁区**（「睡了吗」很常见）——作息只是参考，概率因人设/关系而异
        assertTrue("深夜可主动（降概率不禁止）", p.contains("深夜不是禁区") && p.contains("睡了吗") &&
            p.contains("不是禁发时段"))
        assertTrue("道别后只兑约定不开新话题", p.contains("明确道别") && p.contains("anchor"))
        assertTrue("语义刹车（在忙/刚主动过）", p.contains("在忙") && p.contains("宁可少一条"))
        assertTrue("输出严格 JSON（action/kind/枚举）",
            p.contains("\"action\":\"none|message|wait\"") && p.contains("\"kind\":\"new_topic|continuation|anchor\""))
    }

    @Test fun wechatOnlyDescCopy() {
        val zh = com.freechat.i18n.buildStrings("zh-CN")
        val tw = com.freechat.i18n.buildStrings("zh-TW")
        val en = com.freechat.i18n.buildStrings("en")
        assertTrue("三语小字=工单原文", zh.proactiveDesc == "允许云端主动调用API根据上下文发送消息。依赖微信消息推送和云端API，仅连接微信后有效。" &&
            tw.proactiveDesc == "允許雲端主動調用API根據上下文發送消息。依賴微信消息推送和雲端API，僅連接微信後有效。" &&
            en.proactiveDesc.contains("WeChat"))
    }

    @Test fun wechatLinkRowAndProactiveSubitem() {
        // 2026-10-20 工单：模拟设置页加「连接微信」主项（创建/编辑都显示），
        // 「主动智能」降为子项且**连接微信开启后才显示**（包含关系同时满足）；绑定走三分支。
        val setup = java.io.File("src/main/java/com/freechat/ui/screens/CharacterSetupScreen.kt").readText()
        val strings = java.io.File("src/main/java/com/freechat/i18n/AppStrings.kt").readText()
        assertTrue("连接微信主项在微信档", setup.contains("s.wechatLinkTitle") && setup.contains("dialogueMode == DialogueMode.WECHAT"))
        assertTrue("主动智能=子项且连接后才显示", setup.contains("AnimatedVisibility(wechatConnected") && setup.contains("start = 20.dp"))
        assertTrue("三分支：未登录提示/创建中提示/换绑确认后弹二维码",
            setup.contains("s.wechatLoginRequired") && setup.contains("s.wechatCreateFirst") &&
                setup.contains("showWechatRebindConfirm = true") && setup.contains("showWechatBind = true"))
        assertTrue("换绑确认=只能连一个对话", strings.contains("微信只能同时连接一个对话") &&
            setup.contains("viewModel.wechatBoundConvId()"))
        assertTrue("闹钟权限提示退役（云端定时不需要本机闹钟）",
            !setup.contains("exactAlarmOk") && !strings.contains("proactiveExactHint"))
        // 2026-10-20 病根钉：连接态不许拿 30s 缓存 null 当未连接（假阴会藏子项）——进页查真值
        assertTrue("连接态进页查真值（缓存 null 会假阴）",
            setup.contains("wechatBoundHere") && setup.contains("LaunchedEffect(conversationId, wechatBindTick)"))
    }
}
