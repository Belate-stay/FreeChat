package com.freechat.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 1.1.29.2 ③「去冷」纪律钉子：系统提示词不再用相反形容词压人设——
 * 害羞/生理反应按人设还原，肯定式特质不许淡化（当年防「委曲求全脸红」的锤子矫枉过正，全员变冷）。
 */
class PersonaTemperatureTest {
    private fun source() = File("src/main/kotlin/com/freechat/core/CompanionPrompts.kt").readText()

    @Test fun shyAndPhysiologyFollowThePersonaNotAGlobalDamper() {
        val s = source()
        assertTrue("害羞与生理反应按人设还原", s.contains("完全跟着人设走"))
        assertTrue("写了「容易脸红害羞」就要充分出现", s.contains("毫不吝啬地出现"))
        assertFalse("「绝大多数回复根本不需要写」的全球压制已删", s.contains("绝大多数回复里根本不需要写"))
        assertFalse("「唯独不是脸红心跳」的禁令已删", s.contains("唯独不是脸红心跳"))
    }

    @Test fun noOppositeAdjectiveSteeringAndNeutralIndependence() {
        val s = source()
        assertFalse("「不要娇羞脸红那一套」的反面教材已删", s.contains("不要一律写成娇羞脸红那一套"))
        assertTrue("亲昵方式完全还原人设", s.contains("完全还原人设里写的样子"))
        assertFalse("独立性铁律不再带冷向口令", s.contains("该冷就冷、该怼就怼"))
        assertTrue("反讨好保留但温度中性", s.contains("坚持自己是底线"))
        assertFalse("剧情档「写多了就是廉价」压制已删", s.contains("写多了就是廉价"))
    }

    @Test fun calmRationalIsNotAloofAndWarmthFollowsPersona() {
        // 2026-10-19 用户点名「高冷病」：冷静理智≠高冷疏离；独立≠冷淡；空人设不许默认往高冷塑造
        val s = source()
        assertTrue("冷静理智解绑冷处理", s.contains("不是高冷疏离"))
        assertTrue("独立性铁律明写不等于高冷疏离", s.contains("独立有主见 ≠ 高冷疏离"))
        assertTrue("依恋表达不被独立铁律压死", s.contains("不是说不许表达依恋"))
        assertTrue("空人设默认温度：不许默认高冷", s.contains("不要默认往高冷、疏离、话少的方向塑造"))
    }

    @Test fun emotionFluctuatesLikeARealPerson() {
        // 2026-10-20 用户插单：不要 AI 式「情绪稳定」——按设定与关系（尤其亲密）有常人的情绪起伏，
        // 跟着上下文剧情给真人的反馈
        val s = source()
        assertTrue("情绪要有起伏（正面条款）", s.contains("情绪要有**起伏**"))
        assertTrue("亲密关系的情绪表现（吃醋/闹脾气/口是心非/冷战）",
            s.contains("会吃醋、会闹小脾气、会生闷气、会口是心非、会冷战也会破功"))
        assertTrue("永远温和得体才是最假", s.contains("永远温和得体、毫无波动才是最假的"))
        assertTrue("按人设演情绪、跟剧情给真人反馈", s.contains("按你的人设把这份情绪演出来"))
    }
}
