package com.freechat.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import com.freechat.i18n.LocalStrings
import com.freechat.model.Conversation
import com.freechat.model.ChatMode
import com.freechat.model.SearchResultItem
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import kotlin.math.pow
import kotlin.math.roundToInt
import java.io.File
import com.freechat.ui.animation.FreeChatAnimation
import com.freechat.ui.theme.HazeSpec
import com.freechat.ui.theme.LocalAdvancedMaterial
import com.freechat.ui.theme.LocalFreeChatColors
import com.freechat.ui.theme.frostedCard
import com.freechat.ui.theme.frostedGlass
import com.freechat.ui.theme.softShadow
import com.freechat.ui.theme.seamFeather
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import com.freechat.ui.theme.pageBackground
import com.freechat.ui.theme.hazeBackground
import com.freechat.ui.theme.LocalLiquidPalette
import com.freechat.ui.theme.pageHeaderBackground
import com.freechat.ui.theme.liquidOpaqueBackground
import com.freechat.ui.theme.liquidSourceBackdrop
import com.freechat.ui.theme.LocalLiquidMode

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DrawerContent(
    conversations: List<Conversation>,
    currentId: String?,
    isDark: Boolean,
    /**
     * 侧滑页的 Haze 状态由**外面**持有并传进来（原来是在这一层 remember 的）。
     * 因为那三个对话弹层搬到了 MainActivity 的根 Box 上画，它们要糊的正是侧滑页的列表内容 ——
     * 只有在同一个 state 上，弹层才采得到背后的抽屉，不然玻璃后面是透明的。
     */
    drawerHazeState: HazeState,
    searchQuery: String,
    searchResults: List<SearchResultItem>,
    favoriteConvIds: Set<String> = emptySet(),
    onSearchQueryChange: (String) -> Unit,
    onNewChat: () -> Unit,
    onSelectConversation: (Conversation) -> Unit,
    onSelectSearchResult: (Conversation, String) -> Unit,
    onPinConversation: (Conversation) -> Unit,
    // ===== 多选（长按对话进入）：批量置顶 / 批量删除，落盘只做一次（见 ChatViewModel.setPinnedConversations）=====
    onBatchPinConversations: (Set<String>, Boolean) -> Unit,
    // ===== 三个对话弹层（重命名 / 删除 / 批量删除）只在这里"举手"，弹层本身在 MainActivity 画 =====
    // 为什么不在这一层画：侧滑层那一格只有 90% 屏宽（还是被 graphicsLayer 平移的），弹层挂在这儿
    // 就是一块贴着左边、右边短一截的怪东西，铺不满屏、遮罩也只盖住抽屉。
    // 弹层的状态（谁 / 输入框里那行字）也随之提到 MainActivity。
    onAskRenameConversation: (Conversation) -> Unit,
    onAskDeleteConversation: (Conversation) -> Unit,
    onAskBatchDelete: (List<Conversation>) -> Unit,
    // 抽屉是否可见：面板是常驻组合的（只是平移出屏），关掉时要把多选收回，否则下次拉开还留着上次的勾选
    drawerVisible: Boolean = true,
    // 批量删除的确认弹层现在画在 MainActivity，删完得有人把抽屉从多选态收回来 ——
    // 而多选是这一层的内部状态，外面够不着，所以用一把自增的"钥匙"来触发（值本身没意义，变大就清一次）
    exitSelectKey: Int = 0,
    onOpenSettings: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenAccount: () -> Unit = {},
    onSettingsRowPositioned: ((Rect) -> Unit)? = null,
    onFavoritesRowPositioned: ((Rect) -> Unit)? = null,
    onNewChatRect: ((Rect) -> Unit)? = null
) {
    val colors = LocalFreeChatColors.current
    val s = LocalStrings.current
    val advancedMaterial = LocalAdvancedMaterial.current
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    // 侧滑页独立 Haze 状态：历史对话列表作为模糊源，顶部/底部做与 Chat 页同规格的高斯模糊。
    // 状态本身由调用方持有（原因见参数注释）

    // 对话操作菜单：窗口内 overlay（非 Popup），高级材质下才能真磨砂玻璃糊住背后列表。
    // 入口是每行右侧的「···」按钮 —— 长按已改作「进入多选」，不再唤出菜单。
    var showConvMenu by remember { mutableStateOf(false) }
    var menuConv by remember { mutableStateOf<Conversation?>(null) }
    var menuY by remember { mutableStateOf(0f) }
    var menuRowWidthPx by remember { mutableFloatStateOf(0f) }
    // 重命名 / 删除确认弹层的状态已经提到 MainActivity（原因见上面那三个 onAskXxx 参数）
    // 多选：长按任意对话进入；勾选集合只存 id，展示/批量操作都从当前列表反查，避免留下失效 id
    var selectMode by remember { mutableStateOf(false) }
    var selectedConvIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    fun exitSelectMode() {
        selectMode = false
        selectedConvIds = emptySet()
    }
    val selectedConversations = conversations.filter { it.id in selectedConvIds }
    val allSelectedPinned = selectedConversations.isNotEmpty() && selectedConversations.all { it.isPinned }

    // rowWidth = 该行实测宽度（px）。菜单要右对齐到「···」键那侧，而「···」键的右边缘
    // 正好等于行宽（列表左右各 8dp 内边距，与行内 end padding 8dp 相抵），所以行宽就够了，
    // 不需要再去量抽屉宽度（抽屉是屏宽的 90%，写死比例在平板/折叠屏上会错位）。
    val openConvMenu: (Conversation, Float, Float) -> Unit = { conv, y, rowWidth ->
        menuConv = conv
        menuY = y
        menuRowWidthPx = rowWidth
        showConvMenu = true
    }
    val enterSelectMode: (Conversation) -> Unit = { conv ->
        selectMode = true
        selectedConvIds = setOf(conv.id)
    }
    val toggleSelectConv: (Conversation) -> Unit = { conv ->
        val next = if (conv.id in selectedConvIds) selectedConvIds - conv.id else selectedConvIds + conv.id
        // 取消到空就自动退出多选，省得留一个「已选择 0 项」的空壳
        if (next.isEmpty()) exitSelectMode() else selectedConvIds = next
    }

    // 抽屉关掉就把多选和「···」菜单一起收回：面板是常驻组合、只做平移动画，
    // 状态不清的话下次拉开菜单会原样浮出来，还停在关闭前记的旧 Y 上（可能已经对着另一条对话了）。
    // （弹层那几个 target 的清理跟着状态一起搬去了 MainActivity）
    LaunchedEffect(drawerVisible) {
        if (!drawerVisible) {
            exitSelectMode()
            showConvMenu = false
            menuConv = null
        }
    }

    // 外面（MainActivity 的批量删除弹层）确认删完之后，把多选收回：勾中的那几条已经没了，
    // 留着「已选择 N 项」的空壳最莫名其妙
    LaunchedEffect(exitSelectKey) {
        if (exitSelectKey > 0) exitSelectMode()
    }

    // 搜索时不分组、走消息级结果；平时按时间分类分组展示（@Composable 函数，须在 LazyColumn 构建 lambda 之外调用）
    val groups = if (searchQuery.isBlank()) groupConversations(conversations) else null

    // =============================================
    // 当前对话竖条（全列表唯一一根，从 A 行滑到 B 行）
    //
    // **这一版是重做的**：上一版的位置由「每行上报自己的根坐标」推出来，那是病灶 ——
    // positionInRoot 含祖先 layer 的平移，而点开对话的瞬间抽屉正在滑走，上报值每帧都在变，
    // 竖条被拽着乱窜。真机 60fps 录屏逐帧量出来的轨迹：点下去 16ms 内从 y=1096 直接跳到 521，
    // 下一帧弹回 732，再跳到 632 —— 全程没有一段连续位移，看着就是「啪」地闪一下。
    //
    // 现在纵向位置改从 [LazyListState.layoutInfo] 现算：itemInfo.offset 是**视口坐标**，
    // 与抽屉平移无关、随滚动自动更新，只在布局期读，不进组合。
    // 动画只驱动一个 0→1 的进度，实际落点 = 目标行位置 + 残余偏移 ×(1−进度)：
    // 滚动、抽屉滑走、动画三者互不干扰，竖条永远贴着目标行。
    //
    // 缓动本身已经把「慢—快—慢」画在位移上（见 FreeChatAnimation.indicatorSlideTween），
    // 长度再在中段**拉长 30%**、落位收回（[DrawerIndicatorBar] 的绘制块）—— 把「速度」也画出来，
    // 看着才是滑过去而不是匀速平移。
    // 时序：点完对话抽屉只等 INDICATOR_DRAWER_DELAY_MS 就关，两者同时进行（见那个常量的说明）。
    // =============================================
    val indicatorHeightPx = with(density) { 30.dp.toPx() }
    // 竖条左边缘 = 列表 contentPadding(8dp) + 行内 start padding(16dp)（两种布局同值）
    val indicatorStartPadPx = with(density) { 24.dp.toPx() }
    val indicatorProgress = remember { Animatable(1f) }
    val indicatorDelta = remember { mutableFloatStateOf(0f) }
    // 上一帧竖条**真正画在**哪儿。故意的非 Compose 状态（FloatArray 而非 mutableStateOf）：
    // 它只在绘制期写、只在切换那一刻读一次，不需要触发任何重组/重绘 ——
    // 用 state 写反而会在布局期产生回写。行被滚出视口时，它记的就是视口外那个落点，
    // 切换时天然变成「从屏幕上/下边缘进来」。
    val indicatorDrawnY = remember { FloatArray(1) }
    // 上一次选中的是哪个对话：切换动画要按它去布局里找「上一行现在在哪」（见下面 LaunchedEffect）
    var indicatorLastId by remember { mutableStateOf<String?>(null) }
    // 动画「装上」的是哪个对话。故意的非 Compose 状态：只在 LaunchedEffect 里写、只在布局期读，
    // 而布局期在动画期间每帧都跑，不需要它来触发重组。它是**这一帧该按哪条分支画**的开关：
    // currentId 换人后、LaunchedEffect 还没执行的那一帧，里面装的还是上一个 id（见 DrawerIndicatorBar）。
    val indicatorInstalledId = remember { arrayOfNulls<String>(1) }
    val indicatorViewport = remember { mutableFloatStateOf(0f) }
    // 坐标系！[LazyListState.layoutInfo] 里 itemInfo.offset 是**内容坐标系**的量：
    // 列表还没滚动时首个 item 的 offset 是 0，而不是 contentPadding 的值（列表带
    // contentPadding 时 viewportStartOffset 是负的、正好等于「内容起点相对视口顶」）。
    // 而竖条画在抽屉面板那个 Box 里（不是 LazyColumn 内部），所以必须补上
    // −viewportStartOffset 才是这一行真正画在面板上的位置。
    // 1.0.49 竖条在行内画（行自己的布局坐标）没这问题，搬出来浮在列表上层才暴露：
    // 实测同一行 offset=142、root.y=990，差值 848 = 头部 contentPadding。
    fun indicatorYOf(info: androidx.compose.foundation.lazy.LazyListItemInfo): Float =
        info.offset - listState.layoutInfo.viewportStartOffset +
            info.size / 2f - indicatorHeightPx / 2f

    // 切换动画的**起点**：上一行此刻的位置 + 正在跑的那次动画的残余位移。两处共用同一份算法：
    // ① LaunchedEffect 里用它定 delta；② currentId 刚换、动画还没装上的那一帧（见 DrawerIndicatorBar
    // 的 offset 块），直接拿它当落点 —— 两边算法要是各写一份，接缝处就会差一帧。
    fun indicatorStartY(): Float {
        val prevInfo = indicatorLastId?.let { id ->
            listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }
        }
        val raw = if (prevInfo != null) {
            indicatorYOf(prevInfo) + indicatorDelta.floatValue * (1f - indicatorProgress.value)
        } else {
            // 上一行被滚出可视区了（这次切换是「凭空」开始的）：从它出去的那一边进屏
            if (indicatorDrawnY[0] < indicatorViewport.floatValue / 2f) -indicatorHeightPx
            else indicatorViewport.floatValue
        }
        return raw.coerceIn(0f, (indicatorViewport.floatValue - indicatorHeightPx).coerceAtLeast(0f))
    }

    var indicatorReady by remember { mutableStateOf(false) }

    LaunchedEffect(currentId, searchQuery) {
        // 搜索态整根不画（结果行按消息列，没有「当前对话行」可言）：
        // 把动画收干净，免得退出搜索时竖条还悬在半路
        if (currentId == null || searchQuery.isNotBlank()) {
            indicatorDelta.floatValue = 0f
            indicatorProgress.snapTo(1f)
            return@LaunchedEffect
        }
        // 行还没量到（首帧 / 懒加载还没铺开）就等它出现，别把这一次切换漏掉
        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == currentId }
            ?: snapshotFlow { listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == currentId } }
                .filterNotNull().first()
        val targetY = indicatorYOf(info)
        if (indicatorReady) {
            // 起点 = 上一行**此刻的位置** + 正在跑的那次动画的残余位移（算法见 indicatorStartY）。
            // ⚠️ 两个坑都踩过了：
            // ① 用「新目标 + 残余偏移」凑起点 → 残余偏移是相对**上一个目标**算的，
            //    配着新目标读出来恒等于 0，竖条原地不动（第一版就这样，动画像没播）。
            // ② 用绘制期记下的「上一帧画在哪」当起点 → LaunchedEffect 的执行时机在
            //    这一帧布局/绘制之后，读到的已经是**新位置**了，delta 又是 0（同一副面孔）。
            //    行位置只跟列表滚动有关、跟选中谁无关，所以从布局查上一行是稳的。
            indicatorDelta.floatValue = indicatorStartY() - targetY
            indicatorProgress.snapTo(0f)
            // ⚠️ 这一行必须紧跟 snapTo(0f)：装上 id 之前，布局期那一帧会走 DrawerIndicatorBar
            // 的「按起点画」分支 —— 真机上是 0 帧（LaunchedEffect 与布局同帧内前后脚），
            // 慢一点的机器是 1~2 帧，两种情况落点都跟这里算的起点一致，接得住。
            indicatorInstalledId[0] = currentId
            indicatorProgress.animateTo(1f, FreeChatAnimation.indicatorSlideTween)
        } else {
            // 首次不做动画：一进 App 就到位，免得开屏看见竖条从顶上滑下来
            indicatorDelta.floatValue = 0f
            indicatorProgress.snapTo(1f)
            indicatorInstalledId[0] = currentId
            indicatorReady = true
        }
        indicatorLastId = currentId
    }

    val statusBarHeightDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val titleBarAreaDp = HazeSpec.TitleBarAreaDp
    val bottomBandHeight = HazeSpec.bottomBandHeightDp()
    val bottomBarHeightPx = with(density) { bottomBandHeight.toPx() }
    // 固定悬浮头部（搜索框 + 新对话）高度：标题栏下方留白 + 搜索框 + 间距 + 新对话 + 底部间距
    val headerGapTopDp = 24.dp
    val headerCardHeightDp = HeaderCardHeight
    val headerCardGapDp = 10.dp
    val headerGapBottomDp = 12.dp
    // 多选态下头部只剩一条操作栏（搜索框/新对话被替换掉），高度必须同步收窄：
    // 顶部模糊带高度与列表 contentPadding 都由它推导，不然列表会空出一大截
    val headerTotalDp = if (selectMode)
        headerGapTopDp + headerCardHeightDp + headerGapBottomDp
    else
        headerGapTopDp + headerCardHeightDp + headerCardGapDp + headerCardHeightDp + headerGapBottomDp
    // 顶部模糊层界限下移：覆盖固定头部到底部，保证卡片内容可读；渐隐带再往下延伸，列表靠近新对话就开始虚化。
    // 带高 / endY 由 HazeSpec.topBandHeightDp 统一产出：Chat 页调同一函数，只有「页面自有固定内容区」
    // 这一项不同（抽屉 = headerTotalDp，Chat = 0），标题栏 / 渐隐区 / 半径 / 曲线逐项相同。
    val fadeExtendDp = HazeSpec.TopFadeExtendDp
    val topBandHeight = HazeSpec.topBandHeightDp(statusBarHeightDp, contentZoneDp = headerTotalDp, fadeExtendDp = fadeExtendDp)
    val topBlurHeightPx = with(density) { topBandHeight.toPx() }
    // 接缝羽化宽度（px）：与 Chat 页同值同曲线，缝两侧的模糊衰减完全对称
    val seamFeatherPx = with(density) { HazeSpec.SeamFeatherDp.toPx() }

    if (advancedMaterial) {
        // ===== 高级材质：沉浸式文字流 + 上下高斯模糊，搜索/新对话固定悬浮（透光不透物磨砂玻璃） =====
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pageBackground(colors.Background)
        ) {
            // ===== 底部渐隐区的「采样垫底」：垫在列表源节点下面的一小块不透明流光副本 =====
            // 炫彩下列表源是透明的 → Haze 抓到的样本只有字没有底 → 磨完盖不住下面清晰的正文，
            // 底部那条渐进模糊带就成了「墨汁晕开、但内容还读得出来」。这块让样本自己带上底。
            // 不叠 drawerVeil：高级材质下抽屉面板本身就没罩这层色（见 MainActivity 的 drawer 层），
            // 垫上罩色反而让带子和面板差出一层淡色。原理与 zIndex 的讲究见 liquidSourceBackdrop。
            if (advancedMaterial && LocalLiquidMode.current) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(bottomBandHeight)
                        .hazeSource(state = drawerHazeState, zIndex = -1f)
                        .liquidSourceBackdrop(colors.Background)
                )
            }

            // ===== 对话列表 + 当前对话竖条：**必须共用一个模糊源** =====
            // 从固定头部下方开始，滚动时内容钻到头部之下。
            //
            // 竖条原先跟列表是**兄弟节点**：画在列表之上、却在源节点之外。上下两条模糊带磨出来的
            // 样本里没有它，而带子在不透明底上是**不透明**的 —— 于是竖条一滑进带子就被整条盖掉
            // （用户看到的就是「滑到顶/底直接被截断」）。现在把两者包进同一个源节点：
            // 竖条跟它那一行一样进样本、一样被磨糊、一样在带子里渐隐 —— 要的就是这个。
            //
            // hazeSource 只在高级材质下挂（本分支已在 advancedMaterial 里）：Haze 的**源**节点
            // 不看 blurEnabled，只要挂上就每帧把这一屏（90% 屏宽 × 整屏高）录成图层再交给模糊。
            // 高级材质关掉时这一层根本没人消费，白录一整屏 —— 对话列表是全 App 最长的列表，
            // 这一笔是实打实的帧开销（1.0.49 顺手治掉）。
            //
            // ⚠️ 进了源节点，竖条每帧的位置照样准：位置是 `.offset {}` 在**布局期**现算的
            // （layoutInfo / delta / progress），布局期的读**被观察** —— 该重排就重排，
            // 而重排会让源节点的图层作废、重录一帧。会冻住的只有「在源节点**绘制期**读、
            // 且读完不改布局」的那种动画（1.0.50 那个背景静止的坑），竖条不属此类：
            // 它的长度脉冲（绘制期读 progress）与位移（布局期读 progress）永远同帧变化。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = drawerHazeState)
                    .hazeBackground(colors.Background)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 8.dp, end = 8.dp,
                        top = statusBarHeightDp + titleBarAreaDp + headerTotalDp,
                        bottom = 96.dp
                    )
                ) {
                    if (searchQuery.isNotBlank()) {
                        searchResultItems(searchResults, s, onSelectSearchResult)
                    } else {
                        conversationItems(
                            conversations, groups, currentId, isDark,
                            selectMode, selectedConvIds,
                            onSelectConversation, enterSelectMode, toggleSelectConv, openConvMenu,
                            favoriteConvIds, s, drawerHazeState
                        )
                    }
                }

                // 当前对话竖条（全列表唯一一根，滑着换行 —— 详见 DrawerIndicatorBar 的说明）。
                // 位置放在列表**之后**：源节点内它就在列表内容之上，源节点外它又整体在模糊带之下。
                DrawerIndicatorBar(
                    colors = colors,
                    listState = listState,
                    currentId = if (searchQuery.isBlank()) currentId else null,
                    progress = indicatorProgress,
                    delta = indicatorDelta,
                    drawnY = indicatorDrawnY,
                    viewport = indicatorViewport,
                    installedId = indicatorInstalledId,
                    startY = { indicatorStartY() },
                    startPadPx = indicatorStartPadPx,
                    heightPx = indicatorHeightPx
                ) { indicatorViewport.floatValue = it }
            }

            // 顶部渐变模糊（界限下移到固定头部底部，保证卡片内容可读）
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(topBandHeight)
                    .pageHeaderBackground(colors.Background, LocalLiquidPalette.current.drawerVeil)
                    // 接缝羽化：右边缘正是与 Chat 页之间的接缝，模糊强度衰减到 0
                    .seamFeather(seamFeatherPx, fromEnd = true)
                    .hazeEffect(state = drawerHazeState) {
                        blurRadius = HazeSpec.TopBlurRadius
                        inputScale = HazeInputScale.None
                        backgroundColor = Color.Transparent
                        progressive = HazeProgressive.verticalGradient(easing = LinearEasing, startY = 0f, startIntensity = 1f, endY = topBlurHeightPx, endIntensity = 0f)
                    }
            )
            // 点击隔离层：只覆盖原头部区域（延伸渐隐区内容仍可点）
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(statusBarHeightDp + titleBarAreaDp + headerTotalDp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* 隔离点击：标题栏区域不可点（搜索/新对话在其上，仍可点） */ }
            )

            // 固定悬浮头部：搜索框 + 新对话（透光不透物磨砂玻璃，列表在其下滚动）；
            // 多选态换成一条操作栏：取消 / 已选择 N 项 / 置顶 / 删除
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = titleBarAreaDp + headerGapTopDp)
            ) {
                if (selectMode) {
                    DrawerSelectionBar(
                        count = selectedConversations.size,
                        allPinned = allSelectedPinned,
                        hazeState = drawerHazeState,
                        isDark = isDark,
                        onExit = { exitSelectMode() },
                        onTogglePin = {
                            onBatchPinConversations(selectedConvIds, !allSelectedPinned)
                            exitSelectMode()
                        },
                        onDelete = { onAskBatchDelete(selectedConversations) },
                        modifier = Modifier.padding(horizontal = HeaderCardInset)
                    )
                } else {
                    SearchBox(searchQuery, onSearchQueryChange, drawerHazeState, isDark, Modifier.padding(horizontal = HeaderCardInset))
                    Spacer(Modifier.height(headerCardGapDp))
                    NewChatButton(onNewChat, onNewChatRect, drawerHazeState, isDark, Modifier.padding(horizontal = HeaderCardInset))
                }
            }

            // 底部渐变模糊（与 Chat 页同规格）
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(bottomBandHeight)
                    // 1.0.50 尾巴：这里原来垫的是 liquidOpaqueBackground（一条与 progressive
                    // 同斜率的渐显衬底）。它只能把正文**压暗**，压不掉那条半透明的模糊层
                    // —— 用户看到的还是「墨汁晕开、内容仍可读」。现在衬底挪到了**源节点下面**
                    // 那一块（见上面 liquidSourceBackdrop），让磨出来的层本身不透明，
                    // 才真的把清晰正文替换掉。这里不能再垫了：两层叠起来正文会被吃掉两遍。
                    // 接缝羽化：底部带子同样横跨接缝，右边缘强度归零
                    .seamFeather(seamFeatherPx, fromEnd = true)
                    .hazeEffect(state = drawerHazeState) {
                        blurRadius = HazeSpec.BottomBlurRadius
                        inputScale = HazeInputScale.None
                        backgroundColor = Color.Transparent
                        progressive = HazeProgressive.verticalGradient(easing = LinearEasing, startY = 0f, startIntensity = 0f, endY = bottomBarHeightPx, endIntensity = 1f)
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* 隔离点击：底部模糊区下的内容不可点 */ }
            )

            // 左下角一排：账号（圆头像）· 设置 · 收藏，三个一样高、间距一致
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    // 整排往右挪一点（24 → 32dp），与头部卡片的落点呼应；头像现在 44dp，
                    // 比旁边两个胶囊大一圈，靠外一点三个按钮的重心才对得上
                    .padding(start = 32.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AccountAvatarButton(
                    onClick = onOpenAccount,
                    hazeState = drawerHazeState
                )
                FloatingDrawerButton(
                    icon = Icons.Filled.Settings,
                    label = s.settings,
                    onClick = onOpenSettings,
                    onPositioned = onSettingsRowPositioned,
                    hazeState = drawerHazeState
                )
                FloatingDrawerButton(
                    icon = Icons.Filled.Favorite,
                    label = s.favorites,
                    onClick = onOpenFavorites,
                    onPositioned = onFavoritesRowPositioned,
                    hazeState = drawerHazeState
                )
            }
        }
    } else {
        // ===== 非高级材质：原布局（固定搜索/新对话/底部设置，非沉浸） =====
        Column(
            modifier = Modifier
                .fillMaxSize()
                .pageBackground(colors.Surface)
                .statusBarsPadding()
                .padding(start = 8.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            // 留出顶部空间给共享 FreeChat 标题
            Spacer(Modifier.height(36.dp))
            Spacer(Modifier.height(16.dp))

            if (selectMode) {
                DrawerSelectionBar(
                    count = selectedConversations.size,
                    allPinned = allSelectedPinned,
                    hazeState = drawerHazeState,
                    isDark = isDark,
                    onExit = { exitSelectMode() },
                    onTogglePin = {
                        onBatchPinConversations(selectedConvIds, !allSelectedPinned)
                        exitSelectMode()
                    },
                    onDelete = { onAskBatchDelete(selectedConversations) },
                    modifier = Modifier.padding(start = HeaderCardInset - 8.dp, end = HeaderCardInset)
                )
                Spacer(Modifier.height(12.dp))
            } else {
                // 这一支的 Column 自己已经 pad 了 start = 8dp，这里补到 HeaderCardInset，落点与高级材质一致
                SearchBox(searchQuery, onSearchQueryChange, drawerHazeState, isDark, Modifier.padding(start = HeaderCardInset - 8.dp, end = HeaderCardInset))
                Spacer(Modifier.height(10.dp))
                NewChatButton(onNewChat, onNewChatRect, drawerHazeState, isDark, Modifier.padding(start = HeaderCardInset - 8.dp, end = HeaderCardInset))
                Spacer(Modifier.height(12.dp))
            }

            HorizontalDivider(color = colors.Divider, modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(8.dp))

            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    if (searchQuery.isNotBlank()) {
                        searchResultItems(searchResults, s, onSelectSearchResult)
                    } else {
                        conversationItems(
                            conversations, groups, currentId, isDark,
                            selectMode, selectedConvIds,
                            onSelectConversation, enterSelectMode, toggleSelectConv, openConvMenu,
                            favoriteConvIds, s, drawerHazeState
                        )
                    }
                }

                // 当前对话竖条（同上，两种布局共用一根）
                DrawerIndicatorBar(
                    colors = colors,
                    listState = listState,
                    currentId = if (searchQuery.isBlank()) currentId else null,
                    progress = indicatorProgress,
                    delta = indicatorDelta,
                    drawnY = indicatorDrawnY,
                    viewport = indicatorViewport,
                    installedId = indicatorInstalledId,
                    startY = { indicatorStartY() },
                    startPadPx = indicatorStartPadPx,
                    heightPx = indicatorHeightPx
                ) { indicatorViewport.floatValue = it }
            }

            HorizontalDivider(color = colors.Divider, modifier = Modifier.padding(horizontal = 16.dp))
            SettingsFavoritesRowFull(onOpenAccount, onOpenSettings, onOpenFavorites, onSettingsRowPositioned, onFavoritesRowPositioned)
        }
    }

    // ===== 对话操作菜单（窗口内 overlay + scrim + 真磨砂玻璃，定位在「···」按钮那行旁，缩放淡入） =====
    DrawerConvMenu(
        conversation = menuConv,
        itemY = menuY,
        itemWidth = menuRowWidthPx,
        visible = showConvMenu,
        isDark = isDark,
        advancedMaterial = advancedMaterial,
        hazeState = drawerHazeState,
        onDismiss = { showConvMenu = false },
        onRename = { conv -> showConvMenu = false; onAskRenameConversation(conv) },
        onPin = { conv -> showConvMenu = false; onPinConversation(conv) },
        onDelete = { conv -> showConvMenu = false; onAskDeleteConversation(conv) }
    )

    // 原来这里还有三个 AlertDialog（删除 / 批量删除 / 重命名）。它们不是消失了，是搬到
    // MainActivity 的根 Box 去了 —— 换成窗口内的底部磨砂哑光玻璃弹层之后，弹层必须铺满整屏，
    // 而这一层只有 90% 屏宽，装不下（详见函数签名里那三个 onAskXxx 的注释）。
}

