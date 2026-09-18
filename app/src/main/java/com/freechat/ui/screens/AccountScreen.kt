package com.freechat.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.sp
import com.freechat.ui.theme.FreeChatColors
import dev.chrisbanes.haze.HazeState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.freechat.i18n.AppStrings
import com.freechat.i18n.LocalStrings
import com.freechat.sync.AccountManager
import com.freechat.sync.ApiError
import com.freechat.sync.AvatarStore
import com.freechat.sync.Session
import com.freechat.sync.SyncEngine
import com.freechat.sync.SyncUser
import com.freechat.sync.TokenInfo
import com.freechat.sync.UsageInfo
import com.freechat.ui.components.SheetActionRow
import com.freechat.ui.components.SheetPanel
import com.freechat.ui.theme.HazeSpec
import com.freechat.ui.theme.LocalAdvancedMaterial
import com.freechat.ui.theme.LocalFreeChatColors
import com.freechat.ui.theme.frostedCard
import com.freechat.ui.theme.hazeBackground
import com.freechat.ui.theme.pageHeaderBackground
import com.freechat.ui.theme.pageBackground
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 用户名的格式 —— 必须和 `FreeChatServer/src/profile.js` 里那条正则**逐字一致**。
 *
 * 本地也拦一道只是为了省一次往返，真正的判定永远在服务端（客户端拦不住改包的）。
 * 两边不一致的后果是：本地放行的名字服务端拒，用户看到的是"格式没问题但你就是不行"。
 */
private val USERNAME_RE = Regex("^[A-Za-z0-9_]{3,20}$")

