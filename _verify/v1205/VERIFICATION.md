# FreeChat Beta 1.2.0.5 验收记录

日期：2026-10-16～17。版本：`254 / Beta 1.2.0.5`。范围：本地 Android 客户端及开发文档。已合入 10 月 17 日补充的推荐页可读性、字号与 UI／应用显示密度联动要求。

## 逐条验收

| 要求 | 实现与验证 | 证据 |
| --- | --- | --- |
| 首次阅读页不再拉成长按钮／大手机 | 平板正文最大 720dp，继续按钮最大 320dp 居中，短窗可滚动；同意勾选和协议入口不变 | `agreement-tablet-dark.png`、`TabletStartup1205ContractTest` |
| 发现新版本弹窗适配 | 卡片最大 520dp，正文可滚，短窗／大字号可整卡滚动；保留原按钮、复选与关闭限制 | `notice-tablet-dark.png`、`notice-large-type-short.png` |
| 推荐页左预览、右介绍 | 正常宽屏：左侧真实演示卡，右侧标题／介绍和限宽居中按钮。竖屏、短窗／大字号：限宽纵排，可滚动且 CTA 可达；横竖切换不重播演示 | `feature-preview-left-complete.png`、`feature-portrait-final.png`、`feature-large-type-short.png`、`feature-large-type-short-scrolled.png` |
| 新 Chat 覆盖旧 Chat，不闪空 | 切换前记录旧正文画面，新正文／阅读位置准备好后从右覆盖；只有一份活跃 Chat，旧像素不接收点击也不触发 API | `chat-cover-final.mp4`、`chat-cover-transition.png`；`TabletChatCoverTest` 复现并修复快速连点竞态 |
| 设置详情去掉重复栏目名／图标 | 仅嵌入详情正文去掉首个重复栏标题；左目录、导航栏、实际子分组和手机设置层级仍保留 | `settings-embedded-no-heading.png`、`TabletSettings1205ContractTest` |
| 悬浮阴影统一 | 高级材质采用现有新拟态卡片的双向柔影；普通材质沿用 frostedCard 的轻柔落影，不再叠加黑色平台影 | `floating-settings-root-final.png`、`floating-display-final.png`、`TabletFloating1205ContractTest` |
| 只给 Chat 留全屏按钮 | 实际 Chat 显式开启该入口，侧栏、设置、收藏、模型与角色创建等面板无此按钮 | 平板实际 UI 检查；`TabletChat1205ContractTest.onlyActualChatCanRequestAFullscreenAffordance` |
| 全屏／辅助栏不重排正文 | 固定 Chat 测量宽度，仅移动位置；全屏居中；新规则／模拟设置栏与侧栏等宽 | 下方尺寸表、三份 XML／截图和 `TabletNavigationPolicy.stableChatFrame` 几何测试 |
| 设置／收藏返回 Chat，不回账号 | 移除账号作为工作台返回目标，根返回统一 Chat；子页返回仍保留层级 | `TabletFloating1205ContractTest`、`TabletNavigation1204ContractTest`；Account → Settings → Back 实测回 Chat |
| 切换 Chat 输入框不动 | 正文覆盖不包含输入框外壳；输入框保留显示／隐藏，仅文字草稿淡滑变换，IME composing 状态按对话清理 | `draft-a.png`、`draft-b.png`、`chat-cover-transition.png` |
| 隐藏输入框恢复长草稿不抢焦点 | 平板恢复草稿不当作用户输入，不自动展开紧凑框；仅主动编辑／粘贴才触发原自动展开 | `composer-hidden-before.png`、`composer-hidden-long-draft.png`，切换后系统 `mInputShown=false`；回归契约 |
| 悬浮 X 整窗关闭，根无返回 | 一份窗口级 X 始终在右上角，一级去返回、二／三级保留；所有层级可整窗关闭 | `floating-settings-root-final.png`、`floating-model-nested.png`、`FloatingWindowState` 测试 |
| 未保存更改仍确认 | 模型与角色 X 均进入原确认；继续编辑保留输入，放弃更改关闭整个窗口而非只退一级 | `model-dirty-close-final.png`、`character-dirty-close.png`，两条流程均已实际点击验证 |
| 底层切换不让表单串对话 | 模拟设置／新规则绑定打开时的目标；确认回调使用原点击 ID／档案快照；对话模式锁定也看表单所属目标 | `character-base-chat-anchored.png`：编辑林间书房时底层切到晚风，名称草稿仍属于林间书房；放弃后整窗关闭 |
| 页面显示逻辑选项归位 | 一条“页面显示逻辑”进入同款 SheetPanel（悬浮／嵌入），不再外层铺三选一按钮 | `display-choice.png`、`TabletSettings1205ContractTest` |
| 子设置统一展开 | 悬浮时同卡片依次展开“悬浮位置”和“底层触控”；位置用统一三选项弹层；收起保留设置值 | `floating-display-final.png`、`floating-position-choice.png`；偏好持久化／收起保留测试 |
| 底层触控默认开启 | 新偏好本地持久化、默认 true，不加入跨设备设置同步；开启可切底层 Chat，关闭时背景不响应；学习始终独占 | `floating-base-switch.png`、`floating-base-blocked.png`、设置与窗口策略测试 |
| 手机不受平板逻辑影响 | 手机竖／横屏无平板目录、浮窗或全屏入口；设置仍按手机层级进入 | `phone-portrait-home.png`、`phone-settings.png`、`phone-landscape.png`，按 UI 层级断言验证 |
| 推荐页文字增大、保持舒适层级 | 平板标题 28sp、介绍 18sp／30sp 行高、操作 18sp；真实预览正文 16sp／26sp，次要状态 14sp。手机适度增大标题／正文，不另做图片化界面；正常横屏的大字号档仍保持左预览／右介绍 | `feature-readable-medium.png`、`feature-readable-large-final.png`；`DisplayScale1205Test` |
| 字号与整套 UI 协调缩放 | 保留五档偏好，文字、图标、间距、圆角、卡片和可用 dp 空间同倍率变化；不改系统 DPI、不改变真实平板身份，不重复放大文字 | `scale-live-medium-final.png`、`scale-live-small-final.png`、`scale-live-xxlarge-return-final.png`；转换几何测试 |
| 不重启也能正确重算布局 | 连续特大→标准→小→特大，面板总宽始终为 1920px；保留草稿，没有按字号销毁重建页面 | `scale-live-draft-preserved.png`；旧像素 constraints 换新 density 的回归测试 |
| 大字号短／窄窗口不挤坏控件 | 推荐演示播完可内滚、短窗可外滚；窄侧栏复用图标底栏；头像以实际剩余区域形成正方形，提示和按钮先测量 | `feature-readable-xxlarge-footer.png`、`scale-english-large-phone-drawer-final.png`、`scale-xxlarge-phone-drawer-final.png`、`scale-crop-phone-landscape-xxlarge.png` |
| 原生弹窗／图片预览也随档位缩放 | Dialog、Popup 及两处原生 Material 确认窗口继承同一应用密度，保留原关闭与确认回调 | `scale-native-gallery-xxlarge.png`；原生根作用域回归契约 |
| 特大字号完整输入框不盖住语音键 | 按当前字体实际标签宽度判断；空间不足时复用带无障碍名称的图标快捷键，普通字号仍保留完整标签。长问候语仅在会溢出时适配宽度 | `scale-complete-medium-phone-final.png`、`scale-complete-xxlarge-phone-final.png`、`scale-greeting-fitted-xxlarge-phone-final.png` |
| 微信表情网格不裁掉右侧列 | 当前可用宽度决定 1～8 列，固定原单元尺寸，限高后内部滚动；微信以外的可用性规则不变，点选仅进入草稿 | `scale-wechat-xxlarge-emoji-open-final.png`、`scale-wechat-xxlarge-emoji-tail-final.png`、`scale-wechat-xxlarge-emoji-draft-final.png` |

