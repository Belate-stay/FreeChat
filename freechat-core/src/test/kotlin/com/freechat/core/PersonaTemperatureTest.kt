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
}