/**
 * 账号页：登录 / 注册 / 找回 / 已登录状态。
 *
 * ## 这一页要一直说清楚的一件事
 *
 * **不登录也能用。** 用户在这台设备上写的东西本来就是他自己的，登录只是多一个去处。
 * 所以未登录态不是一堵墙，是一张"要不要顺便"的卡片 —— 页面顶部永远先告诉他
 * 本地已经有多少东西，再让他决定。
 *
 * ## 恢复码必须看得见、抄得下来
 *
 * 服务端只在注册/找回那一次返回它，丢了就只剩"重新生成"一条路（而重新生成要密码）。
 * 所以它不走 Toast，走一个**必须点「我存好了」才能关掉**的弹窗，并且带一键复制。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(onBack: () -> Unit) {
    val colors = LocalFreeChatColors.current
    val s = LocalStrings.current
    val advancedMaterial = LocalAdvancedMaterial.current
    val hazeState = rememberHazeState()
    val density = LocalDensity.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val statusBarHeightDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val titleBarAreaDp = 48.dp
    val topFadeZoneDp = HazeSpec.TopFadeZoneDp
    val topBarHeightPx = with(density) { (statusBarHeightDp + titleBarAreaDp + topFadeZoneDp).toPx() }

    // 内存里的登录态（同步的启动路径上也在读同一份，别在这里做磁盘 IO）
    var auth by remember { mutableStateOf(Session.peekAuth()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    /** 只在注册/找回/重新生成时非空 —— 非空就是那个"必须点我存好了"的弹窗 */
    var recoveryCode by remember { mutableStateOf<String?>(null) }
    var usage by remember { mutableStateOf<UsageInfo?>(null) }
    var user by remember { mutableStateOf<SyncUser?>(null) }
    var tokens by remember { mutableStateOf<List<TokenInfo>>(emptyList()) }
    var localSummary by remember { mutableStateOf<String?>(null) }
    var deleteAsk by remember { mutableStateOf(false) }
    /** 从相册挑中的那张原图 —— 非空就要弹裁切框 */
    var cropSource by remember { mutableStateOf<android.net.Uri?>(null) }
    var showAvatarMenu by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(false) }
    /** 已排队、等着拉起相册选择器的标记 —— 为什么不能直接拉起，见下面那个 LaunchedEffect */
    var pickAvatarQueued by remember { mutableStateOf(false) }
    var changePwd by remember { mutableStateOf(false) }
    var regen by remember { mutableStateOf(false) }
    // 弹层要拿它算磨砂底色的明暗（跟 Frosted.kt 里同一套判法：主题文字色亮就是深色模式）
    val isDark = colors.TextPrimary.luminance() > 0.5f

    // ===== 弹层里的输入状态，全部提升到这一层 =====
    // 弹层是**常驻组合、只是不可见**（靠 AnimatedVisibility 淡入淡出），状态要是写在
    // `if (showX)` 里面，每次显隐都会重建 —— 用户会觉得"我刚打的字被吃了"。
    /** 问问本地缓存那份，不是问服务端 —— 「移除头像」要不要出现取决于屏幕上到底有没有图 */
    val hasAvatar by AvatarStore.current.collectAsState()
    var nameInput by remember { mutableStateOf("") }
    var nameErr by remember { mutableStateOf<String?>(null) }
    var pwdInput by remember { mutableStateOf("") }
    var codeCopied by remember { mutableStateOf(false) }
    /** 退出动画那 140ms 里 recoveryCode 已经变 null，照着它渲染会闪一下空白 —— 留住最后一份 */
    var codeShown by remember { mutableStateOf("") }

    // 输入框的初值依赖 user/auth，是异步到货的，所以不能在 remember 里取一次就完事
    LaunchedEffect(editName) {
        if (editName) {
            nameInput = user?.username ?: auth?.username.orEmpty()
            nameErr = null
        }
    }
    LaunchedEffect(deleteAsk) { if (deleteAsk) pwdInput = "" }
    LaunchedEffect(recoveryCode) {
        if (recoveryCode != null) {
            codeShown = recoveryCode!!
            codeCopied = false
        }
    }

    // 系统相册选择器。**不用 OpenDocument** —— 那个会给用户一个"文件从哪来"的完整目录树，
    // 而这里只是挑一张图，系统相册才是最自然的入口。
    val pickAvatar = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) cropSource = uri }

    // 相册选择器必须等**那个弹窗彻底关掉之后**再拉起。
    //
    // 原来是在菜单那一行的点击回调里「showAvatarMenu = false」和「pickAvatar.launch()」同一帧做完 ——
    // 弹窗自己是一个独立窗口，此刻正在销毁；选择器又是一个要启动的新界面。部分机型上这一下会被吞掉，
    // 表现就是那句「点了『更改头像』之后什么都没发生」。
    // 改成先挂标记，等一帧弹窗退干净、再隔 300ms 拉起（300ms 也刚好盖住系统的窗口退出动画）。
    LaunchedEffect(pickAvatarQueued) {
        if (pickAvatarQueued) {
            kotlinx.coroutines.delay(300)
            pickAvatarQueued = false
            runCatching { pickAvatar.launch("image/*") }
                .onFailure { error = s.accountAvatarFailed }
        }
    }

    val syncStatus by SyncEngine.status.collectAsState()

    // 首次进入把盘上那份读出来（peekAuth 可能还没读到），顺便问一下本地有多少东西
    LaunchedEffect(Unit) {
        auth = withContext(Dispatchers.IO) { Session.loadAuth() }
        localSummary = runCatching { AccountManager.localSummary() }.getOrNull()
    }

    // 已登录时拉一次用量和设备列表。失败不弹错 —— 这是附加信息，不该挡住主流程
    LaunchedEffect(auth?.userId) {
        if (auth == null) {
            usage = null; user = null; tokens = emptyList()
            return@LaunchedEffect
        }
        runCatching { AccountManager.me() }.onSuccess { (u, g) -> user = u; usage = g }
        runCatching { AccountManager.listTokens() }.onSuccess { tokens = it }
    }

    fun run(block: suspend () -> Unit) {
        if (busy) return
        error = null
        busy = true
        scope.launch {
            try {
                block()
            } catch (e: Throwable) {
                error = messageOf(e, s)
            } finally {
                busy = false
                auth = Session.loadAuth()
            }
        }
    }

    /**
     * 「编辑头像」—— 把上次留下的**原图**重新丢进裁切框，让用户重选范围，不用再去相册翻一遍。
     *
     * 原图在 [AvatarStore]（只在本机，服务端只存成品）。万一这台设备上没有
     * （头像是在别的机器上设的、或者用户清过应用数据），就退回拿**当前头像本身**去裁：
     * 512 的方图再裁一次不会变形，只是没得放大了 —— 但总好过点了没反应。
     */
    fun startEditAvatar() {
        val uid = auth?.userId
        val bytes = uid?.let { AvatarStore.originalBytes(it) } ?: hasAvatar
        if (bytes == null || bytes.isEmpty()) {
            error = s.accountAvatarFailed
            return
        }
        // 裁切框只认 Uri，先落到缓存目录再递进去；固定文件名，每次覆盖，不留垃圾
        val tmp = java.io.File(context.cacheDir, "avatar_edit_src.jpg")
        runCatching { tmp.writeBytes(bytes) }
            .onSuccess { cropSource = android.net.Uri.fromFile(tmp) }
            .onFailure { error = s.accountAvatarFailed }
    }

    Box(Modifier.fillMaxSize().pageBackground(colors.Background)) {
        // 整页的卡片共用同一个 HazeState，模糊的才是页面内容而不是每张卡自己的空底
        CompositionLocalProvider(LocalAccountHazeState provides hazeState) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (advancedMaterial) Modifier.hazeSource(state = hazeState)
                        .hazeBackground(colors.Background) else Modifier
                )
                .verticalScroll(rememberScrollState())
                .padding(top = statusBarHeightDp + titleBarAreaDp + 24.dp, bottom = 40.dp)
                .padding(horizontal = 16.dp)
        ) {
            if (auth == null) {
                SignedOut(
                    s = s, busy = busy, error = error, localSummary = localSummary,
                    onLogin = { u, p -> run { AccountManager.login(u, p) } },
                    onRegister = { u, p -> run { recoveryCode = AccountManager.register(u, p) } },
                    onRecover = { u, code, p -> run { recoveryCode = AccountManager.recover(u, code, p) } },
                    onClearError = { error = null }
                )
            } else {
                SignedIn(
                    s = s, auth = auth!!, user = user, usage = usage, tokens = tokens,
                    status = syncStatus, busy = busy, error = error, toast = toast,
                    onSyncNow = {
                        scope.launch {
                            busy = true
                            runCatching { SyncEngine.syncNow() }
                            busy = false
                        }
                    },
                    onRevoke = { id -> run { AccountManager.revokeToken(id); tokens = AccountManager.listTokens() } },
                    onAskDelete = { deleteAsk = true },
                    onLogout = {
                        run {
                            AccountManager.logout()
                            // 登出后要回到未登录态那张卡，那里的"这台设备上已经有 N 条对话"
                            // 得重新算一遍（登出期间用户可能一直在聊）
                            localSummary = runCatching { AccountManager.localSummary() }.getOrNull()
                        }
                    },
                    onDismissError = { error = null },
                    onDismissToast = { toast = null },
                    onClearNotices = { SyncEngine.clearNotices() },
                    onPickAvatar = { showAvatarMenu = true },
                    onChangeUsername = { editName = true },
                    onAskChangePassword = { changePwd = true },
                    onAskRegenerate = { regen = true }
                )
            }
        }
        }

        // ===== 顶部标题栏背景 =====
        if (advancedMaterial) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(statusBarHeightDp + titleBarAreaDp + topFadeZoneDp)
                    .pageHeaderBackground(colors.Background)
                    .hazeEffect(state = hazeState) {
                        blurRadius = HazeSpec.TopBlurRadius
                        inputScale = HazeInputScale.None
                        backgroundColor = Color.Transparent
                        progressive = HazeProgressive.verticalGradient(
                            easing = LinearEasing, startY = 0f, startIntensity = 1f,
                            endY = topBarHeightPx, endIntensity = 0f
                        )
                    }
            )
        } else {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(statusBarHeightDp + titleBarAreaDp)
                    // 标题栏必须**不透明**（正文滚上来要被挡住）。炫彩开着时 pageBackground 是空操作
                    // —— 整页都透明，标题区就跟着透了。改用 pageHeaderBackground：炫彩关=这块底色本身，
                    // 炫彩开=钉在屏幕上的一份流光副本，两种情况下都与页面自身上下同色。
                    .pageHeaderBackground(colors.Background)
            )
        }

        // ===== 悬浮标题栏 =====
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 8.8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = colors.TextPrimary)
            }
            Text(
                s.account,
                color = colors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleLarge
            )
        }

        // ===== 五个底部磨砂哑光玻璃弹层 =====
        // 放在根 Box 的最后：要盖在所有东西之上（包括悬浮标题栏）。
        // 原来这五个是 AlertDialog —— 独立窗口看不到本页画的内容，"模糊背景"根本做不到，
        // 只能把背景压暗；现在换成窗口内的真模糊弹层，背景是糊掉而不是变黑。

        // 头像：点了自己的头像先问一句"换还是撤"，别直接弹相册
        SheetPanel(
            visible = showAvatarMenu,
            onDismiss = { showAvatarMenu = false },
            // 还没有头像时说的是「上传头像」—— 一上来就写「更换头像」，用户会先怀疑自己是不是眼花了
            title = if (hasAvatar != null) s.accountChangeAvatar else s.accountUploadAvatar,
            colors = colors,
            isDark = isDark,
            advancedMaterial = advancedMaterial,
            hazeState = hazeState
        ) {
            SheetActionRow(if (hasAvatar != null) s.accountChangeAvatar else s.accountUploadAvatar, colors) {
                // 只挂标记，不在这里 launch —— 必须等弹层退干净（原因见 pickAvatarQueued 那段注释）
                showAvatarMenu = false
                pickAvatarQueued = true
            }
            // 已经有头像了才有"重裁"可谈 —— 原图是本机留的那份，不用再去相册找
            if (hasAvatar != null) {
                SheetActionRow(s.accountEditAvatar, colors) {
                    showAvatarMenu = false
                    startEditAvatar()
                }
            }
            // 没头像就没有"移除"这个选项 —— 一个点了什么都不发生的按钮比没有更糟
            if (hasAvatar != null) {
                SheetActionRow(s.accountRemoveAvatar, colors, danger = true) {
                    showAvatarMenu = false
                    run {
                        AccountManager.removeAvatar()
                        // 头像都撤了，本机那份原图也别留着 —— 免得日后有别的入口拿它把图裁回来
                        auth?.userId?.let { AvatarStore.removeOriginal(it) }
                        toast = s.accountAvatarRemoved
                    }
                }
            }
        }

        // 改用户名
        SheetPanel(
            visible = editName,
            onDismiss = { editName = false },
            title = s.accountChangeUsername,
            colors = colors,
            isDark = isDark,
            advancedMaterial = advancedMaterial,
            hazeState = hazeState,
            confirmLabel = s.confirm,
            confirmEnabled = !busy,
            onConfirm = {
                val v = nameInput.trim()
                // 先在本地拦一道格式：服务端也会拦，但让用户为一次明显的笔误等一个往返没必要
                if (!USERNAME_RE.matches(v)) {
                    nameErr = s.accountErrUsername(s.accountUsernameRule)
                } else {
                    editName = false
                    run {
                        user = AccountManager.setUsername(v)
                        toast = s.accountUsernameChanged
                    }
                }
            }
        ) {
            OutlinedTextField(
                value = nameInput,
                onValueChange = { nameInput = it; nameErr = null },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.Primary,
                    unfocusedBorderColor = colors.InputBorder,
                    focusedTextColor = colors.TextPrimary,
                    unfocusedTextColor = colors.TextPrimary,
                    cursorColor = colors.Primary
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text(
                nameErr ?: s.accountUsernameRule,
                style = MaterialTheme.typography.labelSmall,
                color = if (nameErr != null) colors.ErrorRed else colors.TextTertiary
            )
        }

        // 恢复码：**必须点「我存好了」才关得掉**，这是用户唯一一次看到它的机会
        // —— 所以 onDismiss 是空的：点外面、按返回键都不会关，跟原来那个 AlertDialog 一样
        SheetPanel(
            visible = recoveryCode != null,
            onDismiss = { /* 刻意不可点外部关闭 */ },
            title = s.accountRecoveryTitle,
            colors = colors,
            isDark = isDark,
            advancedMaterial = advancedMaterial,
            hazeState = hazeState,
            confirmLabel = s.accountSavedIt,
            onConfirm = { recoveryCode = null }
        ) {
            val clipboard = LocalClipboardManager.current
            Text(s.accountRecoveryHint, style = MaterialTheme.typography.bodySmall, color = colors.TextSecondary)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.AccentMuted)
                    .clickable {
                        clipboard.setText(AnnotatedString(codeShown))
                        codeCopied = true
                    }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (codeCopied) s.accountCopied else codeShown,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.Primary,
                    textAlign = TextAlign.Center
                )
            }
            Text(s.accountCopy, style = MaterialTheme.typography.labelSmall, color = colors.TextTertiary)
        }

        // 注销账号：要密码，按钮红着，所以不用 TextButton 那一套，走弹层自己的主按钮
        SheetPanel(
            visible = deleteAsk,
            onDismiss = { deleteAsk = false },
            title = s.accountDelete,
            colors = colors,
            isDark = isDark,
            advancedMaterial = advancedMaterial,
            hazeState = hazeState,
            confirmLabel = s.accountDelete,
            confirmEnabled = pwdInput.isNotBlank() && !busy,
            confirmDanger = true,
            onConfirm = {
                // 先把弹层关掉再发请求：注销成功那一刻 auth 会变 null，
                // 整页会切到未登录态，留着一个绑定旧账号的弹层会闪一下
                deleteAsk = false
                run { AccountManager.deleteAccount(pwdInput) }
            }
        ) {
            Text(s.accountDeleteWarn, style = MaterialTheme.typography.bodyMedium, color = colors.TextSecondary)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = pwdInput,
                onValueChange = { pwdInput = it },
                label = { Text(s.accountDeleteConfirm) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.TextPrimary),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 改密码 / 重新生成恢复码：共用一个"问 1~2 个密码"的弹层
        PasswordSheet(
            visible = changePwd,
            title = s.accountChangePassword,
            fields = listOf(s.accountOldPassword, s.accountNewPassword),
            busy = busy,
            colors = colors,
            isDark = isDark,
            advancedMaterial = advancedMaterial,
            hazeState = hazeState,
            s = s,
            onDismiss = { changePwd = false },
            onConfirm = { vals ->
                val old = vals[0]; val new = vals[1]
                when {
                    old.isBlank() || new.isBlank() -> null
                    new.length < 8 -> s.accountErrPasswordShort
                    else -> {
                        run { AccountManager.changePassword(old, new); toast = s.accountPasswordChanged }
                        changePwd = false
                        null
                    }
                }
            }
        )

        PasswordSheet(
            visible = regen,
            title = s.accountRegenerate,
            fields = listOf(s.accountOldPassword),
            busy = busy,
            colors = colors,
            isDark = isDark,
            advancedMaterial = advancedMaterial,
            hazeState = hazeState,
            s = s,
            onDismiss = { regen = false },
            onConfirm = { vals ->
                if (vals[0].isBlank()) null else {
                    run { recoveryCode = AccountManager.regenerateRecoveryCode(vals[0]); toast = s.accountCodeRegenerated }
                    regen = false
                    null
                }
            }
        )
    }

    // ===== 裁切：挑完图先让人定位，出来的成品已经是圆的 =====
    // （这个是全屏裁切界面，本来就该独立成层，不属于"悬浮卡片加暗"那一类，保持原样）
    cropSource?.let { src ->
        com.freechat.ui.components.AvatarCropDialog(
            sourceUri = src,
            onDone = { path, original ->
                cropSource = null
                val file = java.io.File(path)
                val bytes = runCatching { file.readBytes() }.getOrNull()
                if (bytes != null && bytes.isNotEmpty()) {
                    run {
                        // 原图先存本机，下次「编辑头像」要拿它重裁 —— 服务端只留成品，重裁的余地只能在这留
                        val uid = auth?.userId
                        if (original != null && uid != null) AvatarStore.saveOriginal(uid, original)
                        AccountManager.uploadAvatar(bytes)
                        toast = s.accountAvatarChanged
                    }
                } else {
                    // 原来这一步是静默的：读不出来就什么都不发生，用户只会觉得「又没反应」
                    error = s.accountAvatarFailed
                }
                // 裁切中间文件留着没意义，占地方还容易被当成用户头像到处翻出来
                runCatching { file.delete() }
            },
            onDismiss = { cropSource = null },
            onError = { error = s.avatarCropFailed }
        )
    }
}