/**
 * 当前对话竖条（全列表唯一一根，画在列表上层）。
 *
 * 纵向位置在**布局阶段**从 [androidx.compose.foundation.lazy.LazyListState.layoutInfo] 现算
 * （`offset {}` 的 lambda）：`itemInfo.offset` 是视口坐标，随滚动自动更新 ——
 * 读进 composition 的话滚一帧重组一次，这里不需要任何重组，行动了下一帧就贴上去了。
 *
 * 为什么不再用行的根坐标上报：`positionInRoot` 含祖先 layer 的平移，抽屉滑走时每帧都在变，
 * 竖条会被拽着乱窜（1.0.50 的逐帧实测轨迹见 DrawerContent 里那段说明）。
 *
 * 长度在飞行中段**拉长 30%**：`progress` 是**缓动后**的进度，`sin(π·progress^0.85)` 中段最大，
 * 起步蓄力、中段（速度最快处）拉到最长、落位收回 —— 把非匀速「画」出来。
 * 收放写在绘制块里（不是改 height），免得每帧重组。
 * （旧注释写的「中段收 30%」是上一稿的遗留，现在方向相反：拉长，见下面绘制块里的 `f`。）
 *
 * 外层 [clipToBounds] 是必须的：竖条可能跑到列表可视区之外（当前对话被滚走了），
 * 不裁的话它会浮在头部/底部栏上面。
 */
