package com.freechat.data

import com.freechat.model.Conversation
import com.freechat.model.MemoryEntry
import com.freechat.model.Message
import com.freechat.model.PerConvSettings
import com.google.gson.reflect.TypeToken
import java.util.concurrent.ConcurrentHashMap

/** Grow-only deletion IDs live in the existing CONV wire object; MSGS stays a JSON array. */
object MessageDeletion {
    private val ids = ConcurrentHashMap<String, Set<String>>()
    private val visualIds = ConcurrentHashMap<String, Set<String>>()

    fun register(conv: Conversation): Set<String> {
        visualIds.merge(conv.id, conv.deletedVisualMessageIds.filter { it.isNotBlank() && it in conv.deletedMessageIds }.toSet()) { a, b -> a + b }
        return ids.merge(conv.id, conv.deletedMessageIds.filter { it.isNotBlank() }.toSet()) { a, b -> a + b }!!
    }

    fun deletedVisualIds(convId: String): Set<String> {
        deletedIds(convId) // Load both provenance sets together on a cold start.
        return visualIds[convId].orEmpty()
    }

    fun memoryDeletionIds(convId: String): Set<String> =
        com.freechat.core.DeletionLogic.memoryDeletionIds(deletedIds(convId), deletedVisualIds(convId))

    fun deletedIds(convId: String): Set<String> = ids[convId] ?: LocalStore.locked {
        val type = object : TypeToken<List<Conversation>>() {}.type
        val convs = runCatching {
            AppJson.gson.fromJson<List<Conversation>>(
                LocalStore.readText(LocalStore.conversationsFile()) ?: "[]", type)
        }.getOrNull().orEmpty()
        convs.forEach { register(it.healed()) }
        ids[convId].orEmpty()
    }

    fun messages(convId: String, rows: List<Message>): List<Message> =
        filterMessages(rows, deletedIds(convId))

    fun memories(convId: String, rows: List<MemoryEntry>): List<MemoryEntry> =
        filterMemories(rows, memoryDeletionIds(convId))

    // —— 三个纯过滤已抽进 freechat-core（DeletionLogic，与服务器共用）；本对象只管墓碑存取 ——
    fun filterMessages(rows: List<Message>, deleted: Set<String>): List<Message> =
        com.freechat.core.DeletionLogic.filterMessages(rows, deleted)

    fun filterMemories(rows: List<MemoryEntry>, deleted: Set<String>): List<MemoryEntry> =
        com.freechat.core.DeletionLogic.filterMemories(rows, deleted)

    fun atmosphere(convId: String, settings: PerConvSettings): PerConvSettings =
        com.freechat.core.DeletionLogic.atmosphere(settings, memoryDeletionIds(convId))
}