// ============================================================
//  未登录
// ============================================================

private enum class Tab { LOGIN, REGISTER, RECOVER }

@Composable
private fun SignedOut(
    s: AppStrings,
    busy: Boolean,
    error: String?,
    localSummary: String?,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String) -> Unit,
    onRecover: (String, String, String) -> Unit,
    onClearError: () -> Unit
) {
    val colors = LocalFreeChatColors.current
    var tab by remember { mutableStateOf(Tab.LOGIN) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var localErr by remember { mutableStateOf<String?>(null) }

    // 先讲清楚"不登录照样能用"，再讲"登录能得到什么" —— 顺序反了就变成逼人注册
    Bubble(colors = colors) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(s.accountLocalOnly, style = MaterialTheme.typography.bodyMedium, color = colors.TextPrimary)
            localSummary?.let {
                Text(s.accountLocalData(it), style = MaterialTheme.typography.labelMedium, color = colors.TextTertiary)
            }
        }
    }

    Spacer(Modifier.height(18.dp))

    // 分段控件：登录 / 注册 / 找回
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.AccentMuted)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf(
            Tab.LOGIN to s.accountTabLogin,
            Tab.REGISTER to s.accountTabRegister,
            Tab.RECOVER to s.accountTabRecover
        ).forEach { (t, label) ->
            val selected = tab == t
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (selected) colors.Surface else Color.Transparent)
                    .clickable {
                        tab = t
                        localErr = null
                        onClearError()
                    }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) colors.Primary else colors.TextSecondary,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }

    Spacer(Modifier.height(16.dp))

    Field(s.accountUsername, username, !busy) { username = it }
    Spacer(Modifier.height(10.dp))
    Field(s.accountPassword, password, !busy, password = true) { password = it }

    if (tab == Tab.REGISTER) {
        Spacer(Modifier.height(10.dp))
        Field(s.accountPasswordConfirm, confirm, !busy, password = true) { confirm = it }
    }
    if (tab == Tab.RECOVER) {
        Spacer(Modifier.height(10.dp))
        Field(s.accountRecoveryCode, code, !busy) { code = it }
    }

    val message = localErr ?: error
    if (message != null) {
        Spacer(Modifier.height(12.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = colors.ErrorRed)
    }

    Spacer(Modifier.height(18.dp))

    val actionLabel = when (tab) {
        Tab.LOGIN -> s.accountLogin
        Tab.REGISTER -> s.accountRegister
        Tab.RECOVER -> s.accountResetPassword
    }

    Button(
        onClick = {
            localErr = null
            when {
                username.isBlank() || password.isBlank() -> localErr = s.accountErrFillAll
                password.length < 8 -> localErr = s.accountErrPasswordShort
                tab == Tab.REGISTER && password != confirm -> localErr = s.accountErrMismatch
                tab == Tab.RECOVER && code.isBlank() -> localErr = s.accountErrFillAll
                else -> when (tab) {
                    Tab.LOGIN -> onLogin(username.trim(), password)
                    Tab.REGISTER -> onRegister(username.trim(), password)
                    Tab.RECOVER -> onRecover(username.trim(), code.trim(), password)
                }
            }
        },
        enabled = !busy,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = colors.Primary, contentColor = colors.OnPrimary)
    ) {
        Text(if (busy) s.accountWorking else actionLabel, fontWeight = FontWeight.Bold)
    }
}

