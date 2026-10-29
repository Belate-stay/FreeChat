package com.freechat.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freechat.i18n.AppStrings
import com.freechat.sync.ActiveShareList
import com.freechat.sync.ApiClient
import com.freechat.sync.ApiError
import com.freechat.sync.Session
import com.freechat.sync.ShareInfo
import com.freechat.sync.shareWebUrl
import com.freechat.ui.animation.FreeChatAnimation
import com.freechat.ui.theme.FreeChatColors
import com.freechat.ui.theme.LocalFreeChatColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 「我的分享」排序方式：创建时间（默认，新在前）/ 标题 */
internal enum class ShareSort { CREATED_TIME, TITLE }

/** 空标题分享在筛选菜单里的稳定键（真实对话名不会撞上） */
private const val UNTITLED_KEY = "␀untitled"

private fun shareFilterKey(share: ShareInfo): String = share.title.ifBlank { UNTITLED_KEY }

private fun shareFilterLabel(share: ShareInfo, s: AppStrings): String =
    share.title.ifBlank { s.share }

/**
 * 「我的分享」页共享状态 + 数据操作。
 * 列表渲染在 [MySharesScreen]，标题栏/调节菜单/动作菜单挂在 SettingsScreen —— 两边读写同一份状态。
 */
@Stable
internal class MySharesUiState {
    // ===== 数据 =====
    var shares by mutableStateOf<List<ShareInfo>?>(null)   // null = 加载中
    var loadFailed by mutableStateOf(false)
    var revokingIds by mutableStateOf<Set<String>>(emptySet())
    private val activeShares = ActiveShareList()
    private var reloadVersion = 0

    // ===== 多选（对齐收藏页状态机）=====
    var selectMode by mutableStateOf(false)
    var selectedIds by mutableStateOf<Set<String>>(emptySet())

    // ===== 调节菜单 =====
    var showTuneMenu by mutableStateOf(false)
    var tuneLevel by mutableStateOf(com.freechat.ui.components.TuneLevel.MAIN)
    var filterKey by mutableStateOf<String?>(null)          // null = 全部对话
    var sortMode by mutableStateOf(ShareSort.CREATED_TIME)

    // ===== 卡片动作菜单 / 撤销确认 =====
    var menuShare by mutableStateOf<ShareInfo?>(null)
    var pendingRevoke by mutableStateOf<List<ShareInfo>>(emptyList())

    fun exitSelect() {
        selectMode = false
        selectedIds = emptySet()
    }

    /** 离开页面时收掉全部浮层与多选，免得粘到下一次进来 */
    fun resetOverlays() {
        exitSelect()
        showTuneMenu = false
        menuShare = null
        pendingRevoke = emptyList()
    }

    fun toggleSelect(share: ShareInfo) {
        val next = if (share.id in selectedIds) selectedIds - share.id else selectedIds + share.id
        // 取消到空就自动退出多选，省得留一个「已选择 0 项」的空壳
        if (next.isEmpty()) exitSelect() else selectedIds = next
    }

    fun enterSelect(share: ShareInfo) {
        showTuneMenu = false
        selectMode = true
        selectedIds = setOf(share.id)
    }

    /** 多选态选中的分享行（从当前列表反查） */
    fun selectedItems(): List<ShareInfo> =
        shares.orEmpty().filter { it.id in selectedIds }

    /** 被筛标题的分享全没了 → 自动回退「全部」（对齐收藏页 effectiveFilter） */
    fun effectiveFilter(): String? =
        filterKey?.takeIf { key -> shares.orEmpty().any { shareFilterKey(it) == key } }

    /** 过滤 + 排序后的展示列表 */
    fun displayList(s: AppStrings): List<ShareInfo> {
        val base = shares.orEmpty()
        val key = effectiveFilter()
        val filtered = if (key == null) base else base.filter { shareFilterKey(it) == key }
        return when (sortMode) {
            ShareSort.CREATED_TIME -> filtered.sortedByDescending { it.createdAt }
            ShareSort.TITLE -> filtered.sortedBy { shareFilterLabel(it, s) }
        }
    }

    /** 调节菜单「筛选对话」选项（按分享标题去重——标题即创建时的对话名） */
    fun filterEntries(s: AppStrings): List<com.freechat.ui.components.TuneEntry> =
        listOf(com.freechat.ui.components.TuneEntry("", s.filterAll)) +
            shares.orEmpty()
                .distinctBy { shareFilterKey(it) }
                .sortedBy { shareFilterLabel(it, s) }
                .map { com.freechat.ui.components.TuneEntry(shareFilterKey(it), shareFilterLabel(it, s)) }

    suspend fun reload() {
        val version = ++reloadVersion
        val auth = withContext(Dispatchers.IO) { Session.loadAuth() }
        if (auth == null) {
            if (version == reloadVersion) shares = emptyList()
            return
        }
        loadFailed = false
        try {
            val rows = withContext(Dispatchers.IO) { ApiClient.listShares(auth.token) }
            if (version == reloadVersion) shares = activeShares.visible(rows)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (version == reloadVersion) {
                loadFailed = true
                if (shares == null) shares = emptyList()
            }
        }
    }

