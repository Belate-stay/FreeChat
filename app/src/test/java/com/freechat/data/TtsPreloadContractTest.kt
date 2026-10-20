package com.freechat.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2026-10-19 语音返修契约：
 *  · 切段**合并成段**——旧版一句一段，30s 语音 ≈ 十个请求并行打出去被限流 429
 *    （「额度不足或请求过于频繁」的真凶）；合并后 30s ≈ 2 段、60s ≈ 3 段。
 *  · 预加载 preload + 磁盘缓存（自动下载到本地）：AI 发语音即合成落盘，点按命中缓存秒播。
 *  · 退避重试 synthesizeWithRetry：瞬时限流可恢复，鉴权/权限类不重试。
 */
class TtsPreloadContractTest {

    @Test fun longSpeechSplitsIntoFewSegmentsNotOnePerSentence() {
        // 约 10 句、200+ 字 ≈ 一分钟的语音：旧切法 = 10+ 段
        val text = (1..10).joinToString("") { "这是第${it}句话，说完就换下一句，聊得挺顺的。" }
        val segs = TtsController.splitForSpeech(text)
        assertTrue("200+ 字十句话应合并成 3 段以内，实得 ${segs.size}", segs.size <= 3)
        assertTrue("段数不能为 0", segs.isNotEmpty())
        assertTrue("每段不超 MAX_SEGMENT(120)，实得 ${segs.maxOf { it.length }}", segs.all { it.length <= 120 })
        // 首段限短保秒播
        assertTrue("首段 ≤30 字秒播，实得 ${segs.first().length}", segs.first().length <= 30)
        // 文本不丢
        assertTrue("切段不吞字", segs.joinToString("").replace(" ", "") == text.replace(" ", ""))
    }

    @Test fun shortVoiceIsSingleRequest() {
        val segs = TtsController.splitForSpeech("好呀，那我等你。")
        assertTrue("短语音只应 1 段，实得 ${segs.size}", segs.size == 1)
    }

    @Test fun preloadAndDiskCacheWiringPins() {
        val tts = File("src/main/java/com/freechat/data/TtsController.kt").readText()
        assertTrue("预加载入口在", tts.contains("fun preload(messageId: String, text: String, config: MiMoVoiceConfig)"))
        // 2026-10-19 随点随播返修：预载串成一条队（同刻只合成一条），点播进行中队列让路——
        // 旧版每条各起流水线=开对话几十路并发=429 风暴连点播一起拖死
        assertTrue("预载单条队列", tts.contains("PreloadTask") && tts.contains("preloadQueue"))
        assertTrue("点播优先（预载让路）", tts.contains("speakSynthesizing"))
        assertTrue("让路后不重复烧额度", tts.contains("收工，别重复烧额度"))
        assertTrue("磁盘缓存读写在", tts.contains("fun readDiskCache") && tts.contains("fun writeDiskCache"))
        assertTrue("点播也走磁盘缓存", tts.contains("readDiskCache(cacheKey)?.also"))
        assertTrue("退避重试在", tts.contains("fun synthesizeWithRetry"))
        assertTrue("错误静默路径（预加载不打扰用户）", tts.contains("silent: Boolean = false"))
        assertTrue("内存缓存线程安全收口", tts.contains("fun cacheGet") && tts.contains("fun cachePut"))
        val vm = File("src/main/java/com/freechat/viewmodel/ChatViewModel.kt").readText()
        assertTrue("AI 发语音即预载", vm.contains("voiceLine?.let { preloadVoiceMessage(convId, segId, it.text, it.style) }"))
        assertTrue("打开对话预载近期语音", vm.contains("preloadVoiceMessages(conversation.id, _messages.value)"))
        val main = File("src/main/java/com/freechat/MainActivity.kt").readText()
        assertTrue("磁盘缓存挂载", main.contains("TtsController.attach(applicationContext)"))
    }
}
