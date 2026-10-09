package com.freechat.data

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.Base64

class VoiceConfigurationException(message: String) : IllegalArgumentException(message)

data class MiMoVoiceConfig(
    val modelId: String = BuiltInVoiceModel.STOCK_API_ID,
    val stockVoice: String = "mimo_default",
    val designPrompt: String = "",
    val speakerStyle: String = "",
    val sample: MiMoVoiceSample? = null
)

/** A validated, immutable in-memory sample. Neither bytes nor a data URL belong in synced settings. */
class MiMoVoiceSample private constructor(private val audio: ByteArray, val mimeType: String) {
    val contentHash: String = voiceSha256(audio)
    val sizeBytes: Int get() = audio.size
    fun bytes(): ByteArray = audio.copyOf()
    internal fun dataUrl(): String = "data:$mimeType;base64," + Base64.getEncoder().encodeToString(audio)

    companion object {
        fun create(bytes: ByteArray): MiMoVoiceSample {
            if (VoiceSamplePolicy.encodedSize(bytes.size.toLong()) > VoiceSamplePolicy.MAX_BASE64_BYTES) {
                throw VoiceConfigurationException("参考音频的 Base64 编码不能超过 10 MB，请选择更短的音频。")
            }
            val type = VoiceSamplePolicy.detectMime(bytes)
                ?: throw VoiceConfigurationException("请选择完整有效的 MP3 或 WAV 参考音频。")
            return MiMoVoiceSample(bytes.copyOf(), type)
        }
    }
}

object VoiceSamplePolicy {
    const val MAX_BASE64_BYTES = 10 * 1024 * 1024
    const val MAX_RAW_BYTES = MAX_BASE64_BYTES / 4 * 3
    fun encodedSize(rawBytes: Long): Long = if (rawBytes < 0 || rawBytes > Long.MAX_VALUE / 4 - 2) Long.MAX_VALUE
        else ((rawBytes + 2) / 3) * 4

    /** Bound the read even if a provider omits or lies about the file size. */
    fun read(input: InputStream): MiMoVoiceSample {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            if (output.size().toLong() + count > MAX_RAW_BYTES) {
                throw VoiceConfigurationException("参考音频的 Base64 编码不能超过 10 MB，请选择更短的音频。")
            }
            output.write(buffer, 0, count)
        }
        return MiMoVoiceSample.create(output.toByteArray())
    }

    internal fun detectMime(bytes: ByteArray): String? = when {
        validWav(bytes) -> "audio/wav"
        validMp3(bytes) -> "audio/mpeg"
        else -> null
    }

    private fun ascii(bytes: ByteArray, offset: Int, text: String): Boolean =
        offset >= 0 && offset + text.length <= bytes.size && text.indices.all { bytes[offset + it].toInt() == text[it].code }

    private fun le(bytes: ByteArray, offset: Int, count: Int): Long {
        var value = 0L
        repeat(count) { value = value or ((bytes[offset + it].toLong() and 255L) shl (8 * it)) }
        return value
    }

    private fun validWav(bytes: ByteArray): Boolean {
        if (bytes.size < 44 || !ascii(bytes, 0, "RIFF") || !ascii(bytes, 8, "WAVE")) return false
        val end = le(bytes, 4, 4) + 8
        if (end > bytes.size || end < 44) return false
        var position = 12L
        var hasFormat = false
        var hasAudio = false
        while (position + 8 <= end) {
            val offset = position.toInt()
            val length = le(bytes, offset + 4, 4)
            val contentStart = position + 8
            if (contentStart + length > end) return false
            if (ascii(bytes, offset, "fmt ")) {
                if (length < 16) return false
                val format = le(bytes, offset + 8, 2)
                val channels = le(bytes, offset + 10, 2)
                val sampleRate = le(bytes, offset + 12, 4)
                val blockAlign = le(bytes, offset + 20, 2)
                val bits = le(bytes, offset + 22, 2)
                hasFormat = format in setOf(1L, 3L, 0xfffeL) && channels in 1..32 &&
                    sampleRate in 8000..384000 && blockAlign > 0 && bits in setOf(8L, 16L, 24L, 32L, 64L)
                if (!hasFormat) return false
            }
            if (ascii(bytes, offset, "data")) hasAudio = length > 0
            position = contentStart + length + (length and 1L)
        }
        return hasFormat && hasAudio
    }

    private fun validMp3(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        var offset = 0
        if (ascii(bytes, 0, "ID3")) {
            if (bytes.size < 10 || (6..9).any { bytes[it].toInt() and 0x80 != 0 }) return false
            val tagSize = (6..9).fold(0L) { size, index -> (size shl 7) or (bytes[index].toLong() and 0x7f) }
            val total = 10 + tagSize + if (bytes[5].toInt() and 0x10 != 0) 10 else 0
            if (total > bytes.size - 4) return false
            offset = total.toInt()
        }
        val a = bytes[offset].toInt() and 255
        val b = bytes[offset + 1].toInt() and 255
        val c = bytes[offset + 2].toInt() and 255
        val version = (b shr 3) and 3
        val layer = (b shr 1) and 3
        val bitrateIndex = (c shr 4) and 15
        val sampleRateIndex = (c shr 2) and 3
        if (a != 255 || b and 0xe0 != 0xe0 || version == 1 || layer != 1 ||
            bitrateIndex !in 1..14 || sampleRateIndex == 3) return false
        val bitrates = if (version == 3) intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320)
            else intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160)
        val baseRate = intArrayOf(44100, 48000, 32000)[sampleRateIndex]
        val sampleRate = baseRate / when (version) { 3 -> 1; 2 -> 2; else -> 4 }
        val frameSize = (if (version == 3) 144 else 72) * bitrates[bitrateIndex] * 1000 / sampleRate + ((c shr 1) and 1)
        return frameSize >= 4 && offset.toLong() + frameSize <= bytes.size
    }
}