    /** 确认后执行撤销（单条 / 批量同一条路）：服务端置 revoked 并删图片，链接随即 404 */
    suspend fun executeRevoke(context: Context, s: AppStrings) {
        val items = pendingRevoke
        pendingRevoke = emptyList()
        val ids = items.map { it.id }.filter { it !in revokingIds }
        if (ids.isEmpty()) return
        revokingIds = revokingIds + ids
        try {
            val auth = withContext(Dispatchers.IO) { Session.loadAuth() }
            if (auth == null) {
                Toast.makeText(context, s.notLoggedIn, Toast.LENGTH_SHORT).show()
                return
            }
            ids.forEach { id ->
                withContext(Dispatchers.IO) { ApiClient.revokeShare(auth.token, id) }
                activeShares.revoked(id)
            }
            shares = activeShares.visible(shares.orEmpty())
            Toast.makeText(context, s.shareRevoked, Toast.LENGTH_SHORT).show()
            exitSelect()
            reload()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiError) {
            Toast.makeText(context, e.message ?: s.networkUnreachable, Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(context, s.networkUnreachable, Toast.LENGTH_SHORT).show()
        } finally {
            revokingIds = revokingIds - ids.toSet()
        }
    }
}

/**
 * 「我的分享」：已生成的在线网页链接列表 + 管理（点卡片弹菜单 / 长按多选 / 调节筛选排序）。
 *
 * 撤销是即时的——服务端置 revoked 并删图片，链接立刻 404。
 * 列表刻意不自带 verticalScroll（嵌套滚动崩过 1.0.95），滚动归设置二级页容器。
 * 标题栏/浮层不在这里 —— 状态在 [MySharesUiState]，由 SettingsScreen 统一渲染。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MySharesScreen(s: AppStrings, state: MySharesUiState) {
    val colors = LocalFreeChatColors.current

    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(Unit) { state.reload() }

    // ⚠️ 刻意**不自带 verticalScroll**：设置二级页的容器已经包了滚动，再套一层 = 嵌套垂直滚动，
    // ScrollNode measure 强校验「无限高度」当场崩（1.0.95「我的反馈」闪退根因；本页同款结构一并修）
    Column(Modifier.fillMaxWidth()) {
        Text(
            if (state.loadFailed) s.networkUnreachable else s.shareLinkHint,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.TextSecondary,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp).clickable(enabled = state.loadFailed) {
                scope.launch { state.reload() }
            }
        )
        Spacer(Modifier.height(8.dp))
        val list = state.shares
        when {
            list == null -> Row(
                Modifier.fillMaxWidth().padding(vertical = 24.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = colors.Primary)
            }
            list.isEmpty() -> Text(
                if (state.loadFailed) s.networkUnreachable else s.mySharesEmpty,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.TextTertiary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp)
            )
            else -> {
                val displayList = state.displayList(s)
                if (displayList.isEmpty()) {
                    Text(
                        s.mySharesEmpty,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.TextTertiary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp)
                    )
                } else {
                    SettingsRow {
                        Column {
                            displayList.forEachIndexed { index, share ->
                                if (index > 0) HorizontalDivider(color = colors.Divider.copy(alpha = 0.6f))
                                ShareRow(
                                    share = share,
                                    s = s,
                                    colors = colors,
                                    selectMode = state.selectMode,
                                    isSelected = share.id in state.selectedIds,
                                    revoking = share.id in state.revokingIds,
                                    onClick = {
                                        if (state.selectMode) state.toggleSelect(share)
                                        else state.menuShare = share
                                    },
                                    onLongPress = {
                                        if (state.selectMode) state.toggleSelect(share)
                                        else state.enterSelect(share)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** 单条分享：标题 + 时间；多选态右侧勾选圈（与收藏页同一套视觉）；点卡片弹菜单、长按进多选 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShareRow(
    share: ShareInfo,
    s: AppStrings,
    colors: FreeChatColors,
    selectMode: Boolean,
    isSelected: Boolean,
    revoking: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                share.title.ifBlank { s.share },
                style = MaterialTheme.typography.bodyLarge,
                color = colors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                SimpleDateFormat(s.dateYmdTime, Locale.getDefault()).format(Date(share.createdAt)),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif),
                color = colors.TextTertiary
            )
        }
        Spacer(Modifier.width(8.dp))
        if (revoking) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.Primary)
        }
        // 多选态：右侧勾选圈，进出与选中填充都走过渡（与收藏页完全一致）
        AnimatedVisibility(
            visible = selectMode,
            enter = FreeChatAnimation.expandEnter(),
            exit = FreeChatAnimation.expandExit()
        ) {
            Row {
                Spacer(Modifier.width(12.dp))
                val fill by animateColorAsState(
                    if (isSelected) colors.Primary else Color.Transparent,
                    FreeChatAnimation.colorTween, label = "shareCheckFill")
                val stroke by animateColorAsState(
                    if (isSelected) colors.Primary else colors.TextTertiary.copy(alpha = 0.6f),
                    FreeChatAnimation.colorTween, label = "shareCheckStroke")
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(fill)
                        .border(width = 1.5.dp, color = stroke, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(Icons.Filled.Check, null, tint = colors.Background, modifier = Modifier.size(13.dp))
                    }
                }
            }
        }
    }
}
