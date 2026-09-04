package com.freechat.data

/**
 * PCM → WAV 编码工具（16kHz/16bit/单声道）。
 * 语音识别已改为用户自定义模型（OpenAI /v1/audio/transcriptions），见 ChatViewModel.transcribeAudioOpenAi。
 */
object MiMoAsr {
    /** 把 16kHz/16bit/单声道 PCM 转成 WAV（加 44 字节 RIFF 头） */
    fun pcmToWav(
        pcm: ByteArray,
        sampleRate: Int = 16000,
        channels: Int = 1,
        bitsPerSample: Int = 16
    ): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val dataSize = pcm.size
        val out = ByteArray(44 + dataSize)

        // RIFF 头
        out[0] = 'R'.code.toByte(); out[1] = 'I'.code.toByte(); out[2] = 'F'.code.toByte(); out[3] = 'F'.code.toByte()
        writeIntLE(out, 4, 36 + dataSize)
        out[8] = 'W'.code.toByte(); out[9] = 'A'.code.toByte(); out[10] = 'V'.code.toByte(); out[11] = 'E'.code.toByte()
        // fmt 子块
        out[12] = 'f'.code.toByte(); out[13] = 'm'.code.toByte(); out[14] = 't'.code.toByte(); out[15] = ' '.code.toByte()
        writeIntLE(out, 16, 16)               // fmt 块大小
        writeShortLE(out, 20, 1)              // PCM 编码
        writeShortLE(out, 22, channels)
        writeIntLE(out, 24, sampleRate)
        writeIntLE(out, 28, byteRate)
        writeShortLE(out, 32, blockAlign)
        writeShortLE(out, 34, bitsPerSample)
        // data 子块
        out[36] = 'd'.code.toByte(); out[37] = 'a'.code.toByte(); out[38] = 't'.code.toByte(); out[39] = 'a'.code.toByte()
        writeIntLE(out, 40, dataSize)
        System.arraycopy(pcm, 0, out, 44, dataSize)
        return out
    }

    private fun writeIntLE(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = (value and 0xFF).toByte()
        buf[offset + 1] = ((value shr 8) and 0xFF).toByte()
        buf[offset + 2] = ((value shr 16) and 0xFF).toByte()
        buf[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun writeShortLE(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = (value and 0xFF).toByte()
        buf[offset + 1] = ((value shr 8) and 0xFF).toByte()
    }
}
