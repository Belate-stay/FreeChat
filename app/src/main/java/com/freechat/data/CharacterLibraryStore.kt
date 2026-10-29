package com.freechat.data

import com.freechat.model.CharacterCard
import com.freechat.model.CharacterFolder
import com.freechat.model.CharacterLibraryState
import com.freechat.model.CharacterPersona
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.io.File
import com.freechat.sync.ImageSync

/** A single locked read/modify/write keeps foreground edits and sync from overwriting each other. */
object CharacterLibraryStore {
    private val mutableState = MutableStateFlow(CharacterLibraryState())
    val state: StateFlow<CharacterLibraryState> = mutableState.asStateFlow()

    fun reload() = LocalStore.locked { mutableState.value = read() }
    fun snapshot(): CharacterLibraryState = LocalStore.locked { read() }

    private fun read(): CharacterLibraryState {
        val text = LocalStore.readText(LocalStore.characterLibraryFile()) ?: return CharacterLibraryState()
        val saved = AppJson.gson.fromJson(text, CharacterLibraryState::class.java) ?: return CharacterLibraryState()
        return normalized(saved)
    }

    @Suppress("USELESS_ELVIS")
    private fun normalized(value: CharacterLibraryState): CharacterLibraryState {
        val deletedCards = value.deletedCardIds ?: emptySet()
        val deletedFolders = value.deletedFolderIds ?: emptySet()
        return CharacterLibraryState(
            cards = (value.cards ?: emptyList()).filter { it.id.isNotBlank() && it.id !in deletedCards }
                .distinctBy { it.id }.map { if (it.folderId in deletedFolders) it.copy(folderId = null) else it },
            folders = (value.folders ?: emptyList()).filter { it.id.isNotBlank() && it.id !in deletedFolders }.distinctBy { it.id },
            deletedCardIds = deletedCards, deletedFolderIds = deletedFolders,
        )
    }

    private fun owner(): String = LocalStore.readText(LocalStore.characterLibraryFile())?.let {
        com.google.gson.JsonParser.parseString(it).asJsonObject.get("_accountId")?.asString
    }.orEmpty()

    private fun save(value: CharacterLibraryState, ownerId: String = owner()) {
        val next = normalized(value)
        val document = AppJson.gson.toJsonTree(next).asJsonObject
        if (ownerId.isNotBlank()) document.addProperty("_accountId", ownerId)
        val json = document.toString()
        LocalStore.writeText(LocalStore.characterLibraryFile(), json)
        check(LocalStore.readText(LocalStore.characterLibraryFile()) == json) { "Unable to save character library" }
        mutableState.value = next
    }

    fun add(persona: CharacterPersona, folderId: String? = null,
        sourceConversationId: String? = null, sourceMemberId: String? = null): CharacterCard = LocalStore.locked {
        require(persona.name.isNotBlank())
        val current = read()
        if (!sourceConversationId.isNullOrBlank()) {
            current.cards.firstOrNull { it.sourceConversationId == sourceConversationId && it.sourceMemberId == sourceMemberId }
                ?.let { return@locked it }
        }
        val targetFolder = folderId?.takeUnless { it in current.deletedFolderIds }
        require(targetFolder == null || current.folders.any { it.id == targetFolder })
        val card = CharacterCard(UUID.randomUUID().toString(), persona, targetFolder,
            sourceConversationId = sourceConversationId, sourceMemberId = sourceMemberId)
        save(current.copy(cards = current.cards + card))
        card
    }

    fun update(card: CharacterCard): Boolean = LocalStore.locked {
        val current = read()
        val previous = current.cards.firstOrNull { it.id == card.id } ?: return@locked false
        require(card.persona.name.isNotBlank())
        val incoming = if (card.folderId in current.deletedFolderIds) card.copy(folderId = null) else card
        require(incoming.folderId == null || current.folders.any { it.id == incoming.folderId } || incoming.folderId == previous.folderId)
        save(current.copy(cards = current.cards.map { if (it.id == card.id)
            incoming.copy(createdAt = previous.createdAt, updatedAt = maxOf(System.currentTimeMillis(), previous.updatedAt + 1)) else it }))
        releaseMedia(previous.persona)
        true
    }

