package com.freechat.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class VoiceCreationMode { PROMPT, AUDIO }

/** No absolute path, URI permission, or base64 is persisted in this descriptor. */
data class VoiceSampleRef(val fileName: String, val mimeType: String, val contentHash: String, val sizeBytes: Int)

data class VoiceCreationPreview(
    val mode: VoiceCreationMode,
    val sourcePrompt: String,
    val speakerStyle: String,
    val reference: VoiceSampleRef,
    val preview: VoiceSampleRef
)

data class VoicePreset(
    val id: String,
    val name: String,
    val mode: VoiceCreationMode,
    val sourcePrompt: String,
    val speakerStyle: String,
    val reference: VoiceSampleRef,
    val preview: VoiceSampleRef,
    val createdAtEpochMillis: Long
)

/**
 * Named voices are local presets, not permanent provider voice IDs. On Android all personal
 * metadata and audio live in noBackupFilesDir, outside settings sync, exports and OS backups.
 */
class VoicePresetRepository(private val root: File) {
    private val gson = Gson()
    private val mutex = Mutex()
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val draftLeases = mutableMapOf<String, Int>()
    private val samplesDirectory = File(root, "samples")
    private val manifest = File(root, "presets.json")
    private var readFailure: String? = null
    private val _presets = MutableStateFlow(readPresets())
    val presets: StateFlow<List<VoicePreset>> = _presets.asStateFlow()
    private val _storageError = MutableStateFlow(readFailure)
    val storageError: StateFlow<String?> = _storageError.asStateFlow()

    companion object {
        const val MAX_PRESETS = 20
        private val instances = mutableMapOf<String, VoicePresetRepository>()

        @Synchronized
        fun get(context: Context): VoicePresetRepository {
            val directory = File(context.applicationContext.noBackupFilesDir, "mimo_custom_voices")
            return instances.getOrPut(directory.absolutePath) { VoicePresetRepository(directory) }
        }
    }

    suspend fun importSample(input: InputStream): VoiceSampleRef {
        var staged: VoiceSampleRef? = null
        try {
            return withContext(Dispatchers.IO) {
                val sample = input.use { VoiceSamplePolicy.read(it) }
                mutex.withLock { storeSample(sample).also { lease(it); staged = it } }
            }
        } catch (cancelled: CancellationException) {
            staged?.let { discardLater(setOf(it)) }
            throw cancelled
        }
    }

    suspend fun readSample(reference: VoiceSampleRef): MiMoVoiceSample = withContext(Dispatchers.IO) {
        mutex.withLock { readSampleLocked(reference) }
    }

    /** Called only after a successful, explicit creation request. It does not create a named preset. */
    suspend fun preparePreview(
        mode: VoiceCreationMode,
        sourcePrompt: String,
        speakerStyle: String,
        reference: VoiceSampleRef?,
        audio: ByteArray
    ): VoiceCreationPreview {
        var staged: VoiceSampleRef? = null
        try { return withContext(Dispatchers.IO) {
        if (sourcePrompt.length > 4000 || speakerStyle.length > 1000) {
            throw VoiceConfigurationException("音色描述或说话风格过长，请缩短后再创建。")
        }
        if (mode == VoiceCreationMode.PROMPT && sourcePrompt.isBlank()) {
            throw VoiceConfigurationException("请先填写音色描述。")
        }
        val sample = MiMoVoiceSample.create(audio)
        mutex.withLock {
            val original = if (mode == VoiceCreationMode.AUDIO) {
                val ref = reference ?: throw VoiceConfigurationException("缺少参考音频，请重新上传或录制。")
                readSampleLocked(ref)
                ref
            } else null
            val previewRef = storeSample(sample)
            if (previewRef != original) { lease(previewRef); staged = previewRef }
            VoiceCreationPreview(mode, sourcePrompt.trim(), speakerStyle.trim(), original ?: previewRef, previewRef)
        }
        } } catch (cancelled: CancellationException) {
            staged?.let { discardLater(setOf(it)) }
            throw cancelled
        }
    }