// ============================================================
//  已登录
// ============================================================

@Composable
private fun SignedIn(
    s: AppStrings,
    auth: Session.Auth,
    user: SyncUser?,
    usage: UsageInfo?,
    tokens: List<TokenInfo>,
    status: SyncEngine.Status,
    busy: Boolean,
    error: String?,
    toast: String?,
    onSyncNow: () -> Unit,
    onRevoke: (String) -> Unit,
    onAskDelete: () -> Unit,
    onLogout: () -> Unit,
    onDismissError: () -> Unit,
    onDismissToast: () -> Unit,
    onClearNotices: () -> Unit,
    onPickAvatar: () -> Unit,
    onChangeUsername: () -> Unit,
    // 问密码的两个弹层已经搬到页面根 Box 去了（弹层要在最上层，而这里是滚动内容里的一层）。
    // 这一层只负责举手，真正的弹层和它的输入状态都在 AccountScreen 那一层。
    onAskChangePassword: () -> Unit,
    onAskRegenerate: () -> Unit
) {
    val colors = LocalFreeChatColors.current

    Bubble(colors = colors) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 头像只读本地缓存那份 —— 点它才去问服务器，不该为了画一个圈先等一次网络
            val avatarBytes by com.freechat.sync.AvatarStore.current.collectAsState()
            val avatarBmp = remember(avatarBytes) {
                avatarBytes?.let { runCatching { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull() }
            }
            // 外圈：白描边，只把头像从卡片底上轻轻托起来。粗细与抽屉左下角那颗共用
            // （[AvatarRingWidth]）—— 两处必须一样，否则点进账号页会看到头像换了个边框。
            val avatarIsDark = colors.TextPrimary.luminance() > 0.5f
            val avatarLabel = if (avatarBmp != null) s.accountChangeAvatar else s.accountUploadAvatar
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(colors.AccentMuted)
                    .border(com.freechat.ui.components.AvatarRingWidth, Color.White.copy(alpha = if (avatarIsDark) 0.20f else 0.60f), CircleShape)
                    .clickable(enabled = !busy) { onPickAvatar() },
                contentAlignment = Alignment.Center
            ) {
                if (avatarBmp != null) {
                    Image(
                        bitmap = avatarBmp.asImageBitmap(),
                        contentDescription = avatarLabel,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.Filled.Person, avatarLabel, tint = colors.TextTertiary, modifier = Modifier.size(28.dp))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(s.accountSignedInAs, style = MaterialTheme.typography.labelMedium, color = colors.TextTertiary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        user?.username ?: auth.username,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.TextPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = s.accountChangeUsername,
                        tint = colors.TextTertiary,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable(enabled = !busy) { onChangeUsername() }
                    )
                }
            }
            if (busy) CircularProgressIndicator(
                modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.Primary
            )
        }
    }

    Spacer(Modifier.height(12.dp))

    // ===== 同步状态 =====
    Bubble(colors = colors) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(syncTitle(status, s), style = MaterialTheme.typography.bodyLarge, color = colors.TextPrimary)
                    Text(
                        status.lastSyncAt.takeIf { it > 0 }?.let { s.accountSyncLast(timeText(it)) }
                            ?: s.accountSyncNever,
                        style = MaterialTheme.typography.labelSmall, color = colors.TextTertiary
                    )
                }
                TextButton(onClick = onSyncNow, enabled = !busy) {
                    Text(s.accountSyncNow, color = colors.Primary, fontWeight = FontWeight.Bold)
                }
            }
            if (status.pending > 0) {
                Text(
                    s.accountSyncPending(status.pending),
                    style = MaterialTheme.typography.labelSmall, color = colors.TextTertiary
                )
            }
            status.error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = colors.ErrorRed)
            }
            if (status.notices.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.AccentMuted)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(s.accountNotices, style = MaterialTheme.typography.labelMedium, color = colors.TextSecondary)
                    status.notices.forEach {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = colors.TextSecondary)
                    }
                    TextButton(onClick = onClearNotices) {
                        Text(s.confirm, color = colors.Primary, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }

    // ===== 云端占用 =====
    usage?.takeIf { it.bytesLimit > 0 }?.let { u ->
        Spacer(Modifier.height(12.dp))
        Bubble(colors = colors) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(s.accountUsage, style = MaterialTheme.typography.bodyMedium, color = colors.TextPrimary)
                    Text(
                        "${sizeText(u.bytesUsed)} / ${sizeText(u.bytesLimit)}",
                        style = MaterialTheme.typography.labelMedium, color = colors.TextTertiary
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(colors.AccentMuted)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(
                                (u.bytesUsed.toFloat() / u.bytesLimit.toFloat()).coerceIn(0f, 1f)
                            )
                            .fillMaxHeight()
                            .background(colors.Primary)
                    )
                }
            }
        }
    }

    // ===== 登录中的设备 =====
    if (tokens.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Bubble(colors = colors) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(s.accountDevices, style = MaterialTheme.typography.bodyMedium, color = colors.TextPrimary)
                tokens.forEach { t ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                t.label.ifBlank { t.id.take(8) },
                                style = MaterialTheme.typography.bodySmall, color = colors.TextSecondary
                            )
                            Text(
                                timeText(t.lastUsed.takeIf { it > 0 } ?: t.createdAt),
                                style = MaterialTheme.typography.labelSmall, color = colors.TextTertiary
                            )
                        }
                        TextButton(onClick = { onRevoke(t.id) }, enabled = !busy) {
                            Text(s.accountRevoke, color = colors.ErrorRed, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }

    // ===== 账号操作 =====
    Spacer(Modifier.height(12.dp))
    Bubble(colors = colors) {
        Column {
            ActionRow(s.accountChangePassword, colors, enabled = !busy) { onAskChangePassword() }
            HorizontalDivider(color = colors.Divider.copy(alpha = 0.4f))
            ActionRow(s.accountRegenerate, colors, enabled = !busy) { onAskRegenerate() }
            HorizontalDivider(color = colors.Divider.copy(alpha = 0.4f))
            ActionRow(s.accountLogout, colors, danger = true, enabled = !busy) { onLogout() }
            Text(
                s.accountLogoutHint,
                style = MaterialTheme.typography.labelSmall,
                color = colors.TextTertiary,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 14.dp, top = 0.dp)
            )
            HorizontalDivider(color = colors.Divider.copy(alpha = 0.4f))
            ActionRow(s.accountDelete, colors, danger = true, enabled = !busy) { onAskDelete() }
        }
    }

    // 出错/成功都紧挨着操作区显示，点一下就能消掉 —— 不然它会一直挂在那儿
    val message = error ?: toast
    if (message != null) {
        Spacer(Modifier.height(12.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = if (error != null) colors.ErrorRed else colors.SuccessGreen,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { if (error != null) onDismissError() else onDismissToast() }
                .padding(4.dp)
        )
    }

    // 改密码 / 重新生成恢复码这两个问密码的弹层也搬到 AccountScreen 的根 Box 去了
    // （弹层要在最上层，而这一层是滚动内容中间的一格）—— 这里只负责举手，见 onAskChangePassword / onAskRegenerate
}

