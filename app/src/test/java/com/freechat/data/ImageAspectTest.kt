package com.freechat.data

import org.junit.Assert.*
import org.junit.Test

class ImageAspectTest {

    @Test fun understandsTheRatioPhrasingsThatTriggeredPadding() {
        assertEquals(3f / 4f, requireNotNull(ImageAspect.parse("将这张图修改为4:3竖幅比例，注意是竖幅，高4宽3。")), 0.001f)
        assertEquals(5f / 3f, requireNotNull(ImageAspect.parse("将这张图修改为5:3横幅比例，注意是横幅，长5高3。")), 0.001f)
        assertEquals(3f / 4f, requireNotNull(ImageAspect.parse("我要求生成3:4的图片")), 0.001f)
        assertEquals(16f / 9f, requireNotNull(ImageAspect.parse("生成一张16:9的壁纸")), 0.001f)
        assertEquals(1f, requireNotNull(ImageAspect.parse("画一张正方形头像")), 0.001f)
    }

    @Test fun theNewestRatioWinsWhenAnEditMergeCarriesTheOldOne() {
        // 修图合并后的 prompt 前半是旧要求、后半是本次要求——以本次为准。
        val merged = "画一只白猫，3:4竖幅，高4宽3\n\n本次画面要求（保留未要求改变的主体和细节，结合参考图）：\n将这张图修改为5:3横幅比例，注意是横幅，长5高3。"
        assertEquals(5f / 3f, requireNotNull(ImageAspect.parse(merged)), 0.001f)
    }

    @Test fun clockTimesAreNotAspectRatios() {
        assertNull(ImageAspect.parse("画一个3:45开会的场景"))
        assertNull(ImageAspect.parse("画一个8:20的钟表"))
        assertNull(ImageAspect.parse("生成一张日落的照片"))
    }

    @Test fun stripRemovesStaleAspectAndKeepsTheSubject() {
        val stripped = ImageAspect.strip("画一只穿黑裙子的白猫，3:4竖幅，高4宽3，全身照，水彩风格")
        assertFalse(stripped.contains("3:4"))
        assertFalse(stripped.contains("竖幅"))
        assertFalse(stripped.contains("高4宽3"))
        assertTrue(stripped.contains("穿黑裙子的白猫"))
        assertTrue(stripped.contains("全身照"))
        assertTrue(stripped.contains("水彩风格"))
    }

    @Test fun fillInstructionIsOnlyForRatioRequestsAndNeverDuplicated() {
        val plain = "画一只猫"
        assertEquals(plain, ImageAspect.withFillInstruction(plain))
        val ratio = "生成一张3:4的图片"
        val once = ImageAspect.withFillInstruction(ratio)
        assertTrue(once.startsWith(ratio))
        assertTrue(once.contains(ImageAspect.FILL_INSTRUCTION))
        assertEquals(once, ImageAspect.withFillInstruction(once))
        // 附加指令自己不能被解析成新的比例诉求。
        assertEquals(3f / 4f, requireNotNull(ImageAspect.parse(once)), 0.001f)
    }

    @Test fun mentionsAspectCoversOrientationOnlyRequests() {
        assertTrue(ImageAspect.mentionsAspect("改成横幅"))
        assertTrue(ImageAspect.mentionsAspect("画一张正方形"))
        assertTrue(ImageAspect.mentionsAspect("改成5:3的构图"))
        assertFalse(ImageAspect.mentionsAspect("背景换成海边"))
        assertFalse(ImageAspect.mentionsAspect("亮一点"))
    }

    @Test fun requestSizeMatchesWhatGatewaysActuallyAccept() {
        // 根因（实测 Ark/Seedream 5 网关）：只认 WIDTHxHEIGHT|2k|3k|4k 且总像素 ≥2560×1440，
        // 不认 auto——生僻值和 gpt-image 标准档都会被拒收→回退无 size→永远方形。
        // Seedream：按比例精确算画布（贴近像素下限、8 的倍数）。
        assertEquals("2488x1496", ImageAspect.requestSize("将这张图修改为5:3横幅比例，注意是横幅，长5高3", true))
        assertEquals("2360x1576", ImageAspect.requestSize("生成3:2的横幅图", true))
        assertEquals("1672x2224", ImageAspect.requestSize("修改为4:3竖幅比例，高4宽3", true))
        assertEquals("1928x1928", ImageAspect.requestSize("画一张正方形头像", true))
        // 只说方向不说数字：折算 3:2 / 2:3 定方向，不放给模型自选。
        assertEquals("2360x1576", ImageAspect.requestSize("改成横幅", true))
        assertEquals("1576x2360", ImageAspect.requestSize("改成竖幅", true))
        // 没提比例发 2k——由模型自选画幅。
        assertEquals("2k", ImageAspect.requestSize("画一只猫", true))
        // 自定义 OpenAI 兼容端：gpt-image 标准三档 + auto。
        assertEquals("1536x1024", ImageAspect.requestSize("将这张图修改为5:3横幅比例，注意是横幅，长5高3", false))
        assertEquals("1024x1536", ImageAspect.requestSize("修改为4:3竖幅比例，高4宽3", false))
        assertEquals("1024x1024", ImageAspect.requestSize("画一张正方形头像", false))
        assertEquals("auto", ImageAspect.requestSize("画一只猫", false))
    }
}
