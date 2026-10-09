package com.freechat.core

/**
 * Known WeChat built-in bracket emoji names, not custom/native stickers.
 * Factual names checked on 2026-10-08 against:
 * https://raw.githubusercontent.com/xxk8/wechat-emojis/main/wechatEmoji.ts
 * The source package declares MIT; no source implementation or image assets are copied.
 * This is a conservative known-name whitelist, not an official completeness claim.
 */
object WechatEmojiTokens {
    val names: Set<String> = setOf(
        "微笑", "撇嘴", "色", "发呆", "得意", "流泪", "害羞", "闭嘴", "睡", "大哭",
        "尴尬", "发怒", "调皮", "呲牙", "惊讶", "难过", "囧", "抓狂", "吐", "偷笑",
        "愉快", "白眼", "傲慢", "困", "惊恐", "憨笑", "悠闲", "咒骂", "疑问", "嘘",
        "晕", "衰", "骷髅", "敲打", "再见", "擦汗", "抠鼻", "鼓掌", "坏笑", "右哼哼",
        "鄙视", "委屈", "快哭了", "阴险", "亲亲", "可怜", "笑脸", "生病", "脸红",
        "破涕为笑", "恐惧", "失望", "无语", "嘿哈", "捂脸", "机智", "皱眉", "耶",
        "吃瓜", "加油", "汗", "天啊", "Emm", "社会社会", "旺柴", "好的", "打脸",
        "哇", "翻白眼", "666", "让我看看", "叹气", "苦涩", "裂开", "奸笑",
        "握手", "胜利", "抱拳", "勾引", "拳头", "OK", "合十", "强", "拥抱", "弱",
        "猪头", "跳跳", "发抖", "转圈", "庆祝", "礼物", "红包", "發", "福", "烟花",
        "爆竹", "嘴唇", "爱心", "心碎", "啤酒", "咖啡", "蛋糕", "凋谢", "菜刀",
        "炸弹", "便便", "太阳", "月亮", "玫瑰"
    )

    fun isKnownToken(token: String): Boolean =
        token.startsWith('[') && token.endsWith(']') && token.substring(1, token.length - 1) in names

    private val bracketToken = Regex("""\[[^\[\]\r\n]+]""")

    fun stripKnownTokens(text: String): String = text.replace(bracketToken) { match ->
        if (isKnownToken(match.value)) "" else match.value
    }
}