// ============================================================
//  小零件
// ============================================================

/** 本页所有卡片共用一个 HazeState —— 每张卡各建一个的话模糊的是各自的空背景，等于没有毛玻璃 */
private val LocalAccountHazeState = staticCompositionLocalOf<HazeState?> { null }

@Composable
private fun Bubble(colors: FreeChatColors, content: @Composable () -> Unit) {
    val advancedMaterial = LocalAdvancedMaterial.current
    val hazeState = LocalAccountHazeState.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .frostedCard(hazeState, colors, advancedMaterial, RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) { content() }
}

@Composable
private fun Field(
    label: String,
    value: String,
    enabled: Boolean,
    password: Boolean = false,
    onChange: (String) -> Unit
) {
    val colors = LocalFreeChatColors.current
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, color = colors.TextTertiary) },
        enabled = enabled,
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.TextPrimary),
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * 弹层里的一行操作。_[danger] 排在 [enabled] 前面、且 [enabled] 有默认值，是刻意的_：
 *
 * 这两个参数**都是布尔**，位置挨着，一次 `ActionRow(文案, colors, false)` 的顺口调用就足以
 * 把某一行的点击整个关掉 —— 而它看起来和别的行一模一样，只是**点上去毫无反应、连水波纹都没有**
 * （用户的原话：「点击无反应，像是只是一行文字」）。账号页那两行头像操作就这么死了两个版本。
 * 现在第三个位置留给 danger（写成 false 也无非是不变红），enabled 想关必须写名字。
 */
