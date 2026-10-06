package com.freechat.data

import com.freechat.model.CharacterProfile
import com.freechat.model.ChatMode
import com.freechat.model.Conversation
import com.freechat.sync.ConflictCopyCleanup
import com.freechat.sync.Merge
import org.junit.Assert.*
import org.junit.Test

/** 1.0.99.3 冲突副本套娃根治：假冲突不造副本、真冲突幂等一份、存量套娃自动清理。 */
class Beta993RegressionTest {

    private fun profile(name: String = "陈以沫") = CharacterProfile(
        name = name, gender = "女", personalityText = "温柔",
        avatarPath = "/data/user/0/app/files/avatar_a1b2.jpg", avatarHash = "a1b2c3d4e5f60708",
        languageModelId = "mimo-local-device-id", appearanceImagePaths = listOf("/data/img1.jpg")
    )

    private fun conv(id: String, title: String, profile: CharacterProfile? = profile(), updatedAt: Long = 1) =
        Conversation(id = id, title = title, mode = ChatMode.COMPANION, characterProfile = profile,
            updatedAt = updatedAt)

    @Test fun wireRoundTripFieldDifferencesNeverCountAsPersonaConflict() {
        // 同一份人设，只是头像指纹/设备本地字段被 wire 往返重写过 —— 不是「两台设备各改了人设」
        val local = profile()
        val remote = local.copy(avatarHash = "ffffffffffffffff", avatarPath = "/data/user/0/app/files/avatar_ff.jpg",
            languageModelId = "other-device-model", appearanceImagePaths = listOf("/data/other.jpg"))
        assertTrue(Merge.samePersona(local, remote))
        assertNull(Merge.mergeConv(conv("c", "陈以沫", local), conv("c", "陈以沫", remote)).alternate)
    }

    @Test fun realPersonaEditsStillProduceExactlyOneAlternate() {
        val local = profile().copy(personalityText = "温柔体贴")
        val remote = profile().copy(personalityText = "冷静理性")
        assertFalse(Merge.samePersona(local, remote))
        val merged = Merge.mergeConv(conv("c", "陈以沫", local, updatedAt = 1),
            conv("c", "陈以沫", remote, updatedAt = 2))
        assertNotNull(merged.alternate)
        assertEquals("陈以沫（冲突副本）", merged.alternate!!.title)
    }

    @Test fun conflictCopyTitleNeverStacksSuffixes() {
        assertEquals("陈以沫（冲突副本）", Merge.conflictCopyTitle("陈以沫"))
        assertEquals("陈以沫（冲突副本）", Merge.conflictCopyTitle("陈以沫（冲突副本）"))
        assertEquals("陈以沫（冲突副本）（冲突副本）",
            Merge.conflictCopyTitle("陈以沫（冲突副本）（冲突副本）")) // 已嵌套的不减只防增
    }

    @Test fun cleanupAnalyzesAllLocaleSuffixesAndNestingDepth() {
        assertEquals(ConflictCopyCleanup.TitleInfo("陈以沫", 0), ConflictCopyCleanup.analyze("陈以沫"))
        assertEquals(ConflictCopyCleanup.TitleInfo("陈以沫", 1), ConflictCopyCleanup.analyze("陈以沫（冲突副本）"))
        assertEquals(ConflictCopyCleanup.TitleInfo("陈以沫", 3),
            ConflictCopyCleanup.analyze("陈以沫（冲突副本）（冲突副本）（冲突副本）"))
        assertEquals(ConflictCopyCleanup.TitleInfo("陈以沫", 1), ConflictCopyCleanup.analyze("陈以沫（衝突副本）"))
        assertEquals(ConflictCopyCleanup.TitleInfo("Mika", 2), ConflictCopyCleanup.analyze("Mika (conflict copy) (conflict copy)"))
    }

    @Test fun cleanupRemovesNestedCopiesButKeepsOriginalAndOneCleanCopy() {
        val original = conv("orig", "陈以沫", updatedAt = 100)
        val clean = conv("clean", "陈以沫（冲突副本）", profile().copy(personalityText = "另一套设定"), updatedAt = 90)
        val nested1 = conv("n1", "陈以沫（冲突副本）（冲突副本）", updatedAt = 80)
        val nested2 = conv("n2", "陈以沫（冲突副本）（冲突副本）（冲突副本）", updatedAt = 70)
        val extraClean = conv("x1", "陈以沫（冲突副本）", updatedAt = 60) // 和 clean 同组，最多留一份
        val stale = ConflictCopyCleanup.staleCopies(
            listOf(original, clean, nested1, nested2, extraClean)
        ) { false }
        // 原对话不动；干净副本留一份（人设真不同的那份）；嵌套套娃全清；多余干净副本清
        assertEquals(setOf("n1", "n2", "x1"), stale.toSet())
    }

    @Test fun cleanupNeverDeletesCopiesWithChatHistory() {
        val original = conv("orig", "陈以沫", updatedAt = 100)
        val nestedWithChat = conv("n1", "陈以沫（冲突副本）（冲突副本）", updatedAt = 80)
        val stale = ConflictCopyCleanup.staleCopies(listOf(original, nestedWithChat)) { it.id == "n1" }
        assertTrue(stale.isEmpty())
    }

