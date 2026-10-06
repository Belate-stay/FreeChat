package com.freechat.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Base64
import com.freechat.i18n.LocaleManager
import com.freechat.model.Message
import com.freechat.model.Role
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 「生成在线网页链接」的分享载荷 —— 把选中消息压成 `POST /api/share` 的 JSON。
 *
 * 与长图/Markdown 两种分享同规则：只带正文与图片，剔除思考过程/信息源/附件/失败行
 * （失败行在调用方就被过滤）。图片**一律转存**（本地文件解码、远端 URL 下载后重编码），
 * 压到最长边 1600px、JPEG 85 —— 网页端拿到的是服务器同源图片，不依赖原图床存活，
 * 也把上传体积压到分享限额（单分享 10MB）以内。
 *
 * 上限与 FreeChatServer/src/share.js 保持同口径（改一处必须同步另一处）：
 * 20 条 / 正文 3 万字 / 单图 2MB（压后）/ 单分享 10MB。服务端还会再验一遍，
 * 客户端守卫只为给出更早、更明确的提示。
 */
object ShareLinkPayload {

    const val MAX_MESSAGES = 20
    const val MAX_TOTAL_CHARS = 30000
    const val MAX_IMAGE_BYTES = 2 * 1024 * 1024
    const val MAX_SHARE_BYTES = 10L * 1024 * 1024

    private const val MAX_EDGE = 1600
    private const val JPEG_QUALITY = 85
    private const val REMOTE_TIMEOUT_MS = 10_000

    sealed class Build {
        data class Ok(val body: JsonObject) : Build()
        data object TooManyMessages : Build()
        data object TooManyChars : Build()
        data object TooLarge : Build()
    }

    suspend fun build(title: String, messages: List<Message>): Build = withContext(Dispatchers.IO) {
        if (messages.size > MAX_MESSAGES) return@withContext Build.TooManyMessages
        val textBytes = messages.sumOf { it.content.toByteArray(Charsets.UTF_8).size.toLong() }
        if (messages.sumOf { it.content.length } > MAX_TOTAL_CHARS) return@withContext Build.TooManyChars

        val msgArr = JsonArray()
        val imgArr = JsonArray()
        var totalBytes = textBytes
        val imageTag = LocaleManager.strings().imageTag

        messages.forEachIndexed { index, m ->
            val o = JsonObject()
            o.addProperty("role", if (m.role == Role.USER) "user" else "assistant")
            // 纯图消息没有正文：给网页端一个可渲染的占位（与长图/MD 分享同款）
            o.addProperty("content", m.content.ifBlank { imageTag })
            o.addProperty("timestamp", m.timestamp)
            msgArr.add(o)

            val sources = m.imagePaths.filter { it.isNotBlank() } + m.imageUrls.filter { it.isNotBlank() }
            for (src in sources) {
                val jpeg = loadCompressed(src) ?: continue
                totalBytes += jpeg.size
                if (totalBytes > MAX_SHARE_BYTES) return@withContext Build.TooLarge
                val img = JsonObject()
                img.addProperty("messageIndex", index)
                img.addProperty("mime", "image/jpeg")
                img.addProperty("data", Base64.encodeToString(jpeg, Base64.NO_WRAP))
                imgArr.add(img)
            }
        }
        if (totalBytes > MAX_SHARE_BYTES) return@withContext Build.TooLarge

        val body = JsonObject()
        body.addProperty("title", title.take(200))
        body.add("messages", msgArr)
        body.add("images", imgArr)
        Build.Ok(body)
    }

    /**
     * 取图 → 等比缩到最长边 1600 → JPEG 85。本地路径与 http(s) URL 都收；
     * 任何一步失败（文件没了、图床挂了、解码炸了）都返回 null 跳过这张图——
     * 分享的是对话，少一张图不该毁掉整次分享。
     */
    private fun loadCompressed(src: String): ByteArray? = try {
        val raw = if (src.startsWith("http://") || src.startsWith("https://")) download(src) else File(src).takeIf { it.isFile }?.readBytes()
        raw ?: null
    } catch (_: Exception) {
        null
    }?.let { raw ->
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        // 先按 2 的幂降采样到接近目标，再精确缩放——大图整张解码会 OOM
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeByteArray(raw, 0, raw.size, opts) ?: return null
        val scaled = scaleToEdge(decoded)
        val oriented = applyExifRotation(raw, scaled)
        val out = ByteArrayOutputStream()
        oriented.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        if (oriented !== decoded) oriented.recycle()
        decoded.recycle()
        out.toByteArray().takeIf { it.size <= MAX_IMAGE_BYTES }
    }

    private fun scaleToEdge(bmp: Bitmap): Bitmap {
        val max = maxOf(bmp.width, bmp.height)
        if (max <= MAX_EDGE) return bmp
        val ratio = MAX_EDGE.toFloat() / max
        val w = (bmp.width * ratio).toInt().coerceAtLeast(1)
        val h = (bmp.height * ratio).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bmp, w, h, true)
        if (scaled !== bmp) bmp.recycle()
        return scaled
    }

    /**
     * 相机出片常把「正立」记在 EXIF 里而不是像素里——不转就是横躺的照片。
     * 用 android.media.ExifInterface（API 26 起在框架里，minSdk 26 够用）读方向后矩阵旋转。
     */
    private fun applyExifRotation(raw: ByteArray, bmp: Bitmap): Bitmap = try {
        val exif = android.media.ExifInterface(raw.inputStream())
        val orientation = exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)
        val degrees = when (orientation) {
            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) bmp else {
            val m = Matrix().apply { postRotate(degrees) }
            val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            if (rotated !== bmp) bmp.recycle()
            rotated
        }
    } catch (_: Exception) {
        bmp
    }

    private fun download(url: String): ByteArray? {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = REMOTE_TIMEOUT_MS
            conn.readTimeout = REMOTE_TIMEOUT_MS
            conn.instanceFollowRedirects = true
            if (conn.responseCode != 200) return null
            val bytes = conn.inputStream.use { it.readBytes() }
            // 只认图片字节；图床返回 HTML 错误页时不瞎解码
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            if (opts.outWidth > 0 && opts.outHeight > 0) bytes else null
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }
}
