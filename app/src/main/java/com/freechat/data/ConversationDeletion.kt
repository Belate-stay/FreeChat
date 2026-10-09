package com.freechat.data

import com.freechat.model.Conversation
import com.freechat.sync.ImageSync
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import java.io.File

/** Deleted conversation UUIDs are permanent: late API/sync snapshots must not recreate them. */
object ConversationDeletion {
    private var directory: File? = null
    private var deleted = emptySet<String>()
    private var pendingMedia = emptyMap<String, Set<String>>()
    private val fileFields = setOf("imagePaths", "imageUrls", "quotedImagePath", "attachmentPath",
        "avatarPath", "appearanceImagePath", "appearanceImagePaths")

    private fun ids(): Set<String> {
        val root = LocalStore.filesDir()
        if (directory != root) {
            directory = root
            val journal = json(LocalStore.readText(File(root, "freechat_deleted_conversations.json")) ?: "[]")
            deleted = runCatching {
                (if (journal?.isJsonObject == true) journal.asJsonObject.getAsJsonArray("ids") else journal?.asJsonArray)
                    ?.map { it.asString }?.toSet().orEmpty()
            }.getOrDefault(emptySet())
            pendingMedia = runCatching {
                journal?.takeIf { it.isJsonObject }?.asJsonObject?.getAsJsonObject("pendingMedia")?.entrySet()
                    ?.associate { (id, paths) -> id to paths.asJsonArray.map { it.asString }.toSet() }.orEmpty()
            }.getOrDefault(emptyMap())
        }
        return deleted
    }

    fun contains(id: String): Boolean = LocalStore.locked { id in ids() }

    fun reload() = LocalStore.locked { directory = null; deleted = emptySet(); pendingMedia = emptyMap() }

    fun pendingIds(): Set<String> = LocalStore.locked { ids(); pendingMedia.keys.toSet() }

    fun record(id: String, snapshot: Conversation? = null) = LocalStore.locked {
        if (id !in ids()) {
            val paths = linkedSetOf<String>()
            val rows = json(LocalStore.readText(LocalStore.conversationsFile()) ?: "[]")?.asJsonArray
            collectFiles(rows?.firstOrNull { it.asJsonObject.get("id")?.asString == id }, paths)
            collectFiles(snapshot?.let { AppJson.gson.toJsonTree(it) }, paths)
            collectFiles(json(LocalStore.readText(LocalStore.messagesFile(id)) ?: "[]"), paths)
            deleted = deleted + id
            pendingMedia = pendingMedia + (id to paths)
            saveJournal()
        }
    }

    private fun saveJournal() {
        LocalStore.writeText(File(LocalStore.filesDir(), "freechat_deleted_conversations.json"),
            AppJson.gson.toJson(mapOf("ids" to deleted, "pendingMedia" to pendingMedia)))
    }

    /** Enforced at the shared storage boundary, including writes from a second ViewModel. */
    fun fence(key: String, text: String): String? {
        val affected = key == LocalStore.CONVERSATIONS_KEY || key == LocalStore.PER_CONV_KEY ||
            key.startsWith("freechat:msgs:") || key.startsWith("freechat:memory:")
        if (!affected) return text
        val tombstones = ids()
        if (tombstones.isEmpty()) return text
        if (key.startsWith("freechat:msgs:") || key.startsWith("freechat:memory:")) {
            return text.takeUnless { key.substringAfterLast(':') in tombstones }
        }
        return runCatching {
            val json = JsonParser.parseString(text)
            if (key == LocalStore.CONVERSATIONS_KEY) {
                val kept = com.google.gson.JsonArray()
                json.asJsonArray.filterNot { it.asJsonObject.get("id")?.asString in tombstones }.forEach(kept::add)
                kept.toString()
            } else {
                json.asJsonObject.apply { tombstones.forEach(::remove) }.toString()
            }
        }.getOrDefault(text)
    }

    /** Call on IO. Returns cloud image identities whose last local reference was removed. */
    fun removeLocal(id: String, snapshot: Conversation? = null): Set<String> = LocalStore.locked {
        val rows = json(LocalStore.readText(LocalStore.conversationsFile()) ?: "[]")?.asJsonArray
        val own = rows?.firstOrNull { it.asJsonObject.get("id")?.asString == id }
            ?: snapshot?.let { AppJson.gson.toJsonTree(it) }
        val paths = linkedSetOf<String>()
        ids()
        paths.addAll(pendingMedia[id].orEmpty())
        collectFiles(own, paths)
        collectFiles(json(LocalStore.readText(LocalStore.messagesFile(id)) ?: "[]"), paths)
        val hashes = paths.mapNotNull { ImageSync.refForLocal(it)?.let(ImageSync::hashOfRef) }.toSet()
        record(id)
        if (rows != null) LocalStore.writeText(LocalStore.conversationsFile(), rows.toString())
        LocalStore.deleteFile(LocalStore.messagesFile(id))
        LocalStore.deleteFile(LocalStore.memoryFile(id))
        LocalStore.deleteFile(LocalStore.avatarHashFile(id))
        PerConvStore.remove(id)

        val survivorPaths = linkedSetOf<String>()
        collectFiles(json(LocalStore.readText(LocalStore.conversationsFile()) ?: "[]"), survivorPaths)
        LocalStore.messageConvIds().forEach { other ->
            collectFiles(json(LocalStore.readText(LocalStore.messagesFile(other)) ?: "[]"), survivorPaths)
        }
        val survivorHashes = survivorPaths.mapNotNull { ImageSync.refForLocal(it)?.let(ImageSync::hashOfRef) }.toSet()
        val unused = hashes - survivorHashes
        val root = LocalStore.filesDir().canonicalFile.toPath()
        for (path in paths) {
            val file = runCatching { File(path).canonicalFile }.getOrNull() ?: continue
            val hash = ImageSync.refForLocal(path)?.let(ImageSync::hashOfRef)
            if (path !in survivorPaths && (hash == null || hash in unused) && file.toPath().startsWith(root) && file.isFile) {
                check(file.delete()) { "Unable to delete conversation file ${file.name}" }
            }
        }
        for (hash in unused) {
            val cache = ImageSync.cacheFile(hash)
            if (cache.exists()) check(cache.delete()) { "Unable to delete image cache ${cache.name}" }
        }
        if (id in pendingMedia) { pendingMedia = pendingMedia - id; saveJournal() }
        unused
    }

    private fun json(text: String): JsonElement? = runCatching { JsonParser.parseString(text) }.getOrNull()

    private fun collectFiles(value: JsonElement?, out: MutableSet<String>, isFile: Boolean = false) {
        if (value == null || value.isJsonNull) return
        when {
            value.isJsonObject -> value.asJsonObject.entrySet().forEach { (key, child) -> collectFiles(child, out, key in fileFields) }
            value.isJsonArray -> value.asJsonArray.forEach { collectFiles(it, out, isFile) }
            isFile && value.isJsonPrimitive && value.asJsonPrimitive.isString -> value.asString.takeIf { it.isNotBlank() }?.let(out::add)
        }
    }
}