@Composable
private fun BoxScope.DrawerIndicatorBar(
    colors: com.freechat.ui.theme.FreeChatColors,
    listState: androidx.compose.foundation.lazy.LazyListState,
    currentId: String?,
    progress: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    delta: androidx.compose.runtime.State<Float>,
    drawnY: FloatArray,
    viewport: androidx.compose.runtime.State<Float>,
    installedId: Array<String?>,
    startY: () -> Float,
    startPadPx: Float,
    heightPx: Float,
    onMeasured: (Float) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .onGloballyPositioned { coords -> onMeasured(coords.size.height.toFloat()) }
    ) {
        // 搜索态没有「当前对话行」可言（结果按消息列），currentId 传 null，整根不画
        if (currentId != null) {
            Box(
                modifier = Modifier
                    .offset {
                        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == currentId }
                        // 坐标系见上面 indicatorYOf：itemInfo.offset 是内容坐标系，补上
                        // −viewportStartOffset（= 头部 contentPadding）才是画在面板里的位置
                        val vso = listState.layoutInfo.viewportStartOffset
                        val target = if (info != null) info.offset - vso + info.size / 2f - heightPx / 2f else 0f
                        val y = if (info != null) {
                            when {
                                // 贴着行走：位置每帧现算（滚动/抽屉平移都自动跟上），再加动画的残余偏移
                                installedId[0] == currentId ->
                                    target + delta.value * (1f - progress.value)
                                // ⚠️ 刚换行、动画还没装上（LaunchedEffect 在这帧布局/绘制之后才跑）：
                                // 这一帧 progress 还停在上一次的 1、delta 是 0，照上面那条算就是
                                // **先闪一下目标行**再滑回起点重来 —— 看着像「瞬移＋倒放」。
                                // 直接按起点画，与动画第 0 帧严丝合缝（真机常常同帧内追平，慢机器 1~2 帧）。
                                installedId[0] != null -> startY()
                                // 首次出现（从来没装过动画）：直接到位，不从边缘飞进来
                                else -> target
                            }
                        } else {
                            // 目标行滚出可视区 → 停在视口**外**（上边出去的停上边，下边出去的停下边）。
                            // 不能拿「最后记下的位置」：快速滑动时那一行可能一帧就飞出视口，
                            // 竖条会留在列表正中间 —— 一根没有归属的竖条比不画还糟。
                            // 停在视口外则被外层 clipToBounds 裁掉；行一回来它又是贴着行走的。
                            // 判上下的依据 = 上一帧画在哪（上一帧它就在视口外，正好说明是从哪边出去的）。
                            // ⚠️ 这一支不叠加 delta：行不在可视区时 delta 是「从很远的地方滑过来」的量，
                            // 叠上去会把竖条甩回屏幕中间。
                            if (drawnY[0] < viewport.value / 2f) -heightPx else viewport.value
                        }
                        // 记下这一帧真正画在哪儿：切换动画的起点、以及上面判上下的依据都取它。
                        // 纯数组写、不是 Compose 状态，绘制期写不引发重组/重绘。
                        drawnY[0] = y
                        IntOffset(startPadPx.roundToInt(), y.roundToInt())
                    }
                    .width(3.dp)
                    .height(30.dp)
                    .drawBehind {
                        // 长度：**先拉长再缩小** —— 起步蓄力、中段（速度最快处）拉到最长，
                        // 落位时收回原长。峰值取在 44% 处（sin(π·p^0.85)），比速度峰值早一点点，
                        // 看着像「先探出去、再跟上来」，那口气就是「灵动」的来源。
                        // ⚠️ 拉长会画到节点外面（30dp 的 Box 里画 ~39dp）—— drawBehind 默认
                        // **不裁**到节点边界，靠的就是这个；别在这条链上加 clip。
                        val f = 1f + 0.30f *
                            kotlin.math.sin(kotlin.math.PI.toFloat() * progress.value.pow(0.85f))
                        val h = size.height * f
                        drawRoundRect(
                            color = colors.Primary,
                            topLeft = Offset(0f, (size.height - h) / 2f),
                            size = androidx.compose.ui.geometry.Size(size.width, h),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx())
                        )
                    }
            )
        }
    }
}

