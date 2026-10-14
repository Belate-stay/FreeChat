package com.freechat.ui.components

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 解码位图的进程级缓存（1.1.26 追修）：滑出屏再滑回不重新解码、直接出图。键含文件指纹
 * （大小+mtime）——同路径被换内容时自动失效重解，不会拿旧图当新图。按像素字节计容淘汰。
 */
internal object ImageDecodeCache {
    private class Entry(val fingerprint: String, val bitmap: ImageBitmap)

    // 容量约 1/8 堆（32–96MB 夹紧）：聊天滑过一圈的图都留在手上，淘汰最久未见的。
    private val cache = object : LruCache<String, Entry>(
        (Runtime.getRuntime().maxMemory() / 8).toInt().coerceIn(32 shl 20, 96 shl 20)
    ) {
        override fun sizeOf(key: String, value: Entry): Int = value.bitmap.width * value.bitmap.height * 4
    }

    fun key(path: String, targetMaxDim: Int): String = "$path|$targetMaxDim"

    fun fingerprint(file: File): String = "${file.length()}|${file.lastModified()}"

    fun get(path: String, targetMaxDim: Int, fingerprint: String): ImageBitmap? =
        cache.get(key(path, targetMaxDim))?.takeIf { it.fingerprint == fingerprint }?.bitmap

    fun put(path: String, targetMaxDim: Int, fingerprint: String, bitmap: ImageBitmap) {
        cache.put(key(path, targetMaxDim), Entry(fingerprint, bitmap))
    }
}

/**
 * 本地图片加载 — 直接解码为 ImageBitmap 渲染，绕开 Coil 的 File 缓存。
 *
 * 用户上传的图片在「AI 回复后」曾出现消失/黑屏：消息与文件都还在，问题出在 Coil 对
 * File 模型在 LazyColumn 回收重组合时偶发不重绘。改成直接解码持有点位图，只要文件
 * 存在就一定能显示。1.1.26 追修：解码进 [ImageDecodeCache]，滑回首帧直出不重解。
 */
@Composable
fun LocalImage(
    path: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    targetMaxDim: Int = 1024,
    onImageSize: ((Int, Int) -> Unit)? = null,
    onLoadResult: ((Boolean) -> Unit)? = null
) {
    val cached = remember(path, targetMaxDim) {
        ImageDecodeCache.get(path, targetMaxDim, ImageDecodeCache.fingerprint(File(path)))
    }
    val result by produceState<Pair<ImageBitmap?, Boolean>>(initialValue = cached to (cached != null), path, targetMaxDim) {
        if (cached != null) return@produceState
        value = null to false
        val decoded = withContext(Dispatchers.IO) { decodeLocalImage(File(path), targetMaxDim) }
        if (decoded != null) {
            ImageDecodeCache.put(path, targetMaxDim, ImageDecodeCache.fingerprint(File(path)), decoded)
        }
        value = decoded to true
    }
    val bmp = result.first
    val latestSizeCallback by rememberUpdatedState(onImageSize)
    val latestResultCallback by rememberUpdatedState(onLoadResult)
    LaunchedEffect(bmp, result.second, path) {
        if (bmp != null) latestSizeCallback?.invoke(bmp.width, bmp.height)
        if (result.second) latestResultCallback?.invoke(bmp != null)
    }
    if (bmp != null) {
        // 满宽显示时按图片真实宽高比撑开高度：fillMaxWidth + FillWidth 若无 aspectRatio，
        // 高度会塌成 0（或按像素固有高度渲染导致错乱），图片显示不出来。
        val sized = if (contentScale == ContentScale.FillWidth) {
            modifier.aspectRatio(bmp.width.toFloat() / bmp.height.coerceAtLeast(1).toFloat())
        } else modifier
        Image(
            bitmap = bmp,
            contentDescription = contentDescription,
            modifier = sized,
            contentScale = contentScale
        )
    } else {
        // 解码中/失败时占位，保持尺寸不跳动（也保留 modifier 上的 onGloballyPositioned/clickable）
        Spacer(modifier = modifier)
    }
}

private fun decodeLocalImage(file: File, maxDim: Int): ImageBitmap? {
    if (!file.exists()) return null
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeFile(file.absolutePath, opts)?.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}