@Composable
private fun ActionRow(
    label: String,
    colors: FreeChatColors,
    danger: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Text(
        label,
        style = MaterialTheme.typography.bodyMedium,
        color = if (danger) colors.ErrorRed else colors.TextPrimary,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 4.dp, vertical = 15.dp)
    )
}

/**
 * 一次问 1~2 个密码的通用弹层。[onConfirm] 返回非空字符串就当校验失败、不关窗。
 *
 * 必须在页面根 Box 里**无条件**调用（只靠 visible 控制显隐）：里面的输入内容放在
 * `remember` 上，如果挂在 `if (showX)` 里，每次显隐都会把它重建 —— 用户会看到自己刚打的
 * 密码被清空。开一次清一次是在 [LaunchedEffect] 里显式做的。
 */
@Composable
private fun BoxScope.PasswordSheet(
    visible: Boolean,
    title: String,
    fields: List<String>,
    busy: Boolean,
    colors: FreeChatColors,
    isDark: Boolean,
    advancedMaterial: Boolean,
    hazeState: HazeState,
    s: AppStrings,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> String?
) {
    val values = remember { mutableStateListOf<String>().apply { repeat(fields.size) { add("") } } }
    var err by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(visible) {
        if (visible) {
            repeat(values.size) { values[it] = "" }
            err = null
        }
    }
    SheetPanel(
        visible = visible,
        onDismiss = onDismiss,
        title = title,
        colors = colors,
        isDark = isDark,
        advancedMaterial = advancedMaterial,
        hazeState = hazeState,
        confirmLabel = s.confirm,
        confirmEnabled = !busy,
        onConfirm = { onConfirm(values.toList())?.let { err = it } }
    ) {
        fields.forEachIndexed { i, label ->
            Field(label, values[i], !busy, password = true) { values[i] = it }
        }
        err?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.ErrorRed) }
    }
}

private fun syncTitle(st: SyncEngine.Status, s: AppStrings): String = when (st.phase) {
    SyncEngine.Phase.OFF -> s.accountSyncOff
    SyncEngine.Phase.SYNCING -> s.accountSyncSyncing
    SyncEngine.Phase.ERROR -> s.accountSyncError
    SyncEngine.Phase.IDLE -> s.accountSyncIdle
}

/** 网络不通和"服务器说你不对"必须分开讲：前者等会儿自己会好，后者要用户动手 */
private fun messageOf(e: Throwable, s: AppStrings): String = when {
    e is ApiError && e.isNetworkError -> s.accountErrNetwork
    e is ApiError -> e.message?.takeIf { it.isNotBlank() } ?: s.apiError
    else -> e.message?.takeIf { it.isNotBlank() } ?: s.apiError
}

private fun timeText(ms: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(ms))

private fun sizeText(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes / 1024.0 / 1024 / 1024)
    bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024)
    bytes >= 1024L -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
