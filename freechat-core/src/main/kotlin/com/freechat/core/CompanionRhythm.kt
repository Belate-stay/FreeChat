package com.freechat.core

import kotlin.random.Random

/**
 * 拟人回复节奏表（1.0.91 M1 第三刀从 ChatViewModel.generateCompanionReply 平移，行为一字不变）：
 * 情绪决定「回几条」与「每条隔多久」——像真人的说话节奏，时快时慢、话多话少随心情。
 * 叙事档另走一套（整段一条，只留段间小歇）。随机源可注入（服务器回归要确定性）。
 */
object CompanionRhythm {

    /** 先随机等待再显示「正在输入」——刚发完消息就秒回太假 */
    fun initialTypingDelayMs(random: Random = Random.Default): Long = random.nextLong(1500L, 4500L)

    /**
     * 这一轮展示几条（从分好条的回复里取前 N 条）。
     * ANGRY 有 35% 概率整轮不回（真生气了就不理人）；[regenerating] 重生成时不许「生气不回」
     * （用户主动点的重生成必须给出内容）。
     */
    fun segmentCount(mood: CompanionMood, narrative: Boolean, regenerating: Boolean, segmentTotal: Int, random: Random = Random.Default): Int {
        return if (narrative) segmentTotal.coerceAtMost(3) else when (mood) {
            CompanionMood.ANGRY -> if (!regenerating && random.nextFloat() < 0.35f) 0 else 1
            CompanionMood.SAD, CompanionMood.WRONGED -> 1
            CompanionMood.DISMISSIVE, CompanionMood.SHY, CompanionMood.BORED -> 1
            CompanionMood.EXCITED -> random.nextInt(2, 4)
            CompanionMood.HAPPY -> random.nextInt(2, 4)
            CompanionMood.NEUTRAL -> random.nextInt(1, 4)
        }
    }

    /** 每条之间的「正在输入」间隔（毫秒区间），情绪不同快慢不同 */
    fun segmentIntervalMs(mood: CompanionMood, narrative: Boolean, random: Random = Random.Default): Long {
        return if (narrative) random.nextLong(1500L, 3000L) else when (mood) {
            CompanionMood.ANGRY -> random.nextLong(4000L, 7000L)
            CompanionMood.SAD, CompanionMood.WRONGED -> random.nextLong(2000L, 4500L)
            CompanionMood.DISMISSIVE, CompanionMood.BORED -> random.nextLong(2000L, 4200L)
            CompanionMood.SHY -> random.nextLong(2000L, 3500L)
            CompanionMood.EXCITED -> random.nextLong(800L, 2000L)
            CompanionMood.HAPPY -> random.nextLong(900L, 2600L)
            CompanionMood.NEUTRAL -> random.nextLong(1500L, 3800L)
        }
    }
}
