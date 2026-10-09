package com.freechat.data

import com.freechat.model.CharacterProfile
import com.freechat.model.Message
import com.freechat.model.Role
import com.freechat.sync.ImageSync
import com.freechat.sync.Wire
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.URI
import java.util.Base64
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit

/** Ephemeral image inputs derived from retained messages, never a second stored scene list. */
object SceneReferenceSelector {
    private const val MAX_REFERENCES = 3
    private const val MAX_SCENE_REFERENCES = 2
    private const val MAX_OPTIONAL_LOAD_ATTEMPTS = 6
    private const val MAX_REFERENCE_BYTES = 16 * 1024 * 1024
    private val defaultHttpClient by lazy {
        OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS).retryOnConnectionFailure(false).build()
    }

    data class PreparedReference(
        val source: String,
        val reference: ImageApiRequest.Reference,
        val sceneMessageId: String? = null,
        internal val identities: Set<String> = emptySet()
    )

    private data class Candidate(val source: String, val identity: String)

    /** Standard-mode uploads and retained generated pictures use the same bounded reference reader. */
    fun prepareSources(sources: List<String>, httpClient: OkHttpClient = defaultHttpClient): List<ImageApiRequest.Reference> =
        canonicalCandidates(sources, optional = false).map { candidate ->
            readReference(candidate, null, httpClient)?.reference ?: throw SceneImageFailure.ReferenceFailure()
        }

    /** Blocking file/network reads: callers must use Dispatchers.IO, then revalidate before sending. */
    fun prepare(character: CharacterProfile, retainedHistory: List<Message>,
        deletedMessageIds: Set<String> = emptySet(), httpClient: OkHttpClient = defaultHttpClient): List<PreparedReference> {
        val profile = character.normalized()
        val characterCandidates = canonicalCandidates(profile.appearanceImagePaths, optional = false).take(MAX_REFERENCES)
        val continuity = profile.enhancedSceneContinuity && CharacterPresentationPolicy.usesImageGeneration(profile.dialogueMode)
        val required = mutableListOf<PreparedReference>()
        val seen = mutableSetOf<String>()
        val characterIdentities = mutableSetOf<String>()
        val remainingCharacters = characterCandidates.iterator()

        fun addCharacter(candidate: Candidate) {
            val prepared = readReference(candidate, null, httpClient) ?: throw SceneImageFailure.ReferenceFailure()
            characterIdentities.addAll(prepared.identities)
            if (prepared.identities.none { it in seen }) {
                required += prepared
                seen.addAll(prepared.identities)
            }
        }

        val initialCharacterLimit = if (continuity) MAX_REFERENCES - 1 else MAX_REFERENCES
        while (required.size < initialCharacterLimit && remainingCharacters.hasNext()) addCharacter(remainingCharacters.next())
        if (!continuity) return required

        val scenes = mutableListOf<PreparedReference>()
        val sceneLimit = minOf(MAX_SCENE_REFERENCES, MAX_REFERENCES - required.size)
        val attemptedSources = mutableSetOf<String>()
        var optionalAttempts = 0
        // Reverse first so later rows win when two visualizations have the same timestamp.
        val rows = retainedHistory.asReversed().asSequence().filter { eligibleScene(it, deletedMessageIds) }
            .sortedByDescending { it.timestamp }
        sceneLoop@ for (row in rows) {
            if (optionalAttempts >= MAX_OPTIONAL_LOAD_ATTEMPTS) break
            for (candidate in canonicalCandidates(row.imageUrls + row.imagePaths, optional = true)) {
                if (candidate.identity in characterIdentities || candidate.identity in seen) continue
                if (!attemptedSources.add(candidate.source)) continue
                if (optionalAttempts >= MAX_OPTIONAL_LOAD_ATTEMPTS) break@sceneLoop
                optionalAttempts++
                val prepared = readReference(candidate, row.id, httpClient) ?: continue
                if (prepared.identities.any { it in characterIdentities || it in seen }) continue
                scenes += prepared
                seen.addAll(prepared.identities)
                if (scenes.size >= sceneLimit) break@sceneLoop
            }
        }
        // An unavailable optional scene must not consume the third configured character slot.
        if (scenes.isEmpty()) {
            while (required.size < MAX_REFERENCES && remainingCharacters.hasNext()) addCharacter(remainingCharacters.next())
        }
        return required + scenes
    }

    /** Re-check the fresh store and tombstones on IO; image bytes alone are not retention evidence. */
    fun revalidate(prepared: List<PreparedReference>, retainedHistory: List<Message>,
        deletedMessageIds: Set<String> = emptySet()): List<PreparedReference> {
        val rows = retainedHistory.filter { eligibleScene(it, deletedMessageIds) }.associateBy { it.id }
        val currentCandidates = mutableMapOf<String, List<Candidate>>()
        return prepared.filter { item ->
            val id = item.sceneMessageId
            if (id == null) true
            else {
                val row = rows[id]
                row != null && currentCandidates.getOrPut(id) {
                    canonicalCandidates(row.imageUrls + row.imagePaths, optional = true)
                }.any { it.identity in item.identities }
            }
        }
    }

    private fun eligibleScene(row: Message, deleted: Set<String>): Boolean =
        row.id.isNotBlank() && row.id !in deleted && row.sceneVisualization && row.role == Role.ASSISTANT &&
            !row.failed && !row.isStreaming

    private fun canonicalCandidates(entries: List<String>, optional: Boolean): List<Candidate> {
        val groups = linkedMapOf<String, MutableList<String>>()
        for (entry in entries.map(::normalizeSource).filter { it.isNotBlank() }.distinct()) {
            if (optional && !canLoad(entry)) continue
            groups.getOrPut(identityOf(entry)) { mutableListOf() }.add(entry)
        }
        return groups.map { (identity, sources) ->
            // Invalid explicit character files remain required, without hashing oversized payloads.
            val canonical = sources.filter { canLoad(it) || validRef(it) }
            val source = if (canonical.isEmpty()) sources.first()
                else ImageSync.canonicalEntries(canonical).first()
            Candidate(source, identity)
        }
    }

    private fun normalizeSource(entry: String): String {
        val source = entry.trim()
        return when {
            source.startsWith("file:", ignoreCase = true) -> runCatching { File(URI(source)).absolutePath }.getOrDefault(source)
            isHttp(source) -> source.toHttpUrlOrNull()?.toString() ?: source
            else -> source
        }
    }

    private fun isHttp(source: String): Boolean =
        source.startsWith("https://", ignoreCase = true) || source.startsWith("http://", ignoreCase = true)

    private fun validRef(source: String): Boolean = ImageSync.isRef(source) &&
        ImageSync.hashOfRef(source).let { hash -> hash.length == 16 && hash.all { it in "0123456789abcdef" } }

    private fun localFile(source: String): File? = when {
        ImageSync.isRef(source) -> if (validRef(source)) ImageSync.cachePathIfPresent(ImageSync.hashOfRef(source))?.let(::File) else null
        isHttp(source) || source.startsWith("data:") -> null
        else -> File(source)
    }

    private fun readableFile(file: File?): Boolean = file != null && runCatching {
        file.isFile && file.length() in 1..MAX_REFERENCE_BYTES.toLong()
    }.getOrDefault(false)

    private fun canLoad(source: String): Boolean = when {
        isHttp(source) -> source.toHttpUrlOrNull() != null
        source.startsWith("data:image") -> dataImageBytes(source) != null
        else -> readableFile(localFile(source))
    }

    private fun identityOf(source: String): String {
        if (validRef(source)) return "ref:$source"
        if (source.startsWith("data:image")) {
            dataImageBytes(source)?.let { return "ref:${ImageSync.refOf(Wire.hashOf(it))}" }
        } else if (!isHttp(source)) {
            val file = localFile(source)
            file?.name?.let(ImageSync::hashFromCacheName)?.let { return "ref:${ImageSync.refOf(it)}" }
            if (readableFile(file)) ImageSync.refForLocal(file!!.absolutePath)?.let { return "ref:$it" }
        }
        return "entry:$source"
    }

    private fun readReference(candidate: Candidate, sceneMessageId: String?, httpClient: OkHttpClient): PreparedReference? {
        val reference = try {
            loadReference(candidate.source, httpClient)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        } ?: return null
        return PreparedReference(candidate.source, reference, sceneMessageId,
            setOf(candidate.identity, "ref:${ImageSync.refOf(Wire.hashOf(reference.bytes))}"))
    }

    private fun loadReference(source: String, httpClient: OkHttpClient): ImageApiRequest.Reference? {
        if (isHttp(source)) {
            httpClient.newCall(Request.Builder().url(source).get().build()).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body ?: return null
                if (body.contentLength() > MAX_REFERENCE_BYTES) return null
                val bytes = body.byteStream().use(::readBounded) ?: return null
                val contentType = response.header("Content-Type").orEmpty().substringBefore(';').trim().lowercase()
                if (!contentType.startsWith("image/") && detectedImageMime(bytes) == null) return null
                return ImageApiRequest.Reference(bytes, imageMime(source, bytes, contentType))
            }
        }
        if (source.startsWith("data:image")) {
            val bytes = dataImageBytes(source) ?: return null
            return ImageApiRequest.Reference(bytes, imageMime(source, bytes, source.substringAfter("data:").substringBefore(';')))
        }
        val file = localFile(source)
        if (!readableFile(file)) return null
        val bytes = file!!.inputStream().use(::readBounded) ?: return null
        return ImageApiRequest.Reference(bytes, imageMime(file.name, bytes))
    }

    private fun readBounded(input: InputStream): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (output.size().toLong() + count > MAX_REFERENCE_BYTES) return null
            output.write(buffer, 0, count)
        }
        return output.toByteArray().takeIf { it.isNotEmpty() }
    }

    private fun dataImageBytes(source: String): ByteArray? {
        val comma = source.indexOf(',')
        if (comma < 0) return null
        return runCatching {
            Base64.getDecoder().decode(source.substring(comma + 1).filterNot { it.isWhitespace() })
        }.getOrNull()?.takeIf { it.isNotEmpty() && it.size <= MAX_REFERENCE_BYTES }
    }

    private fun detectedImageMime(bytes: ByteArray): String? {
        if (bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(
                byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a))) return "image/png"
        if (bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte()) return "image/jpeg"
        if (bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
            String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP") return "image/webp"
        if (bytes.size >= 6 && String(bytes, 0, 6, Charsets.US_ASCII) in setOf("GIF87a", "GIF89a")) return "image/gif"
        return null
    }

    private fun imageMime(source: String, bytes: ByteArray, suggested: String = ""): String {
        detectedImageMime(bytes)?.let { return it }
        val mime = suggested.substringBefore(';').trim().lowercase()
        if (mime.startsWith("image/")) return mime
        return when (source.substringBefore('?').substringBefore('#').substringAfterLast('.').lowercase()) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            else -> "image/jpeg"
        }
    }
}