/** 对话列表（含空态），供两种布局复用 */
private fun androidx.compose.foundation.lazy.LazyListScope.conversationItems(
    conversations: List<Conversation>,
    groups: List<ConvGroup>?,
    currentId: String?,
    isDark: Boolean,
    selectMode: Boolean,
    selectedConvIds: Set<String>,
    onSelectConversation: (Conversation) -> Unit,
    onEnterSelectMode: (Conversation) -> Unit,
    onToggleSelect: (Conversation) -> Unit,
    onOpenMenu: (Conversation, Float, Float) -> Unit,
    favoriteConvIds: Set<String>,
    s: com.freechat.i18n.AppStrings,
    hazeState: HazeState
) {
    if (conversations.isEmpty()) {
        item(key = "empty") {
            val colors = LocalFreeChatColors.current
            Box(
                modifier = Modifier
                    .fillParentMaxWidth()
                    .fillParentMaxHeight(0.7f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.AutoMirrored.Filled.List, null, tint = colors.TextTertiary, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        s.noConversations,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.TextTertiary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    } else if (groups != null) {
        groups.forEach { group ->
            item(key = "grp_${group.key}") { GroupHeader(group.label) }
            items(group.items, key = { it.id }) { conv ->
                ConversationItem(
                    conversation = conv,
                    isActive = conv.id == currentId,
                    isDark = isDark,
                    hazeState = hazeState,
                    selectMode = selectMode,
                    isSelected = conv.id in selectedConvIds,
                    // 多选态：整行点击 = 勾选，不进对话（长按同样按勾选处理）
                    onClick = { if (selectMode) onToggleSelect(conv) else onSelectConversation(conv) },
                    onLongPress = { if (selectMode) onToggleSelect(conv) else onEnterSelectMode(conv) },
                    onOpenMenu = onOpenMenu,
                    hasFavorites = favoriteConvIds.contains(conv.id)
                )
            }
        }
    }
    item { Spacer(Modifier.height(8.dp)) }
}

/** 搜索结果列表（消息级）：对话名 + 关键词预览 + 时间，无头像无删除键 */
private fun androidx.compose.foundation.lazy.LazyListScope.searchResultItems(
    results: List<SearchResultItem>,
    s: com.freechat.i18n.AppStrings,
    onSelect: (Conversation, String) -> Unit
) {
    if (results.isEmpty()) {
        item(key = "search_empty") {
            val colors = LocalFreeChatColors.current
            Box(
                modifier = Modifier
                    .fillParentMaxWidth()
                    .fillParentMaxHeight(0.7f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    s.noSearchResults,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.TextTertiary,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        items(results, key = { "${it.conversation.id}_${it.messageId}" }) { item ->
            SearchResultRow(item = item, onClick = { onSelect(item.conversation, item.messageId) })
        }
    }
    item { Spacer(Modifier.height(8.dp)) }
}

/** 单条搜索结果卡片：三行（对话名 / 预览[关键词标红] / 时间），点击跳转到该消息 */
@Composable
private fun SearchResultRow(item: SearchResultItem, onClick: () -> Unit) {
    val colors = LocalFreeChatColors.current
    val s = LocalStrings.current
    val annotated = buildAnnotatedString {
        append(item.preview)
        if (item.matchStart in 0 until item.preview.length && item.matchEnd in item.matchStart..item.preview.length) {
            addStyle(SpanStyle(color = colors.ErrorRed, fontWeight = FontWeight.SemiBold), item.matchStart, item.matchEnd)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            com.freechat.i18n.localizedConvTitle(item.conversation, s),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = colors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(3.dp))
        Text(
            annotated,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 13.sp),
            color = colors.TextTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(
            formatLastMessageTime(item.timestamp),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = colors.TextTertiary.copy(alpha = 0.75f)
        )
    }
}

/**
 * 侧滑页头部两张卡（搜索 / 新对话）的高度。多选态的 [DrawerSelectionBar] 与它同高 ——
 * 列表的 `contentPadding.top`、顶部模糊带高度都由 `headerTotalDp` 推导，改这里必须同步。
 */
private val HeaderCardHeight = 40.dp

/**
 * 头部卡片的左右留白。
 *
 * 为什么是 22dp 而不是跟标题一样的 16dp：标题用的是 Aurora 那套展示字体，
 * 「F」的**左侧边距**（字形留白）在 27sp 下有 4~6dp，落笔点其实在 20dp 出头。
 * 卡片按 16dp 摆，看上去就比标题探出去一截、左右也太满 —— 22dp 之后两者视觉上才齐。
 */
private val HeaderCardInset = 22.dp

/** 紧凑搜索框：高度与新对话按钮一致（[HeaderCardHeight]），磨砂玻璃 + 阴影，BasicTextField 无默认高 */
@Composable
private fun SearchBox(
    query: String,
    onQueryChange: (String) -> Unit,
    hazeState: HazeState,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = LocalFreeChatColors.current
    val s = LocalStrings.current
    val advancedMaterial = LocalAdvancedMaterial.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(HeaderCardHeight)
            .then(
                if (advancedMaterial) Modifier.frostedGlass(hazeState, isDark, RoundedCornerShape(14.dp), elevation = 6.dp)
                else Modifier.frostedCard(null, colors, false, RoundedCornerShape(14.dp), fallback = colors.SurfaceVariant)
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Search, s.searchAction, tint = colors.TextTertiary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(s.searchHistory, style = MaterialTheme.typography.bodySmall, color = colors.TextTertiary)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.TextPrimary),
                    cursorBrush = SolidColor(colors.Primary),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Filled.Close, s.clearAction, tint = colors.TextTertiary, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/** 新建对话按钮：圆角卡，[HeaderCardHeight] 高，磨砂玻璃 + 阴影，与搜索框一致 */
@Composable
private fun NewChatButton(
    onNewChat: () -> Unit,
    onNewChatRect: ((Rect) -> Unit)?,
    hazeState: HazeState,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = LocalFreeChatColors.current
    val s = LocalStrings.current
    val advancedMaterial = LocalAdvancedMaterial.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(HeaderCardHeight)
            .then(
                if (advancedMaterial) Modifier.frostedGlass(hazeState, isDark, RoundedCornerShape(14.dp), elevation = 6.dp)
                else Modifier.frostedCard(null, colors, false, RoundedCornerShape(14.dp), fallback = colors.AccentMuted)
            )
            .clickable { onNewChat() }
            .onGloballyPositioned { coords ->
                val pos = coords.positionInRoot()
                val size = coords.size
                onNewChatRect?.invoke(Rect(pos.x, pos.y, pos.x + size.width, pos.y + size.height))
            }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Add, s.newChat, tint = colors.Primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                s.newChat,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = colors.Primary
            )
        }
    }
}

/**
 * 抽屉左下角的账号入口 —— **里面只有一个圆头像**。
 *
 * 为什么不跟旁边两个一样做成「图标 + 文字」的胶囊：头像是用户自己挑的图，
 * 它就是内容本身，塞上文字只会把它挤小、还得再裁一次。所以它只有头像，
 * 但**高度和左边距跟旁边的胶囊对齐**，三个按钮在同一条水平线上。
 *
 * 外圈：**一圈白色描边**，只用来把头像从背景里轻轻托出来。
 * 早先这里用的是主题色半透明（暖棕主题下就是一圈棕红），在浅色抽屉上非常扎眼 ——
 * 用户的原话是「外圈是红色的且太明显」，于是改黑改白又改细。
 * 现在回到 1.5dp 的白色（深色主题降到低透明度的白，免得刺眼）：太细（1dp）等于没画，
 * 用户点名要「一开始那种粗度」。宽度与账号页那颗 56dp 头像共用 [AvatarRingWidth]。
 */
@Composable
private fun AccountAvatarButton(
    onClick: () -> Unit,
    hazeState: HazeState,
    onPositioned: ((Rect) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = LocalFreeChatColors.current
    val advancedMaterial = LocalAdvancedMaterial.current
    val s = LocalStrings.current
    val isDark = colors.TextPrimary.luminance() > 0.5f
    // 头像是本地缓存里那份字节，登录后由 AccountManager.refreshAvatar 对账换新
    val bytes by com.freechat.sync.AvatarStore.current.collectAsState()
    val bitmap = remember(bytes) {
        bytes?.let { runCatching { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull() }
    }

    Box(
        modifier = modifier
            .size(AvatarButtonSize)
            .onGloballyPositioned { coords ->
                val pos = coords.positionInRoot()
                val size = coords.size
                onPositioned?.invoke(Rect(pos.x, pos.y, pos.x + size.width, pos.y + size.height))
            }
            .softShadow(
                CircleShape,
                dx = 0.dp,
                dy = 4.dp,
                spread = 11.dp,
                tone = Color.Black,
                maxAlpha = if (isDark) 0.13f else 0.07f
            )
            .clip(CircleShape)
            .then(
                // 衬底：同上（头像圈同样是炫彩下会透出下面内容的磨砂面）
                if (advancedMaterial) Modifier
                    .liquidOpaqueBackground(colors.Background)
                    .hazeEffect(state = hazeState) {
                        blurRadius = 24.dp
                        inputScale = HazeInputScale.None
                        backgroundColor = colors.Surface.copy(alpha = 0.5f)
                        tints = listOf(HazeTint(colors.Surface.copy(alpha = 0.4f)))
                    } else Modifier.background(colors.InputBg)
            )
            .border(AvatarRingWidth, Color.White.copy(alpha = if (isDark) 0.20f else 0.60f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = s.account,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                Icons.Filled.Person,
                s.account,
                tint = colors.TextTertiary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/** 头像按钮的直径。比旁边两个胶囊（约 40dp）略大一圈，作为视觉锚点，三者仍然同一条中线。 */
private val AvatarButtonSize = 44.dp

/**
 * 头像那圈白描边的宽度 —— 抽屉左下角 44dp 与账号页 56dp 两颗头像共用（两处必须一样粗，
 * 否则从抽屉点进账号页会看到同一个头像换了个边框）。1dp 太细，用户要求「回到一开始那种粗度」。
 */
internal val AvatarRingWidth = 1.5.dp

/**
 * 底部账号/设置/收藏行（非高级材质，全宽固定，三个等宽平分）。
 *
 * 账号原来在这儿是一颗 44dp 的**悬浮圆头像**（旁边两个是矮胶囊，它比它们大一圈、还偏外一点）。
 * 那是高级材质那一支的设计语言 —— 那边整页都是沉浸式的文字流，需要一个悬浮的锚点；
 * 而高级材质一关，这一页里根本没有"悬浮层"这回事，孤零零一颗圆头像浮在选项外面就很突兀。
 * 所以这一支里它就是一个**普通选项行**，跟设置、收藏长得一模一样，图标位上放头像。
 */
@Composable
private fun SettingsFavoritesRowFull(
    onOpenAccount: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFavorites: () -> Unit,
    onSettingsRowPositioned: ((Rect) -> Unit)?,
    onFavoritesRowPositioned: ((Rect) -> Unit)?
) {
    val s = LocalStrings.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DrawerBottomRow(
            label = s.account,
            onClick = onOpenAccount,
            onPositioned = null,
            avatar = true
        )
        DrawerBottomRow(
            label = s.settings,
            icon = Icons.Filled.Settings,
            onClick = onOpenSettings,
            onPositioned = onSettingsRowPositioned
        )
        DrawerBottomRow(
            label = s.favorites,
            icon = Icons.Filled.Favorite,
            onClick = onOpenFavorites,
            onPositioned = onFavoritesRowPositioned
        )
    }
}

/**
 * 抽屉底部的一个选项行：图标 + 文字，等宽平分。
 *
 * [avatar] = true 时图标位画用户头像（没设过头像就还是那个默认人像），
 * 与 [icon] 二选一 —— 账号那一行的"图标"就是用户自己的脸。
 */
@Composable
private fun RowScope.DrawerBottomRow(
    label: String,
    onClick: () -> Unit,
    onPositioned: ((Rect) -> Unit)?,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    avatar: Boolean = false
) {
    val colors = LocalFreeChatColors.current
    val bytes by com.freechat.sync.AvatarStore.current.collectAsState()
    val bitmap = remember(bytes, avatar) {
        if (!avatar) null
        else bytes?.let { runCatching { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull() }
    }
    Box(
        modifier = modifier
            .weight(1f)
            .onGloballyPositioned { coords ->
                val pos = coords.positionInRoot()
                val size = coords.size
                onPositioned?.invoke(Rect(pos.x, pos.y, pos.x + size.width, pos.y + size.height))
            }
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (avatar) {
                Box(
                    modifier = Modifier.size(20.dp).clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = label,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(Icons.Filled.Person, label, tint = colors.TextSecondary, modifier = Modifier.size(20.dp))
                    }
                }
            } else if (icon != null) {
                Icon(icon, null, tint = colors.TextSecondary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.TextSecondary,
                maxLines = 1
            )
        }
    }
}

/** 悬浮抽屉按钮（设置/收藏）：左下角小号磨砂玻璃胶囊，与 Chat 页悬浮输入框同款质感 */
@Composable
private fun FloatingDrawerButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    onPositioned: ((Rect) -> Unit)?,
    hazeState: HazeState,
    modifier: Modifier = Modifier
) {
    val colors = LocalFreeChatColors.current
    val advancedMaterial = LocalAdvancedMaterial.current
    val isDark = colors.TextPrimary.luminance() > 0.5f

    Box(
        modifier = modifier
            .onGloballyPositioned { coords ->
                val pos = coords.positionInRoot()
                val size = coords.size
                onPositioned?.invoke(Rect(pos.x, pos.y, pos.x + size.width, pos.y + size.height))
            }
            // 自绘柔光阴影，不用 Modifier.shadow：磨砂玻璃卡片的底下没有内容可采样时，
            // 系统阴影会退化成硬边灰环（收藏页那个菜单就是这么坏的），自绘的不会
            .softShadow(
                RoundedCornerShape(24.dp),
                dx = 0.dp,
                dy = 4.dp,
                spread = 11.dp,
                tone = Color.Black,
                maxAlpha = if (isDark) 0.13f else 0.07f
            )
            .clip(RoundedCornerShape(24.dp))
            .then(
                // 衬底：流动炫彩下源节点是透明的，不垫一层不透明底，磨砂就盖不住下面的行
                // （1.0.50「磨砂玻璃变成半透明 PPT 图层」——设置/收藏这两颗胶囊是重灾区）
                if (advancedMaterial) Modifier
                    .liquidOpaqueBackground(colors.Background)
                    .hazeEffect(state = hazeState) {
                        blurRadius = 24.dp
                        inputScale = HazeInputScale.None
                        backgroundColor = colors.Surface.copy(alpha = 0.5f)
                        tints = listOf(HazeTint(colors.Surface.copy(alpha = 0.4f)))
                    } else Modifier.background(colors.InputBg)
            )
            .then(
                if (advancedMaterial) Modifier.border(1.dp, colors.InputBorder.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, label, tint = colors.TextSecondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.TextSecondary)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationItem(
    conversation: Conversation,
    isActive: Boolean,
    isDark: Boolean,
    hazeState: HazeState,
    selectMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onOpenMenu: (Conversation, Float, Float) -> Unit,
    hasFavorites: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = LocalFreeChatColors.current
    val s = LocalStrings.current

    var itemPositionY by remember { mutableFloatStateOf(0f) }
    var itemWidthPx by remember { mutableFloatStateOf(0f) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coords ->
                    // 只给「···」菜单定位用（菜单要贴在这一行旁边）；竖条的位置不走这里，
                    // 它从 LazyListState.layoutInfo 现算（见 DrawerIndicatorBar）
                    itemPositionY = coords.positionInRoot().y
                    itemWidthPx = coords.size.width.toFloat()
                }
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongPress
                )
                .padding(start = 16.dp, end = 8.dp, top = 11.dp, bottom = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧激活指示条：1.0.49 起**不在这里画**了 —— 全列表只有一根，
            // 由列表上层的 DrawerIndicatorBar 按位置滑过去。这里留同宽同高的占位，
            // 保证头像/文字的左边距和以前一模一样。
            Spacer(Modifier.width(3.dp).height(30.dp))
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(
                        if (isActive) colors.Primary.copy(alpha = 0.18f)
                        else colors.SurfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                val avatar = conversation.characterProfile?.avatarPath
                if (!avatar.isNullOrBlank()) {
                    AsyncImage(
                        model = File(avatar),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        if (conversation.mode == ChatMode.COMPANION) Icons.Filled.Person else Icons.Filled.Chat,
                        contentDescription = null,
                        tint = if (isActive) colors.Primary else colors.TextTertiary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (conversation.isPinned) {
                        Icon(
                            Icons.Filled.PushPin,
                            null,
                            tint = colors.Primary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                    }
                    Text(
                        com.freechat.i18n.localizedConvTitle(conversation, s),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                        ),
                        color = if (isActive) colors.Primary else colors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    formatLastMessageTime(conversation.updatedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.TextTertiary,
                    maxLines = 1
                )
            }

            if (selectMode) {
                // 多选态：右侧换成勾选圈（自绘，跟整套 UI 的手绘风格一致，也不依赖扩展图标集）
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) colors.Primary else Color.Transparent)
                        .border(
                            width = 1.5.dp,
                            color = if (isSelected) colors.Primary else colors.TextTertiary.copy(alpha = 0.6f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = colors.Background,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            } else {
                // 「···」菜单键：重命名 / 置顶 / 删除都在里面（原来是「删除」单键，长按唤菜单）
                IconButton(
                    onClick = { onOpenMenu(conversation, itemPositionY, itemWidthPx) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = s.moreActions,
                        tint = colors.TextTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * 侧滑页多选操作栏（44dp，与搜索框同规格）：取消 / 已选择 N 项 / 置顶（或取消置顶）/ 删除。
 * 选中的对话已全部置顶时，置顶键翻成「取消置顶」——否则点一下没反应，用户会以为坏了。
 */
@Composable
private fun DrawerSelectionBar(
    count: Int,
    allPinned: Boolean,
    hazeState: HazeState,
    isDark: Boolean,
    onExit: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalFreeChatColors.current
    val s = LocalStrings.current
    val advancedMaterial = LocalAdvancedMaterial.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(HeaderCardHeight)
            .then(
                if (advancedMaterial) Modifier.frostedGlass(hazeState, isDark, RoundedCornerShape(14.dp), elevation = 6.dp)
                else Modifier.frostedCard(null, colors, false, RoundedCornerShape(14.dp), fallback = colors.SurfaceVariant)
            )
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onExit, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.Close, contentDescription = s.cancel, tint = colors.TextSecondary, modifier = Modifier.size(18.dp))
        }
        Text(
            s.selectedCount(count),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onTogglePin, modifier = Modifier.size(32.dp)) {
            Icon(
                if (allPinned) Icons.Outlined.PushPin else Icons.Filled.PushPin,
                contentDescription = if (allPinned) s.unpinConversation else s.pinConversation,
                tint = colors.TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.DeleteOutline, contentDescription = s.delete, tint = colors.ErrorRed, modifier = Modifier.size(18.dp))
        }
    }
}

/** 侧滑页对话操作菜单（「···」键唤出）：窗口内 overlay + scrim + 真磨砂玻璃（高级材质），定位在该行旁，缩放淡入淡出 */
@Composable
private fun DrawerConvMenu(
    conversation: Conversation?,
    itemY: Float,
    itemWidth: Float,
    visible: Boolean,
    isDark: Boolean,
    advancedMaterial: Boolean,
    hazeState: HazeState,
    onDismiss: () -> Unit,
    onRename: (Conversation) -> Unit,
    onPin: (Conversation) -> Unit,
    onDelete: (Conversation) -> Unit
) {
    val colors = LocalFreeChatColors.current
    val s = LocalStrings.current
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val screenHeightPx = with(density) { config.screenHeightDp.dp.toPx() }
    // 菜单预估高度（3 项 + 2 分割线 + 上下 padding），贴底时向上收
    val menuHeightPx = with(density) { 168.dp.toPx() }
    val topSafePx = with(density) { 80.dp.toPx() }
    val menuWidthPx = with(density) { 180.dp.toPx() }
    // 右边缘对齐到「···」键那侧（= 行右边缘，见 ConversationItem 里的说明）。
    // 入口以前是「长按整行」，菜单固定贴左边缘还说得过去；现在入口在行尾，
    // 再贴左边就会出现「点右边、菜单从最左边冒出来」并盖住头像。
    val menuX = (itemWidth - menuWidthPx).roundToInt().coerceAtLeast(with(density) { 8.dp.toPx() }.roundToInt())
    val clampedY = itemY.coerceIn(topSafePx, (screenHeightPx - menuHeightPx - topSafePx).coerceAtLeast(topSafePx)).roundToInt()

    // 拦截层：点击外部关闭（淡入淡出）
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(120)),
        exit = fadeOut(tween(120))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() }
        )
    }

    // 菜单本体：缩放 + 淡入淡出。
    // 偏移必须加在 AnimatedVisibility 自己身上、不能加在它的内容上：缩放枢轴是按节点自身的
    // 布局位置算的，加在内容上的话枢轴会留在抽屉左上角，菜单会带着一段横移「飞」进来。
    // 放在节点上，枢轴 (1f, 0f) 就正好是菜单显示的右上角，即「···」键旁边。
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.offset { IntOffset(menuX, clampedY) },
        enter = FreeChatAnimation.menuEnter(TransformOrigin(1f, 0f)),
        exit = FreeChatAnimation.menuExit(TransformOrigin(1f, 0f))
    ) {
        conversation?.let { conv ->
            Box(
                modifier = Modifier
                    .width(180.dp)
                    .then(
                        if (advancedMaterial) Modifier.frostedGlass(hazeState, isDark, RoundedCornerShape(16.dp), elevation = 8.dp)
                        else Modifier.background(colors.Surface, RoundedCornerShape(16.dp))
                    )
            ) {
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRename(conv) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.DriveFileRenameOutline, null, tint = colors.TextPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(s.renameConversation, style = MaterialTheme.typography.bodyMedium, color = colors.TextPrimary)
                    }
                    HorizontalDivider(color = colors.Divider.copy(alpha = 0.4f), modifier = Modifier.padding(horizontal = 12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPin(conv) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (conv.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            null,
                            tint = colors.TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (conv.isPinned) s.unpinConversation else s.pinConversation,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.TextPrimary
                        )
                    }
                    HorizontalDivider(color = colors.Divider.copy(alpha = 0.4f), modifier = Modifier.padding(horizontal = 12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDelete(conv) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.DeleteOutline, null, tint = colors.ErrorRed, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(s.deleteConversation, style = MaterialTheme.typography.bodyMedium, color = colors.ErrorRed)
                    }
                }
            }
        }
    }
}

/** 最后对话时间格式化：xxxx/xx/xx  xx:xx（年/月/日 时:分） */
private fun formatLastMessageTime(timestamp: Long): String {
    val c = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
    return String.format(
        java.util.Locale.US,
        // 冒号用**半角**：全角「：」在英文界面的数字里看着像串了行的中文标点。
        // 纯数字时间本来就该用半角，中文语境里也一样。
        "%04d/%02d/%02d  %02d:%02d",
        c.get(java.util.Calendar.YEAR),
        c.get(java.util.Calendar.MONTH) + 1,
        c.get(java.util.Calendar.DAY_OF_MONTH),
        c.get(java.util.Calendar.HOUR_OF_DAY),
        c.get(java.util.Calendar.MINUTE)
    )
}

/** 对话分组：置顶 / 今天 / 昨天 / 7天内 / 30天内 / 更早之前 */
private data class ConvGroup(val key: String, val label: String, val items: List<Conversation>)

@Composable
private fun groupConversations(convs: List<Conversation>): List<ConvGroup> {
    val s = LocalStrings.current
    val dayMs = 24L * 3600 * 1000L
    val startOfToday = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
    val startOfYesterday = startOfToday - dayMs
    val startOf7Days = startOfToday - 7 * dayMs
    val startOf30Days = startOfToday - 30 * dayMs

    fun category(t: Long): String = when {
        t >= startOfToday -> "today"
        t >= startOfYesterday -> "yesterday"
        t >= startOf7Days -> "week"
        t >= startOf30Days -> "month"
        else -> "earlier"
    }

    val pinned = convs.filter { it.isPinned }.sortedByDescending { it.updatedAt }
    val unpinned = convs.filter { !it.isPinned }.sortedByDescending { it.updatedAt }

    val groups = mutableListOf<ConvGroup>()
    if (pinned.isNotEmpty()) groups.add(ConvGroup("pinned", s.groupPinned, pinned))
    listOf(
        "today" to s.groupToday,
        "yesterday" to s.groupYesterday,
        "week" to s.groupWithin7Days,
        "month" to s.groupWithin30Days,
        "earlier" to s.groupEarlier
    ).forEach { (key, label) ->
        val items = unpinned.filter { category(it.updatedAt) == key }
        if (items.isNotEmpty()) groups.add(ConvGroup(key, label, items))
    }
    return groups
}

/** 分组标题 — 别样效果字体，与对话项区分 */
@Composable
private fun GroupHeader(label: String) {
    val colors = LocalFreeChatColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(colors.Primary)
        )
        Spacer(Modifier.width(7.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp
            ),
            color = colors.TextSecondary
        )
    }
}