### 正文尺寸实测

隔离模拟器 1920×1200px / 240dpi，窗口约 1280×800dp。选择同一段最后可见正文：

> 我们可以先从最有共鸣的一段开始，再慢慢梳理它和日常生活的联系。

| 状态 | 文字 bounds（px） | 宽×高 |
| --- | --- | --- |
| 默认分屏 | `[652,925][1757,953]` | 1105×28 |
| 全屏居中 | `[396,925][1501,953]` | 1105×28 |
| 进入右侧新规则 | `[140,925][1245,953]` | 1105×28 |

只有横坐标改变，字号、行宽、高度与纵向位置不变。原始层级：`chat-split.xml`、`chat-fullscreen.xml`、`chat-rules.xml`；截图：`chat-split-before.png`、`chat-fullscreen.png`、`chat-rules-width.png`。

### 关键交互复现

1. **模型窗口**：悬浮设置 → 语言模型 → 添加模型 → 名称输入 `QA-model` → X → 继续编辑，名称仍在；再 X → 放弃更改，整个窗口关闭回 Chat。没有保存假模型或发送 API 请求。
2. **角色窗口**：林间书房 → 模拟设置 → 确认编辑 → 修改名称 → X 提示；继续编辑后点击底层晚风，角色草稿仍为林间书房；X → 放弃更改，整个窗口关闭，未提交草稿。
3. **底层触控**：悬浮设置保持打开时选择底层晚风，Chat 正常改变。关闭底层触控后，再点击已记录的侧栏对话坐标，Chat 不变；重新打开仍保持原选中对话。不存在额外背景变暗／模糊层。
4. **最后一次选择优先**：用 Deferred 挂起 B 的旧画面捕获，马上重新选 A，再释放捕获。修复前发生 `A,B` 反向覆盖；修复后只有 `A`，由单元测试钉住。
5. **动画边界**：`chat-cover-transition.png` 由录屏 4.5 秒处开始取帧，新页覆盖时旧 Chat 保留、输入框外壳位置不变。录制人为设动画时间倍率 3 便于观察，完成后恢复 1；不将该录像当成性能基准。