    /** Commit the preset; its UI keeps draft leases until completion or cancellation cleanup. */
    suspend fun save(name: String, preview: VoiceCreationPreview): VoicePreset = withContext(Dispatchers.IO) {
        mutex.withLock {
            val label = name.trim()
            if (label.isBlank() || label.length > 40) throw VoiceConfigurationException("音色名称需要 1～40 个字符。")
            if (_presets.value.size >= MAX_PRESETS) throw VoiceConfigurationException("本机最多保存 20 个音色，请先删除不再使用的音色。")
            if (_presets.value.any { it.name.equals(label, ignoreCase = true) }) {
                throw VoiceConfigurationException("已有同名音色，请换一个名称。")
            }
            readSampleLocked(preview.reference)
            readSampleLocked(preview.preview)
            val preset = VoicePreset(UUID.randomUUID().toString(), label, preview.mode, preview.sourcePrompt,
                preview.speakerStyle, preview.reference, preview.preview, System.currentTimeMillis())
            val next = _presets.value + preset
            writePresets(next)
            _presets.value = next
            preset
        }
    }

    suspend fun playbackConfig(presetId: String): MiMoVoiceConfig = withContext(Dispatchers.IO) {
        mutex.withLock {
            val preset = _presets.value.find { it.id == presetId }
                ?: throw VoiceConfigurationException("这个本机音色已不存在，请重新选择音色。")
            MiMoVoiceConfig(modelId = BuiltInVoiceModel.CLONE_API_ID, designPrompt = preset.sourcePrompt,
                speakerStyle = preset.speakerStyle, sample = readSampleLocked(preset.reference))
        }
    }

