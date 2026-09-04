package com.freechat.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.freechat.R

/** 聊天默认字体（鸿蒙宋韵朗黑 W）——只作用于用户提示词 + AI 回复正文。
 *  单字重文件：加粗/倾斜由系统合成（伪粗体）。 */
val HYSongYunLangHei = FontFamily(
    Font(R.font.hysongyunlanghei, FontWeight.Normal)
)

/** 等宽字体（Consolas）——用于设置页模型名称等需要等宽展示的技术标识 */
val Consolas = FontFamily(
    Font(R.font.consola, FontWeight.Normal)
)

/** 全局 UI 字体（谷歌思源黑体 Noto Sans CJK Medium）——标题栏/设置页/侧滑页等原「跟随系统字体」的地方 */
val NotoSansCJK = FontFamily(
    Font(R.font.notosanscjkmedium, FontWeight.Normal)
)

/** FreeChat 标题专用字体（Aurora）——标题显示为「FreeChat」时使用 */
val Aurora = FontFamily(
    Font(R.font.aurora, FontWeight.Normal)
)

/** 模型名 + 思考时间专用字体（HFSandBeach）——仅用于 AI 回复文案下方的模型名与思考时间 */
val HFSandBeach = FontFamily(
    Font(R.font.hfsandbeach_2, FontWeight.Normal)
)

/** 聊天文字字体族：由「字体优化」开关决定是自定义字体（HYSongYunLangHei）还是系统默认 */
val LocalChatFontFamily = staticCompositionLocalOf<FontFamily> { FontFamily.Default }

/** 模型名/思考时间字体族：由「字体优化」开关决定是 HFSandBeach 还是系统默认 */
val LocalLatinFontFamily = staticCompositionLocalOf<FontFamily> { FontFamily.Default }

/** 全局 UI 字体族：由「字体优化」开关决定是 SanJiDianHeiJianTi 还是系统默认 */
val LocalGlobalFontFamily = staticCompositionLocalOf<FontFamily> { FontFamily.Default }

/** 等宽字体族（模型名等原 Consolas 处）：由「字体优化」开关决定是 Consolas 还是全局字体 */
val LocalMonoFontFamily = staticCompositionLocalOf<FontFamily> { FontFamily.Default }