### 字号联动的根因与边界复测（2026-10-17）

- 全局选择五档字号时，Compose density 乘以原有档位倍率，fontScale 保持 1；复制应用内 Configuration，宽高 dp 同步换算。设备本身的 `wm density` 在连续切换前后都保持 override 240，未写系统设置。旧的额外文字几何补偿使用残余倍率 1，预览滑杆也除去当前档位，避免二次放大。
- 连续切换实测曾发现：从特大切到标准后，旧 914dp 的窗口乘新 density 1.5，只铺出约 1371px，右侧和底部错误留白；反向则会裁切。原因在于像素 constraints 未变时，部分 `BoxWithConstraints` dp 换算仍沿用旧作用域。现由一个小型 `AppBoxWithConstraints` 用**当前 LocalDensity＋原始像素 constraints**重新计算四个 dp 属性，现有九个文件／十三处作用域共用它，不用字号 key 重建页面。保留原始 constraints、BoxScope 修饰符、对齐和最小约束语义，无界仍为 Infinity。参考 [AndroidX 原实现](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/foundation/foundation-layout/src/commonMain/kotlin/androidx/compose/foundation/layout/BoxWithConstraints.kt)。
- 修复后，同一进程连续特大→标准→小→特大：字体页标题右边界一直为 1920px，分隔线按有效空间合理变化，卡片不裁切／不留假空白。返回 Chat 后 `QA-scale-draft` 仍在。证据：`scale-live-xxlarge-final.png`、`scale-live-medium-final.png`、`scale-live-small-final.png`、`scale-live-xxlarge-return-final.png`、`scale-live-draft-preserved.png`。
- 手机 392 system-dp、English、大字号档 112% 的侧栏约 315 app-dp：设置／收藏使用原有图标样式，不再挤成竖排或裁掉英文。默认宽手机和 340dp 宽平板仍可显示完整标签；图标保留无障碍名称及原点击回调。中文 140% 也检查通过。
- 头像裁剪先由 Column 测量提示／操作行，正方形仅使用剩余预览区；手势与保存使用 `onSizeChanged` 得到的同一实际像素边长。2400×1080 / 440dpi、140% 档下圆框像素外接框实测 `[1023,235][1377,589]`，为 **355×355px**，保存为 **512×512px**。图片是离线几何测试图，不是用户照片；成品为 `scale-crop-saved.jpg`。数值回归另外覆盖短窄 320×360 system-dp、两行提示，不再依赖固定 144dp 预留。
- 完整输入框原文字标签在 1080×2400 / 440dpi、140% 档会伸入语音圆钮。现在按实际字体与标签测量，空间不足才使用同款图标；标准档保留文字，微信档表情入口实际点击可用。原先被裁的八字问候语“世界好像只属于你”现完整显示，UI bounds 为 `[231,1349][972,1479]`，未越过 1080px 屏幕。保留原键盘合并动画。
- 同一实际检查还复现表情固定八列裁掉最右两项的问题；新增回归先失败后通过。最终该窗口自适应七列，最右完整表情 bounds 为 `[865,1346][970,1438]`；面板限高、可滚至“🌙”，点击只写草稿，不发送。未更改 emoji 列表或开放给叙事模式。

## 自动化与构建

使用 JBR 17.0.14，命令：