object VoiceRecordingPolicy {
    const val MIN_SECONDS = 3
    const val MAX_SECONDS = 60
    const val PCM_BYTES_PER_SECOND = 32000

    fun wavFromPcm(pcm: ByteArray): ByteArray {
        if (pcm.size < MIN_SECONDS * PCM_BYTES_PER_SECOND) throw VoiceConfigurationException("录音需要至少 3 秒，请再录一段清晰的人声。")
        if (pcm.size > MAX_SECONDS * PCM_BYTES_PER_SECOND) throw VoiceConfigurationException("录音最多 60 秒，请缩短录音。")
        if (pcm.size % 2 != 0) throw VoiceConfigurationException("录音数据不完整，请重新录制。")
        val buffer = ByteBuffer.allocate(44 + pcm.size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII)).putInt(36 + pcm.size)
        buffer.put("WAVEfmt ".toByteArray(Charsets.US_ASCII)).putInt(16).putShort(1).putShort(1)
        buffer.putInt(16000).putInt(32000).putShort(2).putShort(16)
        buffer.put("data".toByteArray(Charsets.US_ASCII)).putInt(pcm.size).put(pcm)
        return buffer.array()
    }
}

object MiMoVoiceRequest {
    fun validate(config: MiMoVoiceConfig) {
        val modelId = BuiltInVoiceModel.apiModelId(config.modelId)
        if (config.designPrompt.length > 4000 || config.speakerStyle.length > 1000) {
            throw VoiceConfigurationException("音色描述或说话风格过长，请缩短后再创建。")
        }
        if (modelId == BuiltInVoiceModel.DESIGN_API_ID && config.designPrompt.isBlank()) {
            throw VoiceConfigurationException("请先填写音色描述，再创建音色。")
        }
        if (modelId == BuiltInVoiceModel.CLONE_API_ID && config.sample == null) {
            throw VoiceConfigurationException("请先上传或录制参考音频，再创建音色。")
        }
    }

