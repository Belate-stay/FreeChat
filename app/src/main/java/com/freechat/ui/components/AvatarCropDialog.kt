package com.freechat.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.freechat.i18n.LocalStrings
import java.io.File
import java.io.FileOutputStream

/**
 * 头像裁切弹窗 —— **圆形取景框**，单指拖动 + 双指缩放，无第三方依赖。
 *
 * 为什么取景是圆的：头像在所有地方（侧栏、账号页、角色预览）都是 `clip(CircleShape)` 画的，
 * 方形取景框会让用户以为自己选的那四个角被保留了，其实一进圆圈就被切掉。
 * 直接给圆的，所见即所得 —— 裁出来的成品也是按这个圆的正方形外接框裁的（圆外那圈像素本来就看不见，
 * 留成图只会让文件更大、还可能被人从别处看到）。
 *
 * 输出固定缩到 [OUTPUT_SIZE] 见方再压 JPEG：原图动辄 4000px，存盘和上传都吃不消，
 * 而头像最大也就显示到 80dp，512 足够细腻。
 *
 * 顺带处理 EXIF 旋转 —— 相册里竖着拍的照片，`BitmapFactory` 读出来是横的，
 * 不转正的话用户会看到自己的头像躺倒。
 *
 * [onDone] 同时把**没裁过的那张图**（转正、降采样后重压的 JPEG）交出去，
 * 是给「编辑头像」用的：服务端只存裁好的成品，想重选范围就得本机留一份原图。
 * 交给调用方定夺存不存 —— 这个弹窗不碰账号，也不知道 userId。
 */
