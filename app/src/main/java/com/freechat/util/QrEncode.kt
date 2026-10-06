package com.freechat.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/**
 * 本地二维码编码（1.0.93）。
 *
 * 微信 ClawBot 的 `qrcode_img_content` / `qrcode` 是**要编码进二维码的内容**
 * （扫码后打开的确认链接 / 码值），不是现成的图片地址——openclaw 插件也是自己画码。
 * 拿它当图片 URL 加载只会得到一片空白（1.0.91 的实机教训）。
 */
object QrEncode {

    /** 编码成黑白位图；内容为空/编码失败返回 null（调用方兜底显示文本） */
    fun bitmap(content: String, size: Int = 512): Bitmap? {
        if (content.isBlank()) return null
        return try {
            val hints = mapOf(EncodeHintType.MARGIN to 1)
            val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bmp.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            bmp
        } catch (_: Exception) {
            null
        }
    }
}