    @Test fun cleanupHandlesGroupsWhereOnlyCopiesExist() {
        // 原对话已被用户删掉，只剩副本 —— 仍留一份，其余清掉
        val copyA = conv("a", "陈以沫（冲突副本）", updatedAt = 90)
        val copyB = conv("b", "陈以沫（冲突副本）", updatedAt = 80)
        val stale = ConflictCopyCleanup.staleCopies(listOf(copyA, copyB)) { false }
        assertEquals(listOf("b"), stale)
    }

    @Test fun cleanupIgnoresOrdinaryConversationsEntirely() {
        val normal = listOf(conv("a", "陈以沫"), conv("b", "小明"), conv("c", "Mika"))
        assertTrue(ConflictCopyCleanup.staleCopies(normal) { false }.isEmpty())
    }

    // ===== 1.0.99.3 图片上云 =====

    @Test fun localImagesBecomeContentAddressedRefsAndRoundTrip() {
        com.freechat.data.LocalStore.init(java.nio.file.Files.createTempDirectory("beta993").toFile())
        val img = java.io.File(com.freechat.data.LocalStore.filesDir(), "photo.jpg").apply { writeBytes(ByteArray(64) { it.toByte() }) }
        val ref = com.freechat.sync.ImageSync.refForLocal(img.absolutePath)
        assertNotNull(ref)
        assertTrue(ref!!.startsWith("img:"))
        // 同一张图（同一字节）永远同一引用 —— 内容寻址天然去重
        assertEquals(ref, com.freechat.sync.ImageSync.refForLocal(img.absolutePath))
        // 缓存文件名自带指纹：反推引用不用重算
        val hash = com.freechat.sync.ImageSync.hashOfRef(ref)
        val cache = com.freechat.sync.ImageSync.cacheFile(hash).apply { writeBytes(ByteArray(10)) }
        assertEquals(ref, com.freechat.sync.ImageSync.refForLocal(cache.absolutePath))
    }

    @Test fun missingOrEmptyFilesProduceNoRef() {
        com.freechat.data.LocalStore.init(java.nio.file.Files.createTempDirectory("beta993").toFile())
        assertNull(com.freechat.sync.ImageSync.refForLocal("/no/such/file.jpg"))
        assertNull(com.freechat.sync.ImageSync.refForLocal(""))
    }

    @Test fun materializeWritesCacheAndHealsDanglingRefs() {
        val dir = java.nio.file.Files.createTempDirectory("beta993").toFile()
        com.freechat.data.LocalStore.init(dir)
        com.freechat.data.LocalStore.setWriteListener(null)
        val hash = com.freechat.sync.Wire.hashOf(byteArrayOf(9, 9, 9))
        // 消息先到、图片后到：消息里留着解不开的引用
        val msgFile = com.freechat.data.LocalStore.messagesFile("c1")
        com.freechat.data.LocalStore.writeText(msgFile,
            """[{"id":"m","imagePaths":["${com.freechat.sync.ImageSync.refOf(hash)}"]}]""")
        com.freechat.sync.ImageSync.materialize(hash, byteArrayOf(1, 2, 3))
        val healed = msgFile.readText()
        assertFalse(healed.contains("img:"))
        assertTrue(healed.contains("img_$hash.jpg"))
    }

    @Test fun messagesWireKeepsRefsAndHttpButDropsLocalPaths() {
        com.freechat.data.LocalStore.init(java.nio.file.Files.createTempDirectory("beta993").toFile())
        val img = java.io.File(com.freechat.data.LocalStore.filesDir(), "a.jpg").apply { writeBytes(byteArrayOf(7, 7)) }
        val msgs = listOf(
            com.freechat.model.Message(id = "m", role = com.freechat.model.Role.USER,
                content = "看图", imagePaths = listOf(img.absolutePath),
                imageUrls = listOf("https://example.org/x.png", img.absolutePath))
        )
        val arr = com.freechat.sync.Wire.msgsToWire(msgs)
        val obj = arr[0].asJsonObject
        val paths = obj.getAsJsonArray("imagePaths").map { it.asString }
        val urls = obj.getAsJsonArray("imageUrls").map { it.asString }
        assertTrue(paths.all { it.startsWith("img:") })
        assertTrue(urls.any { it == "https://example.org/x.png" })
        assertTrue(urls.any { it.startsWith("img:") })
        // 解引用往返：远端新消息的引用解成缓存路径
        val ref = paths[0]
        val hash = com.freechat.sync.ImageSync.hashOfRef(ref)
        com.freechat.sync.ImageSync.materialize(hash, byteArrayOf(7, 7))
        val back = com.freechat.sync.Wire.msgsFromWire(arr, emptyList())
        assertEquals(1, back[0].imagePaths.size)
        assertFalse(back[0].imagePaths[0].startsWith("img:"))
        assertTrue(back[0].imagePaths[0].contains("img_$hash.jpg"))
    }

    @Test fun imageRefHelpersAreConsistent() {
        val hash = "0123456789abcdef"
        assertEquals("img:$hash", com.freechat.sync.ImageSync.refOf(hash))
        assertEquals(hash, com.freechat.sync.ImageSync.hashOfRef("img:$hash"))
        assertTrue(com.freechat.sync.ImageSync.isRef("img:x"))
        assertFalse(com.freechat.sync.ImageSync.isRef("https://a"))
        assertEquals(hash, com.freechat.sync.ImageSync.hashFromCacheName("img_$hash.jpg"))
        assertNull(com.freechat.sync.ImageSync.hashFromCacheName("other.jpg"))
    }
}
