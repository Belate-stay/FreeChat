package com.freechat.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 本地图片加载 — 直接解码为 ImageBitmap 渲染，绕开 Coil 的 File 缓存。
 *
 * 用户上传的图片在「AI 回复后」曾出现消失/黑屏：消息与文件都还在，问题出在 Coil 对
 * File 模型在 LazyColumn 回收重组合时偶发不重绘。改成直接解码持有点位图，只要文件
 * 存在就一定能显示。
 */
@Composable
fun LocalImage(
    path: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    targetMaxDim: Int = 1024
) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path, targetMaxDim) {
        value = withContext(Dispatchers.IO) { decodeLocalImage(File(path), targetMaxDim) }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = contentDescription,
            modifier = modifier,
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