    fun create(text: String, config: MiMoVoiceConfig, format: String = "mp3"): JsonObject {
        validate(config)
        if (text.isBlank()) throw VoiceConfigurationException("请先输入需要朗读的文本。")
        if (format !in setOf("mp3", "wav")) throw VoiceConfigurationException("不支持的语音输出格式。")
        val modelId = BuiltInVoiceModel.apiModelId(config.modelId)
        val audio = JsonObject().apply { addProperty("format", format) }
        val instruction = when (modelId) {
            BuiltInVoiceModel.DESIGN_API_ID -> {
                if (config.designPrompt.isBlank()) throw VoiceConfigurationException("请先填写音色描述，再创建音色。")
                listOf(config.designPrompt.trim(), config.speakerStyle.trim()).filter { it.isNotEmpty() }.joinToString("\n")
            }
            BuiltInVoiceModel.CLONE_API_ID -> {
                val sample = config.sample ?: throw VoiceConfigurationException("请先上传或录制参考音频，再创建音色。")
                audio.addProperty("voice", sample.dataUrl())
                config.speakerStyle.trim()
            }
            else -> {
                audio.addProperty("voice", config.stockVoice.ifBlank { "mimo_default" })
                config.speakerStyle.trim()
            }
        }
        val messages = JsonArray().apply {
            if (instruction.isNotBlank()) add(JsonObject().apply {
                addProperty("role", "user"); addProperty("content", instruction)
            })
            add(JsonObject().apply { addProperty("role", "assistant"); addProperty("content", text) })
        }
        return JsonObject().apply {
            addProperty("model", modelId); add("messages", messages); add("audio", audio)
            addProperty("stream", false)
        }
    }

    fun cacheIdentity(messageId: String, text: String, config: MiMoVoiceConfig): String {
        val fields = listOf(messageId, text, BuiltInVoiceModel.apiModelId(config.modelId), config.stockVoice,
            config.designPrompt, config.speakerStyle, config.sample?.contentHash.orEmpty())
        return voiceSha256(fields.joinToString("") { "${it.length}:$it" }.toByteArray(Charsets.UTF_8))
    }
}

enum class MiMoTtsErrorKind { AUTHENTICATION, MODEL_PERMISSION, QUOTA, NETWORK, INVALID_CONFIGURATION, INVALID_RESPONSE, PLAYBACK, UNKNOWN }
data class MiMoTtsError(val kind: MiMoTtsErrorKind, val message: String)

object MiMoTtsErrorPolicy {
    fun httpError(status: Int, providerCode: String? = null): MiMoTtsError = when {
        status == 401 -> MiMoTtsError(MiMoTtsErrorKind.AUTHENTICATION, "MiMo 语音鉴权失败，当前应用的语音密钥不可用。")
        status == 403 || status == 404 || providerCode?.lowercase() in setOf("model_not_found", "permission_denied", "model_not_allowed", "model_access_denied") ->
            MiMoTtsError(MiMoTtsErrorKind.MODEL_PERMISSION, "当前 MiMo 账号未开放所选语音模型，暂时无法创建或朗读这个音色。")
        status == 402 || status == 429 || providerCode?.lowercase() in setOf("insufficient_quota", "quota_exceeded", "rate_limit_exceeded") ->
            MiMoTtsError(MiMoTtsErrorKind.QUOTA, "MiMo 语音额度不足或请求过于频繁，请稍后再试。")
        status >= 500 || status == 408 -> MiMoTtsError(MiMoTtsErrorKind.NETWORK, "MiMo 语音服务暂时不可用，请稍后再试。")
        status in 400..499 -> MiMoTtsError(MiMoTtsErrorKind.INVALID_CONFIGURATION, "MiMo 未接受这次音色请求，请检查描述或参考音频。")
        else -> MiMoTtsError(MiMoTtsErrorKind.UNKNOWN, "MiMo 语音创建失败，请稍后重试。")
    }
}

object MiMoVoiceResponse {
    fun providerErrorCode(body: String): String? = runCatching {
        JsonParser.parseString(body).asJsonObject.getAsJsonObject("error")?.get("code")?.asString
    }.getOrNull()?.take(100)

    fun hasProviderError(body: String): Boolean = runCatching {
        JsonParser.parseString(body).asJsonObject.has("error")
    }.getOrDefault(false)

    fun decode(body: String): ByteArray {
        val decoded = runCatching {
            val choices = JsonParser.parseString(body).asJsonObject.getAsJsonArray("choices")
            val data = choices?.takeIf { it.size() > 0 }?.get(0)?.asJsonObject
                ?.getAsJsonObject("message")?.getAsJsonObject("audio")?.get("data")?.asString
            if (data.isNullOrBlank()) null else Base64.getDecoder().decode(data).takeIf { it.isNotEmpty() }
        }.getOrNull()
        return decoded ?: throw VoiceConfigurationException("MiMo 没有返回音频，请重新创建或检查模型权限。")
    }
}

internal fun voiceSha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
    .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
