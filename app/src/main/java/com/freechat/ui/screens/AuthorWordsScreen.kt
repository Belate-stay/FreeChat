package com.freechat.ui.screens

import com.freechat.ui.components.HeaderIconButton

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freechat.data.AuthorWords
import com.freechat.i18n.LocalStrings
import com.freechat.ui.theme.FreeChatColors
import com.freechat.ui.theme.HazeSpec
import com.freechat.ui.theme.LocalChatFontFamily
import com.freechat.ui.theme.LocalAdvancedMaterial
import com.freechat.ui.theme.LocalFreeChatColors
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import com.freechat.ui.theme.pageBackground
import com.freechat.ui.theme.hazeBackground
import com.freechat.ui.components.TopBarBackdrop
import com.freechat.ui.components.TopBarBackdropSource

/**
 * 「作者的话」全文页（1.1.0）：从关于作者页点入的二级页。
 *
 * 版式与 AgreementScreen 同一套（顶栏 + 自滚正文、22dp 边距、段落 26sp 行高），
 * 正文按 [AuthorWords.CONTENT] 的渲染约定解析：`---` 分隔线、`## `/`### ` 小标题、
 * `**…**` 加粗、`- ` 条目。长文整页展示，问答区只留「点击阅读」入口。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthorWordsScreen(onBack: () -> Unit) {
    val colors = LocalFreeChatColors.current
    val s = LocalStrings.current
    val advancedMaterial = LocalAdvancedMaterial.current
    val hazeState = rememberHazeState()
    val statusBarHeightDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(Modifier.fillMaxSize().pageBackground(colors.Background)) {
        TopBarBackdropSource(hazeState, colors.Background, HazeSpec.topBandHeightDp(statusBarHeightDp))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (advancedMaterial) Modifier.hazeSource(state = hazeState).hazeBackground(colors.Background) else Modifier)
                .verticalScroll(rememberScrollState())
                .padding(top = HazeSpec.topContentPaddingDp(statusBarHeightDp, 36.dp), bottom = 40.dp)
                .padding(horizontal = 22.dp)
        ) {
            Spacer(Modifier.height(24.dp))
            AuthorWordsContent(colors)
        }

        TopBarBackdrop(hazeState, colors.Background, HazeSpec.topBandHeightDp(statusBarHeightDp))

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 8.8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeaderIconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, s.back, tint = colors.TextPrimary)
            }
            Text(
                s.authorWordsTitle,
                color = colors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}

@Composable
private fun AuthorWordsContent(colors: FreeChatColors) {
    // 先把行聚成块（纯逻辑）再逐块渲染 —— 组合函数里不能夹带调 Composable 的局部函数
    val blocks = mutableListOf<Pair<Int, String>>()   // 0=正文 1=小标题 2=分隔线 3=条目
    val para = StringBuilder()
    fun flush() {
        if (para.isNotBlank()) { blocks += 0 to para.toString().trim(); para.clear() }
    }
    for (raw in AuthorWords.CONTENT.trim().lines()) {
        val line = raw.trim()
        when {
            line == "---" -> { flush(); blocks += 2 to "" }
            line.startsWith("### ") || line.startsWith("## ") -> {
                flush(); blocks += 1 to line.removePrefix("### ").removePrefix("## ")
            }
            line.startsWith("- ") -> { flush(); blocks += 3 to line.removePrefix("- ") }
            line.isEmpty() -> flush()
            else -> { if (para.isNotEmpty()) para.append('\n'); para.append(line) }
        }
    }
    flush()

    blocks.forEach { (kind, text) ->
        when (kind) {
            1 -> AuthorWordsHeading(text, colors)
            2 -> {
                Spacer(Modifier.height(22.dp))
                HorizontalDivider(
                    color = colors.Primary.copy(alpha = 0.18f),
                    thickness = 1.dp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Spacer(Modifier.height(22.dp))
            }
            3 -> AuthorWordsBullet(text, colors)
            else -> AuthorWordsParagraph(text, colors)
        }
    }
}

/** 小标题：主题色加粗，比正文大一档 */
@Composable
private fun AuthorWordsHeading(text: String, colors: FreeChatColors) {
    Text(
        annotated(text, colors, heading = true),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = colors.Primary,
        lineHeight = 26.sp
    )
    Spacer(Modifier.height(12.dp))
}

/**
 * 正文段落：字体与 Chat 文字流同款（[LocalChatFontFamily]，即「字体优化」开关下的
 * HanYiSongYun / 系统默认），行高按该字体 1.2em 的字面给足；
 * 段后留出**空行节奏**（22dp ≈ 一行）——1.1.0 首版只留 8dp，长文读起来全挤成一坨。
 * 段内换行原样保留（`1、2、3、` 这类连行各自成行）。
 */
@Composable
private fun AuthorWordsParagraph(text: String, colors: FreeChatColors) {
    Text(
        annotated(text, colors, heading = false),
        style = MaterialTheme.typography.bodyLarge,
        color = colors.TextPrimary,
        fontFamily = LocalChatFontFamily.current,
        lineHeight = 28.sp
    )
    Spacer(Modifier.height(22.dp))
}

/** 条目（`- ` 开头）：与段落同字体，条目间紧凑、不带空行 */
@Composable
private fun AuthorWordsBullet(text: String, colors: FreeChatColors) {
    Text(
        annotated("· $text", colors, heading = false),
        style = MaterialTheme.typography.bodyLarge,
        color = colors.TextPrimary,
        fontFamily = LocalChatFontFamily.current,
        lineHeight = 28.sp,
        modifier = Modifier.padding(start = 12.dp)
    )
    Spacer(Modifier.height(8.dp))
}

/** 把 `**强调**` 解析成加粗片段；其余按普通字渲染（与 AgreementParagraph 同语义） */
private fun annotated(text: String, colors: FreeChatColors, heading: Boolean) = buildAnnotatedString {
    text.split("**").forEachIndexed { i, part ->
        if (i % 2 == 1) withStyle(
            SpanStyle(fontWeight = FontWeight.Bold, color = if (heading) colors.TextPrimary else colors.Primary)
        ) { append(part) }
        else append(part)
    }
}