    suspend fun delete(presetId: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val target = _presets.value.find { it.id == presetId } ?: return@withLock
            val next = _presets.value.filterNot { it.id == presetId }
            writePresets(next)
            _presets.value = next
            deleteUnreferencedSamplesLocked(setOf(target.reference, target.preview))
        }
    }

    suspend fun discardSamples(references: Set<VoiceSampleRef>) = withContext(Dispatchers.IO) {
        mutex.withLock { discardLocked(references) }
    }

    /** Screen disposal must release drafts even after its composition coroutine scope was cancelled. */
    fun discardLater(references: Set<VoiceSampleRef>) {
        if (references.isNotEmpty()) cleanupScope.launch { discardSamples(references) }
    }

    private fun discardLocked(references: Set<VoiceSampleRef>) {
        references.forEach { releaseLease(it) }
        deleteUnreferencedSamplesLocked(references)
    }

    private fun deleteUnreferencedSamplesLocked(references: Set<VoiceSampleRef>) {
        val savedFiles = _presets.value.flatMap { listOf(it.reference.fileName, it.preview.fileName) }.toSet()
        references.forEach { reference ->
            if (reference.fileName !in savedFiles && reference.fileName !in draftLeases) {
                runCatching { checkedSampleFile(reference).delete() }
            }
        }
    }

    private fun lease(reference: VoiceSampleRef) {
        draftLeases[reference.fileName] = (draftLeases[reference.fileName] ?: 0) + 1
    }

    private fun releaseLease(reference: VoiceSampleRef) {
        val count = draftLeases[reference.fileName] ?: return
        if (count <= 1) draftLeases.remove(reference.fileName) else draftLeases[reference.fileName] = count - 1
    }

    private fun checkedSampleFile(reference: VoiceSampleRef): File {
        val expectedExtension = when (reference.mimeType) {
            "audio/wav" -> "wav"
            "audio/mpeg" -> "mp3"
            else -> throw VoiceConfigurationException("参考音频格式无效，请重新上传或录制。")
        }
        if (!reference.contentHash.matches(Regex("[0-9a-f]{64}")) ||
            reference.fileName != "${reference.contentHash}.$expectedExtension" ||
            reference.sizeBytes !in 1..VoiceSamplePolicy.MAX_RAW_BYTES) {
            throw VoiceConfigurationException("参考音频记录无效，请重新上传或录制。")
        }
        val directory = samplesDirectory.canonicalFile
        val file = File(directory, reference.fileName).canonicalFile
        if (file.parentFile != directory) throw VoiceConfigurationException("参考音频位置无效，请重新选择音色。")
        return file
    }

    private fun readSampleLocked(reference: VoiceSampleRef): MiMoVoiceSample {
        val file = checkedSampleFile(reference)
        if (!file.isFile || file.length() != reference.sizeBytes.toLong()) {
            throw VoiceConfigurationException("本机参考音频已丢失或发生变化，请重新创建这个音色。")
        }
        val sample = try { file.inputStream().use { VoiceSamplePolicy.read(it) } }
            catch (_: Exception) { throw VoiceConfigurationException("本机参考音频无法读取，请重新创建这个音色。") }
        if (sample.contentHash != reference.contentHash || sample.mimeType != reference.mimeType) {
            throw VoiceConfigurationException("本机参考音频已发生变化，请重新创建这个音色。")
        }
        return sample
    }

    private fun storeSample(sample: MiMoVoiceSample): VoiceSampleRef {
        if (!samplesDirectory.isDirectory && !samplesDirectory.mkdirs()) {
            throw VoiceConfigurationException("无法保存本机参考音频，请检查可用存储空间。")
        }
        val extension = if (sample.mimeType == "audio/wav") "wav" else "mp3"
        val ref = VoiceSampleRef("${sample.contentHash}.$extension", sample.mimeType, sample.contentHash, sample.sizeBytes)
        val file = checkedSampleFile(ref)
        if (!file.isFile || runCatching { readSampleLocked(ref) }.isFailure) writeAtomic(file, sample.bytes())
        return ref
    }

    private fun readPresets(): List<VoicePreset> {
        if (!manifest.exists()) return emptyList()
        return try {
            if (manifest.length() > 1024 * 1024) throw IllegalArgumentException("oversized metadata")
            val type = object : TypeToken<List<VoicePreset>>() {}.type
            val loaded: List<VoicePreset> = gson.fromJson(manifest.readText(Charsets.UTF_8), type)
                ?: throw IllegalArgumentException("missing metadata")
            if (loaded.size > MAX_PRESETS || loaded.map { it.id }.toSet().size != loaded.size) {
                throw IllegalArgumentException("invalid metadata")
            }
            loaded.forEach { preset ->
                require(preset.id.isNotBlank() && preset.name.isNotBlank() && preset.name.length <= 40)
                require(preset.sourcePrompt.length <= 4000 && preset.speakerStyle.length <= 1000)
                checkedSampleFile(preset.reference); checkedSampleFile(preset.preview)
            }
            loaded
        } catch (_: Exception) {
            readFailure = "本机音色列表无法读取。原有文件已保留，请检查存储空间后重新打开应用。"
            emptyList()
        }
    }

    private fun writePresets(presets: List<VoicePreset>) {
        readFailure?.let { throw VoiceConfigurationException(it) }
        if (!root.isDirectory && !root.mkdirs()) throw VoiceConfigurationException("无法保存本机音色列表。")
        writeAtomic(manifest, gson.toJson(presets).toByteArray(Charsets.UTF_8))
    }

    private fun writeAtomic(file: File, bytes: ByteArray) {
        val temporary = File.createTempFile("voice-write-", ".tmp", file.parentFile)
        try {
            temporary.outputStream().use { output -> output.write(bytes); output.flush() }
            try { Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) }
            catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (_: Exception) {
            throw VoiceConfigurationException("本机音色保存失败，请检查可用存储空间后重试。")
        } finally { temporary.delete() }
    }
}