@Composable
fun AvatarCropDialog(
    sourceUri: Uri,
    onDone: (croppedPath: String, originalJpeg: ByteArray?) -> Unit,
    onDismiss: () -> Unit,
    /** 图读不出来时先喊一声再去 onDismiss —— 静默关闭会让用户以为「点了没反应」 */
    onError: () -> Unit = {}
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val s = LocalStrings.current

    fun openInput(uri: Uri): java.io.InputStream? =
        if (uri.scheme == "file") runCatching { File(uri.path!!).inputStream() }.getOrNull()
        else runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()

    val bitmap = remember(sourceUri) { runCatching { loadUpright(::openInput, sourceUri) }.getOrNull() }

    if (bitmap == null) {
        // 图读不出来（文件被删了 / 不是图片）：不该把用户困在一个黑屏里，
        // 但也不能一声不吭地关掉 —— 先让上层说一句，再退
        LaunchedEffect(Unit) {
            onError()
            onDismiss()
        }
        return
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    // 初始 scale=1 时图正好铺满取景框（ContentScale.Crop 语义），用户只能放大不能缩小 ——
    // 缩到比框还小会露出黑边，那不是裁剪该有的样子
    val frameSizeDp = minOf(config.screenWidthDp - 48, 320).dp
    val frameSizePx = with(density) { frameSizeDp.toPx() }

    val bw = bitmap.width.toFloat()
    val bh = bitmap.height.toFloat()
    // 让短边铺满取景框所需的缩放（相对 Image 的 Crop 基线，所以从 1 起步）
    val cover = maxOf(frameSizePx / bw, frameSizePx / bh)
    val baseW = bw * cover
    val baseH = bh * cover

    /**
     * 位移必须夹住：图得**始终盖满取景框**，往外最多能挪「图边长 − 框边长」的一半，
     * 再过去就露黑底了。缩放的锚点是取景框中心，所以放大之后允许的范围也跟着变大，
     * 每次都要按新 scale 重新夹一遍。
     */
    fun clampOffset(x: Float, y: Float, atScale: Float): Offset {
        val maxX = maxOf(0f, (baseW * atScale - frameSizePx) / 2f)
        val maxY = maxOf(0f, (baseH * atScale - frameSizePx) / 2f)
        return Offset(x.coerceIn(-maxX, maxX), y.coerceIn(-maxY, maxY))
    }

    fun cropAndSave() {
        // ===== 取景框在源图上盖住的那块正方形 =====
        // 屏幕坐标 → 源图素要反掉两层：
        //   ① graphicsLayer 的 scale/translation（回到「铺满」坐标系，原点在取景框左上角）
        //   ② ContentScale.Crop 的居中偏移（长边会被切掉一截，x/y 各自不同）
        // ②原先漏了，于是映射整体偏上：竖图铺满时上下各切一截，用户框里看到的是中间那块，
        // 实际裁的是偏上那块，重选范围也没用 —— 这就是「裁切无效」的根子。
        // 两个方向除的是同一个 cover、乘的是同一个 1/scale，所以映射出来的边长必然相等：
        // 成品天然是正方形，**不能**再逐边 coerceIn（那会把方图挤成长条，脸就扁了）。
        val half = frameSizePx / 2f
        fun toSrcX(screenX: Float) = ((screenX - offset.x - half) / scale + half - (half - baseW / 2f)) / cover
        fun toSrcY(screenY: Float) = ((screenY - offset.y - half) / scale + half - (half - baseH / 2f)) / cover

        val side = (frameSizePx / (scale * cover)).toInt().coerceAtLeast(1)
        val maxLeft = (bitmap.width - side).coerceAtLeast(0)
        val maxTop = (bitmap.height - side).coerceAtLeast(0)
        // 以取景框中心为准取正方形，再整体推进图内。拖动时已经夹过位移，
        // 这里的 coerce 只是兜底，保证任何情况下都裁得出一块合法的方图，不会静默罢工
        val left = (toSrcX(half) - side / 2f).toInt().coerceIn(0, maxLeft)
        val top = (toSrcY(half) - side / 2f).toInt().coerceIn(0, maxTop)

        val cropped = runCatching { Bitmap.createBitmap(bitmap, left, top, side, side) }.getOrNull() ?: return
        // 一律缩到 OUTPUT_SIZE 见方：小图放大也照做，头像显示就那么点大，
        // 尺寸统一比「有的 200 有的 512」好收拾
        val output = runCatching { Bitmap.createScaledBitmap(cropped, OUTPUT_SIZE, OUTPUT_SIZE, true) }
            .getOrNull() ?: return

        val file = File(context.filesDir, "avatar_${System.currentTimeMillis()}.jpg")
        runCatching {
            FileOutputStream(file).use { out -> output.compress(Bitmap.CompressFormat.JPEG, 92, out) }
        }.getOrNull() ?: return

        // 原图（已转正、已降到 2048 以内）也压一份交出去。走在内存里不走盘：
        // 它只用来给下一次「编辑头像」兜底，落盘再读回来多一次 IO 和一次漏文件的可能。
        // 压不出来不算失败 —— 成品已经在手，大不了下次编辑退回用当前头像
        val original = runCatching {
            java.io.ByteArrayOutputStream().use { out ->
                if (bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)) out.toByteArray() else null
            }
        }.getOrNull()
        onDone(file.absolutePath, original)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.92f)), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(frameSizeDp)
                    .clip(CircleShape)
                    .pointerInput(Unit) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val next = (scale * zoom).coerceIn(1f, MAX_SCALE)
                            // 以双指中心为锚点缩放：手指底下那块像素得待在原地。
                            // graphicsLayer 是绕取景框中心缩的，不补这一下，两指一捏画面会整体跳一下。
                            //   u  = 双指中心相对取景框中心的偏移
                            //   保持「缩放前后，u 处对应的源图像素不变」→ t' = u(1−r) + r·t
                            val ratio = next / scale
                            val ux = centroid.x - frameSizePx / 2f
                            val uy = centroid.y - frameSizePx / 2f
                            scale = next
                            offset = clampOffset(
                                ux * (1f - ratio) + ratio * offset.x + pan.x,
                                uy * (1f - ratio) + ratio * offset.y + pan.y,
                                next
                            )
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        },
                    contentScale = ContentScale.Crop
                )
            }
            // 取景圈画在最上层：它只负责"告诉你边界在哪"，不该被拖动的手势吃掉
            Box(
                Modifier
                    .size(frameSizeDp)
                    .border(2.dp, Color.White.copy(alpha = 0.9f), CircleShape)
            )

            Row(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 36.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) { Text(s.cancel, color = Color.White) }
                Box(Modifier.size(28.dp))
                Button(onClick = { cropAndSave() }) { Text(s.confirm, fontWeight = FontWeight.Medium) }
            }

            Text(
                s.avatarCropHint,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 84.dp)
            )
        }
    }
}

/** 成品边长 —— 头像最大显示到 80dp，512 已经过剩，但留点余量给未来 */
private const val OUTPUT_SIZE = 512

/** 最多放大到 5 倍 —— 再大就是马赛克了，而且放宽了也没人真去用它定位 */
private const val MAX_SCALE = 5f

/**
 * 按 EXIF 把图转正后读出来，并降采样到 2048 以内防 OOM。
 *
 * EXIF 得**单独再开一次流**读 —— `BitmapFactory` 会把流读到尾，同一个流读不了两遍。
 * file:// 和 content:// 都要支持：编辑已有头像给的是 file，从相册换新给的是 content。
 */
private fun loadUpright(open: (Uri) -> java.io.InputStream?, uri: Uri): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    open(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val raw = open(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null

    val rotation = runCatching {
        open(uri)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
    }.getOrDefault(0f)

    if (rotation == 0f) return raw
    val m = Matrix().apply { postRotate(rotation) }
    return runCatching { Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true) }.getOrDefault(raw)
}
