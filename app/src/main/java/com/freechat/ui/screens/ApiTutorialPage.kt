package com.freechat.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freechat.i18n.AppStrings
import com.freechat.ui.theme.FreeChatColors
import com.freechat.ui.theme.LocalChatFontFamily
import com.freechat.ui.theme.linkInk

/**
 * 获取 API 教程（2026-10-19 工单）：以 DeepSeek 为例，充值 → 申请 API Key → 查用量/价格 →
 * 取三项必要信息 → 回 FreeChat 添加模型；文末给其他模型的通用找法。
 * 教学正文固定中文不进语言包（FeatureRecPage 演示文案同先例）——要三语再抽词条。
 * 排版纪律：文案精炼、段间留呼吸感；截图一律居中、限宽、按讲解顺序放；
 * 链接=linkInk 蓝+斜体+下划线可点（[文字](https://地址) 语法）；**加粗** 标重点。
 * 设置二级页不许自带滚动（戒律9）：容器负责滚，这里只出内容。
 */
@Composable
fun ApiTutorialPage(colors: FreeChatColors, s: AppStrings) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // 首段下移（2026-10-19 返修）：贴顶会探进标题栏的高斯模糊层，压出一段呼吸再开始
        Spacer(Modifier.height(28.dp))
        TutPara(colors, colors.TextSecondary,
            "FreeChat 不提供模型服务，内置模型仅供体验。自行申请 API Key 并填入「添加模型」页，即可稳定使用。\n本教程以 DeepSeek 为例，其他服务商见文末。")

        TutTitle(colors, "一、注册登录")
        TutPara(colors, colors.TextPrimary,
            "浏览器访问 [platform.deepseek.com](https://platform.deepseek.com)，注册并登录。")

        TutTitle(colors, "二、充值")
        TutPara(colors, colors.TextPrimary,
            "进入「充值」页。金额随意，**1 元起充**。\n**建议先充 1 元试水**，观察效果与额度消耗后，再决定后续充值金额。")
        TutShot(colors, com.freechat.R.drawable.api_tut_pay, "支付页面")

        TutTitle(colors, "三、查看余额")
        TutPara(colors, colors.TextPrimary,
            "充值到账后，在「用量信息」页查看**充值余额**与累计消费。")
        TutShot(colors, com.freechat.R.drawable.api_tut_balance, "余额页面")

        TutTitle(colors, "四、申请 API Key")
        TutPara(colors, colors.TextPrimary,
            "侧边菜单进入 **API keys**（下图蓝圈处）：")
        TutShot(colors, com.freechat.R.drawable.api_tut_menu, "平台菜单")
        TutPara(colors, colors.TextPrimary,
            "点「创建 API key」，输入名称并创建：")
        TutShot(colors, com.freechat.R.drawable.api_tut_create_key, "创建 API key")
        TutPara(colors, colors.TextPrimary,
            "密钥**只完整显示一次**，请立即复制保存。格式为 **sk- 开头**的字符串。")

        TutTitle(colors, "五、查看用量")
        TutPara(colors, colors.TextPrimary,
            "消费金额、请求次数、Tokens 用量均在「用量信息」页查询——试水后在此观察消耗。")
        TutShot(colors, com.freechat.R.drawable.api_tut_usage, "用量页面")
        TutPara(colors, colors.TextSecondary,
            "所有用费均为您提供AI服务，客户端直连模型无中间商赚取差价，价格透明可在各平台官网的用量中查看。")

        TutTitle(colors, "六、模型价格与能力")
        TutPara(colors, colors.TextPrimary,
            "「产品定价」页（见菜单蓝圈）列出各模型的价格与支持能力（思考模式、图像理解等）：")
        TutShot(colors, com.freechat.R.drawable.api_tut_pricing, "模型价格与能力")

        TutTitle(colors, "七、三项必要信息")
        TutPara(colors, colors.TextPrimary,
            "在 FreeChat 添加模型前，从开放平台取得三项信息：")
        TutBullet(colors, "**API Key** — 创建密钥时复制的 sk- 开头字符串")
        TutBullet(colors, "**URL 地址** — 接口地址。DeepSeek 为 [api.deepseek.com](https://api.deepseek.com)，见定价表「BASE URL」行或接口文档")
        TutBullet(colors, "**模型 ID** — 模型标识。如 **deepseek-flash**、**deepseek-v4-pro**，见定价表「模型」行")

        TutTitle(colors, "八、在 FreeChat 添加模型")
        TutPara(colors, colors.TextPrimary,
            "设置 → 模型管理 → 语言模型 →「添加模型」，依次填入：")
        TutBullet(colors, "**模型名称** — 自定义显示名，如「DeepSeek」")
        TutBullet(colors, "**URL 地址** — 上文的接口地址")
        TutBullet(colors, "**API Key** — sk- 开头的密钥")
        TutBullet(colors, "**模型 ID** — 上文的模型标识")
        TutPara(colors, colors.TextPrimary,
            "保存后在列表中选中该模型，即可开始对话。")

        TutTitle(colors, "九、使用其他模型")
        TutPara(colors, colors.TextPrimary,
            "浏览器搜索「**○○API开放平台**」（如阿里云百炼、智谱、月之暗面），按同样流程操作：\n" +
                "注册 → 充值 → 申请 API Key → 获取 URL 与模型 ID → 在 FreeChat 填写。\n" +
                "接口文档以各平台为准（DeepSeek：[api-docs.deepseek.com](https://api-docs.deepseek.com)）。")

        Spacer(Modifier.height(16.dp))
        TutPara(colors, colors.TextSecondary,
            "API Key 仅保存在本机，不随云同步上传；不使用时可随时在服务商后台停用。")
        Spacer(Modifier.height(8.dp))
    }
}