    fun deleteCard(id: String) = LocalStore.locked {
        val current = read()
        save(current.copy(cards = current.cards.filterNot { it.id == id }, deletedCardIds = current.deletedCardIds + id))
        current.cards.firstOrNull { it.id == id }?.let { releaseMedia(it.persona) }
        Unit
    }

    fun deleteUnclassifiedCards(ids: Set<String>): Set<String> = deleteCards(ids, null)

    /** Recheck source membership under the lock so a card moved by sync is no longer eligible. */
    fun deleteCards(ids: Set<String>, sourceFolderId: String?): Set<String> = LocalStore.locked {
        val current = read()
        if (sourceFolderId != null && current.folders.none { it.id == sourceFolderId }) return@locked emptySet()
        val removed = current.cards.filter { it.id in ids &&
            if (sourceFolderId != null) it.folderId == sourceFolderId
            else (it.folderId == null || current.folders.none { folder -> folder.id == it.folderId }) }
        if (removed.isEmpty()) return@locked emptySet()
        val removedIds = removed.map { it.id }.toSet()
        save(current.copy(cards = current.cards.filterNot { it.id in removedIds },
            deletedCardIds = current.deletedCardIds + removedIds))
        removed.forEach { releaseMedia(it.persona) }
        removedIds
    }

    fun moveUnclassifiedCards(ids: Set<String>, folderId: String): Set<String> = moveCards(ids, folderId, null)

    fun moveCards(ids: Set<String>, folderId: String?, sourceFolderId: String?): Set<String> = LocalStore.locked {
        val current = read()
        if (folderId == sourceFolderId || (folderId != null && current.folders.none { it.id == folderId })) return@locked emptySet()
        if (sourceFolderId != null && current.folders.none { it.id == sourceFolderId }) return@locked emptySet()
        val movingIds = current.cards.filter { it.id in ids &&
            if (sourceFolderId != null) it.folderId == sourceFolderId
            else (it.folderId == null || current.folders.none { folder -> folder.id == it.folderId }) }.map { it.id }.toSet()
        if (movingIds.isEmpty()) return@locked emptySet()
        val now = System.currentTimeMillis()
        save(current.copy(cards = current.cards.map { if (it.id in movingIds)
            it.copy(folderId = folderId, updatedAt = maxOf(now, it.updatedAt + 1)) else it }))
        movingIds
    }

    fun createFolder(name: String): CharacterFolder = LocalStore.locked {
        require(name.isNotBlank())
        val folder = CharacterFolder(UUID.randomUUID().toString(), name.trim())
        val current = read()
        save(current.copy(folders = current.folders + folder))
        folder
    }

    fun renameFolder(id: String, name: String) = LocalStore.locked {
        require(name.isNotBlank())
        val current = read()
        save(current.copy(folders = current.folders.map { if (it.id == id)
            it.copy(name = name.trim(), updatedAt = maxOf(System.currentTimeMillis(), it.updatedAt + 1)) else it }))
    }

    fun deleteFolder(id: String) = LocalStore.locked {
        val current = read()
        save(current.copy(folders = current.folders.filterNot { it.id == id },
            cards = current.cards.map { if (it.folderId == id) it.copy(folderId = null, updatedAt = maxOf(System.currentTimeMillis(), it.updatedAt + 1)) else it },
            deletedFolderIds = current.deletedFolderIds + id))
    }

    fun moveCard(id: String, folderId: String?) = LocalStore.locked {
        val current = read()
        val targetFolder = folderId?.takeUnless { it in current.deletedFolderIds }
        require(targetFolder == null || current.folders.any { it.id == targetFolder })
        val card = current.cards.firstOrNull { it.id == id } ?: return@locked
        update(card.copy(folderId = targetFolder))
        Unit
    }

