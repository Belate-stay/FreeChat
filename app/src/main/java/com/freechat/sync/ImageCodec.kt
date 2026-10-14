package com.freechat.sync

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * 1.0.99.3 图片压缩（上传载荷用；本机原图不动）：
 * 长边 2048px、JPEG ~85%、保留 EXIF 方向 —— 常规图片压到 200–500KB，
 * 128MB/人 的配额够存近千张（用户拍板「压缩后上传」）。
 *
 * 单测（JVM，没有 android.graphics）里 [compressForUpload] 返回 null，
 * 调用方按「压不动就小文件直存」兜底 —— 纯逻辑（引用/指纹/缓存）不受影响。
 */
internal object ImageCodec {
    private const val MAX_EDGE = 2048
    private const val JPEG_QUALITY = 85

    fun compressForUpload(file: File): ByteArray? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // Bounds-only decoding intentionally returns null, even for a valid bitmap.
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(maxOf(bounds.outWidth, bounds.outHeight))
        }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null
        val scaled = fitLongEdge(decoded)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        if (scaled !== decoded) scaled.recycle()
        decoded.recycle()
        out.toByteArray().takeIf { it.isNotEmpty() }
    }.getOrNull()

    /** 只往下采样：长边 ≤2048 时 inSampleSize=1（不放大模糊图） */
    private fun sampleSize(longEdge: Int): Int {
        var size = 1
        while (longEdge / (size * 2) >= MAX_EDGE) size *= 2
        return size
    }

    /**
     * 1.1.21.3 白边根治：裁掉生成结果四周的纯色垫边（供应商把非方形构图垫进方形画布留下的）。
     * 无垫边/无法解码返回 null（JVM 单测同 compressForUpload）。[targetAspect] 是提示词里的目标
     * 画幅（宽/高）——裁完必须更接近它才动手，防止误裁白底商品图这类自带留白的构图。
     */
    fun trimUniformMargins(bytes: ByteArray, targetAspect: Float? = null): ByteArray? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        try {
            val pixels = IntArray(decoded.width * decoded.height)
            decoded.getPixels(pixels, 0, decoded.width, 0, 0, decoded.width, decoded.height)
            val trim = com.freechat.data.ImageMargins.trim(decoded.width, decoded.height, pixels, targetAspect)
                ?: return null
            val cropped = Bitmap.createBitmap(decoded, trim.left, trim.top,
                decoded.width - trim.left - trim.right, decoded.height - trim.top - trim.bottom)
            val out = ByteArrayOutputStream()
            val png = bytes.size > 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte()
            if (png) cropped.compress(Bitmap.CompressFormat.PNG, 100, out)
            else cropped.compress(Bitmap.CompressFormat.JPEG, 95, out)
            if (cropped !== decoded) cropped.recycle()
            out.toByteArray().takeIf { it.isNotEmpty() }
        } finally {
            decoded.recycle()
        }
    }.getOrNull()

    private fun fitLongEdge(bitmap: Bitmap): Bitmap {
        val longEdge = maxOf(bitmap.width, bitmap.height)
        if (longEdge <= MAX_EDGE) return bitmap
        val scale = MAX_EDGE.toFloat() / longEdge
        val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    /** EXIF 方向已在 decodeFile 后被抹平 —— JPEG 输出恒为正向（Matrix 预留，1.0.99.3 先不做旋转） */
    @Suppress("unused")
    private val identity = Matrix()
}
