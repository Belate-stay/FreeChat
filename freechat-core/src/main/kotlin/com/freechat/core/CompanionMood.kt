package com.freechat.core

import com.freechat.model.CharacterProfile
import com.freechat.model.PerConvSettings

/**
 * 拟人情绪：本地兜底判定 + 情绪余波 + 模型标签解析 + 作息生成（纯函数，1.0.91 M1 从 ChatViewModel 平移）。
 * 时间一律从参数注入（nowMillis），服务器侧才能做确定性回归。
 */
enum class CompanionMood { NEUTRAL, HAPPY, EXCITED, ANGRY, SAD, WRONGED, DISMISSIVE, SHY, BORED }

/** 根据用户消息关键词判断拟人角色情绪（模型未识别情绪时的本地兜底） */
fun detectCompanionMood(text: String): CompanionMood {
    val t = text.trim()
    val angry = listOf("滚", "讨厌", "烦", "闭嘴", "神经病", "有病", "傻", "蠢", "滚蛋", "别烦", "不想理", "别说了")
    if (angry.any { t.contains(it) }) return CompanionMood.ANGRY
    val happy = listOf("哈哈", "开心", "好玩", "笑死", "喜欢", "太好了", "棒", "爱你", "想你", "嘻嘻")
    if (happy.any { t.contains(it) }) return CompanionMood.HAPPY
    val bored = listOf("哦", "嗯", "呵呵", "随便", "都行", "无所谓")
    if (t.length <= 2 && bored.any { t == it }) return CompanionMood.BORED
    return CompanionMood.NEUTRAL
}

/**
 * 情绪余波（1.0.73）：本条消息没带情绪线索时，沿用「上次聊完时」的快照情绪（随时间衰减）。
 * 这样前天吵完架今天开口，它不会若无其事满血 —— 快照就是情感底片。
 */
fun moodWithResidue(text: String, per: PerConvSettings?, nowMillis: Long = System.currentTimeMillis()): CompanionMood {
    val direct = detectCompanionMood(text)
    if (direct != CompanionMood.NEUTRAL) return direct
    val snapMood = per?.lastMood?.takeIf { it.isNotBlank() && per.moodAtMs > 0 } ?: return CompanionMood.NEUTRAL
    val ageMin = (nowMillis - per.moodAtMs) / 60000L
    if (ageMin > 360) return CompanionMood.NEUTRAL   // 6 小时后余波散尽
    return runCatching { CompanionMood.valueOf(snapMood) }.getOrDefault(CompanionMood.NEUTRAL)
}

/** 把模型输出的情绪标签解析成 CompanionMood（识别不了返回 null，走本地兜底） */
fun parseEmotionLabel(label: String): CompanionMood? = when (label.trim()) {
    "开心" -> CompanionMood.HAPPY
    "兴奋" -> CompanionMood.EXCITED
    "生气" -> CompanionMood.ANGRY
    "难过" -> CompanionMood.SAD
    "委屈" -> CompanionMood.WRONGED
    "敷衍" -> CompanionMood.DISMISSIVE
    "害羞" -> CompanionMood.SHY
    "无聊" -> CompanionMood.BORED
    "平静", "平常" -> CompanionMood.NEUTRAL
    else -> null
}

/** 检测用户是否在道晚安/说去睡（用于「可选不回复」与时间观念） */
fun detectGoodnight(text: String): Boolean {
    val t = text.trim()
    val keywords = listOf("晚安", "睡了", "我要睡", "去睡了", "睡觉了", "要睡了", "困了", "睡觉去", "我先睡", "睡了没")
    return keywords.any { t.contains(it) }
}

/** 根据性格/MBTI/记忆里的作息描述，生成当天的睡觉/起床时刻（每天微调，不重复） */
fun generateSleepSchedule(ch: CharacterProfile): Pair<Int, Int> {
    // 「规则」里也会写人物习惯（熬夜/作息规律），一并纳入识别
    val t = ch.personalityText + ch.memoryPerception + ch.worldRules
    val nightOwl = t.contains("熬夜") || t.contains("夜猫") || t.contains("晚睡") || t.contains("夜猫子")
    val early = t.contains("规律") || t.contains("早睡") || t.contains("作息规律")
    val baseSleep = when {
        nightOwl -> 2            // 凌晨 2 点睡
        early -> 23              // 23 点睡
        ch.mbtiPJ < 0.5f -> 1    // P 随性，偏晚睡
        else -> 23               // J 决断，偏规律
    }
    // 取模跨午夜（23±1 → 22/23/0），不能用 coerceIn 夹值 —— 夹到 0..4 会把 23 点整档
    // 全夹成凌晨 4 点，J 型/早睡角色的作息整个反过来（1.0.73 埋雷，1.0.99.4b 修）。
    val sleepHour = Math.floorMod(baseSleep + kotlin.random.Random.nextInt(-1, 2), 24)
    val duration = if (nightOwl) kotlin.random.Random.nextInt(8, 10) else kotlin.random.Random.nextInt(6, 9)
    val wakeHour = (sleepHour + duration) % 24
    return sleepHour to wakeHour
}
