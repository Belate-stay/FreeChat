package com.freechat.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.1.30X 换代契约：内置模型=DeepSeek-V4.1-Flash，深度思考与联网搜索默认开启；检索失败静默。 */
class CompanionModelDefaultsTest {

    @Test fun brainModelIsDeepSeekWithThinkingAndSearchDefaultOn() {
        val m = CompanionBrain.BRAIN_MODEL
        assertEquals("deepseek-flash", m.id)
        assertEquals("DeepSeek-V4.1-Flash", m.displayName)
        assertTrue("深度思考默认开启（能力位+默认位）", m.supportsDeepThinking && m.deepThinkingDefault)
        assertTrue("联网搜索默认开启（能力位）", m.supportsWebSearch)
    }

    @Test fun searchFailureOrEmptyYieldsNoBlockAndNeverThrows() {
        // 锦上添花语义：任何失败返回空串，回复生成不受影响
        assertEquals("", kotlinx.coroutines.runBlocking { BrainSearch.infoBlock(com.freechat.data.SearchIntent(emptyList())) })
    }
}