```powershell
$env:JAVA_HOME='C:/Users/M/.jdks/jbr-17.0.14'
$env:PATH="$env:JAVA_HOME/bin;$env:PATH"
./gradlew.bat '-Dorg.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1024m' `
  :app:testDebugUnitTest :freechat-core:test :app:assembleDebug :app:assembleRelease --console=plain
```

- 客户端 JUnit：724 项，0 失败／0 错误，3 项既有跳过（721 通过）。
- 共享核心 JUnit：46 项，0 失败／0 错误／0 跳过。
- 本轮重点套件：Chat 5、Floating 5、Settings 4、Startup 6、Capture race 1、DisplayScale 11，及已有布局／偏好／导航／角色保存回归。
- 已先运行对应失败测试再实现修复；推荐页左右顺序补充要求同样先见红后见绿。
- Debug 与 Release 构建通过，Release vital lint 通过。
- `git diff --check` 通过；Git 仅提示原有混合换行将来规范化的警告。
- 分阶段只读规格／代码评审通过，最后三项缩放边界由主代理复核并运行验收；另修复评审和实测中发现的表单目标漂移、长草稿抢焦点、异步捕获覆盖最新点击、英文窄底栏、横屏取景与保存不一致、实时字号旧 dp 缓存、完整输入框按钮重叠、长问候语和表情列裁切问题。新增边界均先观察失败再修复，未通过删掉断言掩盖。

### 完整 Debug lint 的既有问题

本轮额外运行了 `:app:lintDebug`，它不是全绿：**7 errors / 57 warnings**。已对照当前 Git HEAD 原始代码，以下 7 处均在本轮前存在，未通过关闭检查／添加基线掩盖：

- `FeatureRecPage.kt` 原演示使用 drawable 资源的 `openRawResource`，ResourceType。
- `Frosted.kt`、`SiriOrb.kt`、`VoiceCancelRing.kt` 原 AGSL draw 的 API 33 标注，3 处 NewApi。
- 既有 Debug `ChatPresentationQaActivity.kt` 两处在 Compose 中读取 StateFlow.value。
- `SettingsScreen.kt` 既有我的分享标题 AnimatedContent 的 `_` 目标状态参数未使用。

实际完整报告：`app/build/intermediates/lint_intermediate_text_report/debug/lintReportDebug/lint-results-debug.txt`。不要误用 `app/build/reports/` 内旧的零错误报告。Release vital 检查和构建成功不等于完整 Debug lint 零问题。

## 安装包

- `versionCode=254`，`versionName=Beta 1.2.0.5`，`applicationId=com.freechat`。
- Release APK 使用原有签名配置，v2 验证通过，1 位签名者。
- 解包核对最终 AndroidManifest：无 `com.freechat.qa` 测试 Activity／Receiver。
- 交付：`D:/Desktop/FreeChat/FreeChat_Beta_1.2.0.5.apk`，35,542,451 字节。
- SHA-256：`F23EBBAAD7A981DD66C15D4D4C1A02B9007BC2E7F4B2871C7F4DDB310D5A5917`。复制后与构建目录内已验证的 Release APK 哈希一致。

## 测试范围和限制

- 仅使用本任务启动的隐藏模拟器 `emulator-5560` 与隔离 userdata。平板检查覆盖 1920×1200、1200×1920、1920×1050 / 240dpi；大字号使用应用自己的 XXLARGE（不是会被主题覆盖的系统字号）。手机 1080×2400、2400×1080 / 440dpi。
- 深浅色、高级材质、底层触控开关、普通与大字号均有实际画面检查。本轮使用现有主题／卡片／非线性动效，不加入新主题、图片化 UI 或第三方依赖。
- 模拟器软件 GPU 只能检查排版、触控和视觉衔接；没有真实平板，不能据此承诺 60／120fps 或各厂商键盘表现。
- 模型使用本地离线假配置，没有测试付费模型服务、麦克风收音或真实云账号同步；这些链路未因本轮验收而触发调用。
- 未改服务器／数据库，未部署、提交或 Push，未删除既有用户文件／未提交修改；应用内正式版更新日志保持不变。
- `feature-landscape*.png`、`model-unsaved-confirm.png`、`scale-xxlarge-font-final.png`、`scale-live-medium-from-xxlarge.png` 等早期探索／故障截图不是最终验收画面；字号最终结果以 `scale-live-*-final.png` 和上方明确列出的证据为准。早期名字含 final 也不代表覆盖了后续补充要求。