    /** Remote changes operate on the latest locked state; a pending local persona keeps a visible copy. */
    fun applyRemoteCard(card: CharacterCard, localDirty: Boolean): Set<String> = LocalStore.locked {
        val current = read()
        if (card.id in current.deletedCardIds) return@locked setOf(card.id)
        val incoming = if (card.folderId in current.deletedFolderIds) card.copy(folderId = null) else card
        val previous = current.cards.firstOrNull { it.id == card.id }
        val dirty = linkedSetOf<String>()
        val next = if (previous != null && localDirty && !samePersona(previous.persona, incoming.persona)) {
            val existingCopy = current.cards.any { it.id != card.id && samePersona(it.persona, incoming.persona, ignoreConflictName = true) }
            dirty += previous.id
            if (existingCopy) current else {
                val copy = incoming.copy(id = UUID.randomUUID().toString(),
                    persona = incoming.persona.copy(name = incoming.persona.name.removeSuffix("（冲突副本）") + "（冲突副本）"))
                dirty += copy.id
                current.copy(cards = current.cards + copy)
            }
        } else {
            val winner = if (localDirty && previous != null && previous.updatedAt > incoming.updatedAt) previous else incoming
            if (winner != incoming) dirty += winner.id
            current.copy(cards = current.cards.filterNot { it.id == incoming.id } + winner)
        }
        if (incoming.folderId != card.folderId) dirty += card.id
        save(next)
        previous?.let { releaseMedia(it.persona) }
        dirty
    }

    private fun samePersona(a: CharacterPersona, b: CharacterPersona, ignoreConflictName: Boolean = false): Boolean {
        fun view(p: CharacterPersona) = p.copy(avatarPath = "", appearanceImagePath = "",
            appearanceImagePaths = p.appearanceImagePaths.map { ImageSync.refForLocal(it) ?: it },
            name = if (ignoreConflictName) p.name.removeSuffix("（冲突副本）") else p.name)
        return view(a) == view(b)
    }

    fun applyRemoteFolder(folder: CharacterFolder, localDirty: Boolean = false): Boolean = LocalStore.locked {
        val current = read()
        if (folder.id in current.deletedFolderIds) return@locked true
        val previous = current.folders.firstOrNull { it.id == folder.id }
        val keepLocal = localDirty && previous != null && previous.updatedAt > folder.updatedAt
        save(current.copy(folders = current.folders.filterNot { it.id == folder.id } + if (keepLocal) previous!! else folder))
        keepLocal
    }

    fun applyRemoteCardDeletion(id: String) = LocalStore.suspendApply { deleteCard(id) }

    /** Unknown folders are retained. Only an explicit folder tombstone invalidates their membership. */
    fun applyRemoteFolderDeletion(id: String): Set<String> = LocalStore.suspendApply {
        val changed = read().cards.filter { it.folderId == id }.map { it.id }.toSet()
        deleteFolder(id)
        changed
    }

    /** The first login adopts the offline pool; subsequent accounts restore their own snapshots. */
    fun switchAccount(userId: String) = LocalStore.suspendApply {
        require(userId.isNotBlank())
        val previous = owner()
        if (previous == userId) { reload(); return@suspendApply }
        fun archive(id: String) = File(LocalStore.filesDir(), "freechat_library_account_${com.freechat.sync.Wire.hashOf(id.toByteArray())}.json")
        val current = read()
        if (previous.isNotBlank()) LocalStore.writeText(archive(previous), AppJson.gson.toJson(current))
        val incoming = LocalStore.readText(archive(userId))?.let { AppJson.gson.fromJson(it, CharacterLibraryState::class.java) }
            ?: if (previous.isBlank()) current else CharacterLibraryState()
        save(incoming, userId)
    }

    private fun releaseMedia(persona: CharacterPersona) {
        val entries = listOf(persona.avatarPath, persona.appearanceImagePath) + persona.appearanceImagePaths
        ImageSync.releaseUnreferenced(entries.filter { it.isNotBlank() }).forEach { hash ->
            LocalStore.deleteFile(ImageSync.cacheFile(hash))
        }
    }

    /** Inactive accounts still own their cached images; the active archive may be an older snapshot. */
    fun mediaFiles(): List<File> {
        val ownerId = owner()
        val activeArchive = if (ownerId.isBlank()) "" else "freechat_library_account_${com.freechat.sync.Wire.hashOf(ownerId.toByteArray())}.json"
        return listOf(LocalStore.characterLibraryFile()) + LocalStore.filesDir().listFiles().orEmpty().filter {
            it.name.startsWith("freechat_library_account_") && it.name.endsWith(".json") && it.name != activeArchive
        }
    }
}