/** 小节标题：明显加大加粗，节间留足呼吸（2026-10-19 返修：标题别挨太紧） */
@Composable
private fun TutTitle(colors: FreeChatColors, text: String) {
    Spacer(Modifier.height(30.dp))
    Text(text, color = colors.TextPrimary, fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleMedium, lineHeight = 30.sp,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
}

/**
 * 正文段：`**加粗**` 标重点；`[文字](https://地址)` 渲染成可点链接（linkInk 蓝+斜体+下划线）。
 * 行距放大、行高统一，保证可读性。
 */
@Composable
private fun TutPara(colors: FreeChatColors, color: androidx.compose.ui.graphics.Color, text: String) {
    Text(
        text = tutAnnotated(text, colors),
        color = color,
        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = LocalChatFontFamily.current),
        lineHeight = 27.sp,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
    )
}

/** 条目行：缩进 + 悬挂排版感，条目之间留白（2026-10-19 返修：每一步之间都要间距） */
@Composable
private fun TutBullet(colors: FreeChatColors, text: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(start = 10.dp, top = 8.dp, bottom = 8.dp)) {
        Text("·", color = colors.TextSecondary,
            style = MaterialTheme.typography.bodyMedium, lineHeight = 27.sp,
            modifier = Modifier.padding(end = 8.dp))
        Text(
            text = tutAnnotated(text, colors),
            color = colors.TextPrimary,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = LocalChatFontFamily.current),
            lineHeight = 27.sp,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** 教程截图：居中、限宽（不喧宾夺主）、圆角细框；竖图按宽度等比出高 */
@Composable
private fun TutShot(colors: FreeChatColors, resId: Int, caption: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center) {
        Image(
            painter = painterResource(resId),
            contentDescription = caption,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .widthIn(max = 288.dp)
                .fillMaxWidth(0.82f)
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, colors.Divider.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
        )
    }
}

/** 解析 **加粗** 与 [文字](链接)；链接=可点 + 蓝 + 斜体 + 下划线（全平台「这里能点」语汇） */
private fun tutAnnotated(text: String, colors: FreeChatColors): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end > 0) {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(text.substring(i + 2, end)) }
                    i = end + 2
                } else {
                    append(text.substring(i))
                    i = text.length
                }
            }
            text.startsWith("[", i) -> {
                val labelEnd = text.indexOf("](", i)
                val hrefEnd = if (labelEnd > 0) text.indexOf(")", labelEnd + 2) else -1
                if (labelEnd > 0 && hrefEnd > labelEnd + 2) {
                    withLink(
                        LinkAnnotation.Url(
                            text.substring(labelEnd + 2, hrefEnd),
                            TextLinkStyles(SpanStyle(
                                color = colors.linkInk,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                textDecoration = TextDecoration.Underline
                            ))
                        )
                    ) { append(text.substring(i + 1, labelEnd)) }
                    i = hrefEnd + 1
                } else {
                    append("[")
                    i++
                }
            }
            else -> {
                append(text[i])
                i++
            }
        }
    }
}
