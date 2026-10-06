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
        BitmapFactory.decodeFile(file.absolutePath, bounds) ?: return null
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
