package com.freechat.data

import com.freechat.model.ModelType
import java.io.ByteArrayInputStream
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class MiMoVoiceRequestTest {
    @Test
    fun oldSettingsDefaultToTheExistingStockVoice() {
        val config = MiMoVoiceConfig()
        val request = MiMoVoiceRequest.create("你好。", config)
        assertEquals("mimo-v2.5-tts", request["model"].asString)
        assertEquals("mimo_default", request.getAsJsonObject("audio")["voice"].asString)
        assertEquals("assistant", request.getAsJsonArray("messages")[0].asJsonObject["role"].asString)
        assertFalse(request["stream"].asBoolean)
    }

    @Test
    fun voiceDesignUsesThePromptAsAUserMessageAndDoesNotSendAStockVoice() {
        val request = MiMoVoiceRequest.create("晚安。", MiMoVoiceConfig(
            modelId = BuiltInVoiceModel.DESIGN_API_ID,
            designPrompt = "  温柔沉稳的年轻女声  ", speakerStyle = "慢一点"), "wav")
        val messages = request.getAsJsonArray("messages")
        assertEquals(BuiltInVoiceModel.DESIGN_API_ID, request["model"].asString)
        assertEquals("user", messages[0].asJsonObject["role"].asString)
        assertTrue(messages[0].asJsonObject["content"].asString.contains("温柔沉稳的年轻女声"))
        assertTrue(messages[0].asJsonObject["content"].asString.contains("慢一点"))
        assertEquals("晚安。", messages[1].asJsonObject["content"].asString)
        assertFalse(request.getAsJsonObject("audio").has("voice"))
        assertEquals("wav", request.getAsJsonObject("audio")["format"].asString)
        assertFalse(request["stream"].asBoolean)
    }

    @Test
    fun cloningUsesAValidatedAudioDataUrlAndOptionalUserStyle() {
        val sample = MiMoVoiceSample.create(wavFixture())
        val request = MiMoVoiceRequest.create("你好。", MiMoVoiceConfig(
            modelId = BuiltInVoiceModel.CLONE_API_ID, sample = sample, speakerStyle = "自然一点"))
        assertEquals("data:audio/wav;base64," + Base64.getEncoder().encodeToString(sample.bytes()),
            request.getAsJsonObject("audio")["voice"].asString)
        val messages = request.getAsJsonArray("messages")
        assertEquals("自然一点", messages[0].asJsonObject["content"].asString)
        assertEquals("你好。", messages[1].asJsonObject["content"].asString)
        assertFalse(request["stream"].asBoolean)
    }

    @Test
    fun cloningWithoutStyleStillSendsTheExactSpokenText() {
        val request = MiMoVoiceRequest.create("原文不能被润色", MiMoVoiceConfig(
            modelId = BuiltInVoiceModel.CLONE_API_ID, sample = MiMoVoiceSample.create(wavFixture())))
        val messages = request.getAsJsonArray("messages")
        assertEquals(1, messages.size())
        assertEquals("assistant", messages[0].asJsonObject["role"].asString)
        assertEquals("原文不能被润色", messages[0].asJsonObject["content"].asString)
        assertFalse(request.getAsJsonObject("audio").has("optimize_text_preview"))
    }

    @Test
    fun missingCustomInputsFailInsteadOfFallingBackToAStockVoice() {
        assertInvalid("音色描述") { MiMoVoiceRequest.create("你好", MiMoVoiceConfig(modelId = BuiltInVoiceModel.DESIGN_API_ID)) }
        assertInvalid("参考音频") { MiMoVoiceRequest.create("你好", MiMoVoiceConfig(modelId = BuiltInVoiceModel.CLONE_API_ID)) }
        assertInvalid("模型") { MiMoVoiceRequest.create("你好", MiMoVoiceConfig(modelId = "unrecognized-model")) }
    }

    @Test
    fun sampleFormatComesFromBytesAndUnsupportedOrTruncatedAudioIsRejected() {
        assertEquals("audio/wav", MiMoVoiceSample.create(wavFixture()).mimeType)
        assertEquals("audio/mpeg", MiMoVoiceSample.create(mp3Fixture()).mimeType)
        assertInvalid("MP3 或 WAV") { MiMoVoiceSample.create("not audio.mp3".toByteArray()) }
        assertInvalid("MP3 或 WAV") { MiMoVoiceSample.create("ID3".toByteArray()) }
        assertInvalid("MP3 或 WAV") { MiMoVoiceSample.create(wavFixture().copyOf(20)) }
        val forged = wavFixture().apply { this[40] = 127 }
        assertInvalid("MP3 或 WAV") { MiMoVoiceSample.create(forged) }
    }

    @Test
    fun base64LimitIsAppliedToEncodedBytesRatherThanRawBytes() {
        assertEquals(10L * 1024 * 1024, VoiceSamplePolicy.encodedSize(VoiceSamplePolicy.MAX_RAW_BYTES.toLong()))
        assertTrue(VoiceSamplePolicy.encodedSize(VoiceSamplePolicy.MAX_RAW_BYTES.toLong() + 1) > VoiceSamplePolicy.MAX_BASE64_BYTES)
        assertInvalid("10 MB") { MiMoVoiceSample.create(ByteArray(VoiceSamplePolicy.MAX_RAW_BYTES + 1)) }
        assertInvalid("10 MB") { VoiceSamplePolicy.read(ByteArrayInputStream(ByteArray(VoiceSamplePolicy.MAX_RAW_BYTES + 1))) }
    }

    @Test
    fun callerCannotMutateValidatedSampleOrItsHash() {
        val bytes = wavFixture()
        val sample = MiMoVoiceSample.create(bytes)
        val before = sample.contentHash
        bytes[44] = 18
        sample.bytes()[44] = 23
        assertEquals(before, sample.contentHash)
        assertNotEquals(before, MiMoVoiceSample.create(bytes).contentHash)
    }

    @Test
    fun cacheIdentitySeparatesModelsDescriptionsSamplesAndSpeakingStyles() {
        val stock = MiMoVoiceConfig()
        val design = MiMoVoiceConfig(modelId = BuiltInVoiceModel.DESIGN_API_ID, designPrompt = "清亮女声")
        val clone = MiMoVoiceConfig(modelId = BuiltInVoiceModel.CLONE_API_ID, sample = MiMoVoiceSample.create(wavFixture()))
        fun key(c: MiMoVoiceConfig, text: String = "Aa") = MiMoVoiceRequest.cacheIdentity("message", text, c)
        assertNotEquals(key(stock), key(design))
        assertNotEquals(key(design), key(design.copy(designPrompt = "沉稳男声")))
        assertNotEquals(key(clone), key(clone.copy(sample = MiMoVoiceSample.create(wavFixture(1)))))
        assertNotEquals(key(clone), key(clone.copy(speakerStyle = "耳语")))
        assertNotEquals(key(stock), key(stock, "BB")) // Java hashCode collision in the previous cache.
        assertEquals(key(clone), key(clone.copy(sample = MiMoVoiceSample.create(wavFixture()))))
    }

    @Test
    fun microphoneRecordingHasThreeSecondMinimumAndSixtySecondMaximum() {
        assertInvalid("至少 3 秒") { VoiceRecordingPolicy.wavFromPcm(ByteArray(3 * 32000 - 2)) }
        assertInvalid("最多 60 秒") { VoiceRecordingPolicy.wavFromPcm(ByteArray(60 * 32000 + 2)) }
        val wav = VoiceRecordingPolicy.wavFromPcm(ByteArray(3 * 32000))
        assertEquals(44 + 3 * 32000, wav.size)
        assertEquals("audio/wav", MiMoVoiceSample.create(wav).mimeType)
    }

    @Test
    fun builtInModelsHaveImmutableRoutesAndTheExistingKeyBinding() {
        val models = BuiltInVoiceModel.createAll("fixture-key")
        assertEquals(listOf(BuiltInVoiceModel.STOCK_ID, BuiltInVoiceModel.DESIGN_ID, BuiltInVoiceModel.CLONE_ID), models.map { it.id })
        models.forEach {
            assertTrue(it.isBuiltIn)
            assertEquals(ModelType.TTS, it.modelType)
            assertEquals("fixture-key", it.apiKey)
            assertEquals("https://api.xiaomimimo.com/v1", it.apiBaseUrl)
            assertFalse(ModelAccessPolicy.canEdit(it))
        }
        assertEquals(BuiltInVoiceModel.DESIGN_API_ID, BuiltInVoiceModel.apiModelId(BuiltInVoiceModel.DESIGN_ID))
    }

    @Test
    fun errorsDistinguishAuthenticationModelEntitlementQuotaAndNetwork() {
        assertEquals(MiMoTtsErrorKind.AUTHENTICATION, MiMoTtsErrorPolicy.httpError(401).kind)
        assertEquals(MiMoTtsErrorKind.MODEL_PERMISSION, MiMoTtsErrorPolicy.httpError(403).kind)
        assertEquals(MiMoTtsErrorKind.MODEL_PERMISSION, MiMoTtsErrorPolicy.httpError(400, "model_not_found").kind)
        assertEquals(MiMoTtsErrorKind.QUOTA, MiMoTtsErrorPolicy.httpError(429).kind)
        assertEquals(MiMoTtsErrorKind.NETWORK, MiMoTtsErrorPolicy.httpError(503).kind)
        assertEquals(MiMoTtsErrorKind.INVALID_CONFIGURATION, MiMoTtsErrorPolicy.httpError(400).kind)
    }

    @Test
    fun responseDecoderRequiresNonemptyAudioAndHandlesMissingChoices() {
        val encoded = Base64.getEncoder().encodeToString(wavFixture())
        assertArrayEquals(wavFixture(), MiMoVoiceResponse.decode("{\"choices\":[{\"message\":{\"audio\":{\"data\":\"$encoded\"}}}]}"))
        assertInvalid("没有返回音频") { MiMoVoiceResponse.decode("{\"choices\":[]}") }
        assertInvalid("没有返回音频") { MiMoVoiceResponse.decode("{\"choices\":[{\"message\":{\"audio\":{\"data\":\"\"}}}]}" ) }
    }

    private fun assertInvalid(fragment: String, block: () -> Unit) {
        try { block(); fail("Expected a clear validation error containing $fragment") }
        catch (error: VoiceConfigurationException) { assertTrue(error.message.orEmpty(), error.message.orEmpty().contains(fragment)) }
    }

    companion object {
        fun wavFixture(marker: Int = 0): ByteArray {
            val data = ByteArray(60)
            fun ascii(offset: Int, value: String) = value.toByteArray(Charsets.US_ASCII).copyInto(data, offset)
            fun le(offset: Int, value: Int, count: Int) { repeat(count) { data[offset + it] = (value ushr (it * 8)).toByte() } }
            ascii(0, "RIFF"); le(4, data.size - 8, 4); ascii(8, "WAVE")
            ascii(12, "fmt "); le(16, 16, 4); le(20, 1, 2); le(22, 1, 2)
            le(24, 16000, 4); le(28, 32000, 4); le(32, 2, 2); le(34, 16, 2)
            ascii(36, "data"); le(40, 16, 4); data[44] = marker.toByte()
            return data
        }

        fun mp3Fixture(): ByteArray = ByteArray(417).apply {
            this[0] = 0xff.toByte(); this[1] = 0xfb.toByte(); this[2] = 0x90.toByte(); this[3] = 0
        }
    }
}
