package com.freechat.data

import com.freechat.model.Message
import com.freechat.model.Role
import com.freechat.sync.ImageSync
import com.freechat.sync.Wire
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 1.2.3+ 图片同步硬化（用户工作单：「安卓生成的图片——AI 直接生成/场景图/用户上传——
 * 都要能同步到云端并在 Web 正常显示」）：
 *
 *  · G2 丢格根治 —— 压缩失败不再整格消失，压不动就推原字节（有上限）；
 *  · wire 转不动保留原值 —— 格子绝不凭空消失；
 *  · 墓碑护栏判据 —— 引用两种形态（未解引用 / 已自愈路径）都数得到；
 *  · 补缺清单含图 —— 缓存里现有的图都进 localAllKeys，历史缺件可补推。
 */
class ImageSyncHardeningTest {

    private fun freshStore(): File {
        val dir = Files.createTempDirectory("img-hardening").toFile()
        LocalStore.init(dir)
        LocalStore.setWriteListener(null)
        return dir
    }

    /** JVM 没有 android.graphics —— ImageCodec.compressForUpload 恒为 null，
     *  正好把「压不动」这条兜底路径逼出来（真机上是 GIF/怪格式走这里） */
    @Test fun compressionFailureStillCachesRawPayloadUpToCap() {
        freshStore()
        // 600KB：旧逻辑只收 ≤500KB 的原字节，这一张会被整格丢掉（G2 实锤场景）
        val big = File(LocalStore.filesDir(), "photo_big.jpg").apply { writeBytes(ByteArray(600 * 1024) { 1 }) }
        val ref = ImageSync.ensureCachedFromFile(big.absolutePath)
        assertNotNull("压缩失败且原图 600KB 时必须仍给出引用（G2）", ref)
        val hash = ImageSync.hashOfRef(ref!!)
        assertTrue("载荷应已落缓存", ImageSync.cachePathIfPresent(hash) != null)

        // 超过 4MB 兜底上限：服务器单对象塞不下，才允许放弃（本机路径照常留着）
        val huge = File(LocalStore.filesDir(), "photo_huge.jpg").apply { writeBytes(ByteArray(5 * 1024 * 1024) { 2 }) }
        assertNull(ImageSync.ensureCachedFromFile(huge.absolutePath))
    }

    @Test fun wireNeverDropsUnconvertibleImageSlots() {
        freshStore()
        val msgs = listOf(
            Message(
                id = "m", role = Role.USER, content = "看图",
                imagePaths = listOf("/no/such/file.png"),
                imageUrls = listOf("/no/such/other.png", "https://example.org/ok.png"),
            )
        )
        val arr = Wire.msgsToWire(msgs)
        val obj = arr[0].asJsonObject
        val paths = obj.getAsJsonArray("imagePaths").map { it.asString }
        val urls = obj.getAsJsonArray("imageUrls").map { it.asString }
        // 转不动保留原值 —— 丢格=这张图在其他端的这份消息里凭空消失
        assertEquals(listOf("/no/such/file.png"), paths)
        assertEquals(2, urls.size)
        assertTrue(urls.contains("/no/such/other.png"))
        assertTrue(urls.contains("https://example.org/ok.png"))
    }

    @Test fun localReferenceCheckSeesBothRefAndHealedPathForms() {
        freshStore()
        val hash = "0123456789abcdef"
        // 两种形态都算引用：未解引用的 "img:<hash>" 和自愈后的 img_<hash>.jpg 路径
        LocalStore.writeText(
            LocalStore.messagesFile("c1"),
            """[{"id":"m","imagePaths":["${ImageSync.refOf(hash)}"]}]"""
        )
        assertTrue("未解引用形态要数得到", ImageSync.isReferencedLocally(hash))

        LocalStore.writeText(
            LocalStore.messagesFile("c1"),
            """[{"id":"m","imagePaths":["/data/user/0/app/files/img_$hash.jpg"]}]"""
        )
        assertTrue("自愈后的缓存路径形态也要数得到", ImageSync.isReferencedLocally(hash))

        LocalStore.writeText(LocalStore.messagesFile("c1"), """[{"id":"m","imagePaths":[]}]""")
        assertFalse("引用清干净了才算归零", ImageSync.isReferencedLocally(hash))
    }

    @Test fun localCacheHashesFeedTheBackfillInventory() {
        freshStore()
        val hash = Wire.hashOf(byteArrayOf(4, 2))
        ImageSync.cacheFile(hash).writeBytes(byteArrayOf(4, 2))
        // 补缺清单的图片口径（G5）：缓存里现有的图都算本机存量（Adapter.localAllKeys 消费它）
        assertEquals(listOf(hash), ImageSync.localCacheHashes())
        // 没有缓存的引用不进清单 —— 推送侧对「缺缓存但还引用着」另有墓碑护栏
        ImageSync.cacheFile(hash).delete()
        assertEquals(emptyList<String>(), ImageSync.localCacheHashes())
    }

    @Test fun imageRefHelpersStillConsistent() {
        val hash = "fedcba9876543210"
        assertEquals("img:$hash", ImageSync.refOf(hash))
        assertEquals(hash, ImageSync.hashOfRef("img:$hash"))
        assertEquals(hash, ImageSync.hashFromCacheName("img_$hash.jpg"))
    }
}
