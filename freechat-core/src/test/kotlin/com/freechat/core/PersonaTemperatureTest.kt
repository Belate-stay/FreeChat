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
        assertTrue("害羞与生理反应按人设还原", s.contains("害羞与生理反应以用户明确设定为准"))
        assertTrue("肯定式强度与禁忌保留，但不要求机械展示", s.contains("保留写明的强度和禁忌"))
        assertFalse("「绝大多数回复根本不需要写」的全球压制已删", s.contains("绝大多数回复里根本不需要写"))
        assertFalse("「唯独不是脸红心跳」的禁令已删", s.contains("唯独不是脸红心跳"))
    }

    @Test fun noOppositeAdjectiveSteeringAndNeutralIndependence() {
        val s = source()
        assertFalse("「不要娇羞脸红那一套」的反面教材已删", s.contains("不要一律写成娇羞脸红那一套"))
        assertTrue("亲昵方式完全还原人设", s.contains("完全还原人设里写的样子"))
        assertFalse("独立性铁律不再带冷向口令", s.contains("该冷就冷、该怼就怼"))
        assertTrue("反讨好保留但温度中性", s.contains("不为证明独立而故意冷淡"))
        assertFalse("剧情档「写多了就是廉价」压制已删", s.contains("写多了就是廉价"))
    }

    @Test fun calmRationalIsNotAloofAndWarmthFollowsPersona() {
        // 2026-10-19 用户点名「高冷病」：冷静理智≠高冷疏离；独立≠冷淡；空人设不许默认往高冷塑造
        val s = source()
        assertTrue("冷静理智解绑冷处理", s.contains("不把克制写成冷漠"))
        assertTrue("独立性不预设温度", s.contains("不统一塑造成热情、疏离或寡言的人"))
        assertTrue("依恋表达不被独立铁律压死", s.contains("不把亲密、依恋或示弱一概视为失去自我"))
        assertTrue("不统一加热或降温", s.contains("不用故意加热或降温"))
    }

    @Test fun emotionFluctuatesLikeARealPerson() {
        // 2026-10-20 用户插单：不要 AI 式「情绪稳定」——按设定与关系（尤其亲密）有常人的情绪起伏，
        // 跟着上下文剧情给真人的反馈
        val s = source()
        assertTrue("情绪有因有续", s.contains("情绪可以延续，也可以因新的事情改变"))
        assertTrue("亲密关系的情绪表现（吃醋/闹脾气/口是心非/冷战）",
            s.contains("亲近、拒绝、依恋、吃醋、示弱各自要有这段关系和眼前事情的依据"))
        assertFalse("不再强迫情绪表演", s.contains("毫无波动才是最假的"))
        assertTrue("按事件和既有状态给反馈", s.contains("既有状态作出反应"))
    }
}
