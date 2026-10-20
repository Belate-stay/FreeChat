# FreeChat Beta 1.2.0.3 验收记录

日期：2026-10-16。交付 versionCode **252** / `Beta 1.2.0.3`，按用户最后确认的版本格式。

## 安装包与构建

- 安装包：`D:/Desktop/FreeChat/FreeChat_Beta_1.2.0.3.apk`
- 文件大小：35,476,915 字节。
- SHA-256：`3753D340360AB8A088A062A9F75ED467C12ACBDE969C8BDC604E2B548038CDA8`
- aapt 确认包名 `com.freechat`，versionCode 252，versionName `Beta 1.2.0.3`，minSdk 26 / targetSdk 34 不变。
- apksigner 验签通过，APK Signature Scheme v2，单签名者；release manifest 不含 TabletSeedQaActivity / TabletBusyQaReceiver。
- 最后一次完整构建：`BUILD SUCCESSFUL in 2m 14s`，98 tasks，15 executed / 83 up-to-date。

JBR 17，串行命令：

```powershell
$env:JAVA_HOME='C:/Users/M/.jdks/jbr-17.0.14'
$env:PATH="$env:JAVA_HOME/bin;$env:PATH"
./gradlew.bat '-Dorg.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1024m' :app:testDebugUnitTest :freechat-core:test :app:assembleDebug :app:assembleRelease --console=plain
```

| 测试模块 | 总数 | 通过 | 跳过 | 失败／错误 |
| --- | ---: | ---: | ---: | ---: |
| app JVM | 671 | 668 | 3 | 0 |
| freechat-core | 46 | 46 | 0 | 0 |

相比上一版增加 23 项：显示偏好 5、连续画布几何 5、角色创建目录 5、设置契约 3、导航／隔离／阴影 5。匹配 Tablet 名称的 46 项全通过，另有原人设保存策略 5 项随全量通过。

先复现、后修复：新几何策略最初缺失；悬浮卡片测量／隐藏 Chat 返回锁 12 项中 2 失败；跨对话改写守卫 14 项中 1 失败；深色阴影 15 项中 1 失败。实现对应修复后最终全量通过。

## 验收环境与范围

仅使用独立 `emulator-5560`（API 37、x86_64、SwiftShader），沿用 `_verify/v1202/emulator/userdata.img`，未清空或使用用户日常 AVD。离线夹具监听本机 18764，只有假 Key，没有登录真实账号、调用真实付费模型或变更线上服务。

| 场景 | 实际尺寸／密度 |
| --- | --- |
| 平板横屏 | 1920×1200 / 240 dpi，1280×800 dp |
| 平板竖屏 | 1200×1920 / 240 dpi，800×1280 dp |
| 平板自由窗口 | Rect(150,100–900,1150)，约 500 dp 宽 |
| 自由窗口扩大 | Rect(100,70–1800,1150)，恢复双栏 |
| 手机竖屏 | 1080×2400 / 440 dpi，约 393 dp 宽 |
| 手机横屏 | 2400×1080 / 440 dpi，短边仍约 393 dp |

## 已实际检查

- 设置顶部增加平板优化分类，但首次详情仍为模型选择。默认嵌入；悬浮下展开两侧／左侧／右侧。重启后偏好保留，切换显示方式时仍能回到优化设置项。
- 悬浮卡片在左右留有间距，竖屏比例、统一圆角和阴影；主页面继续显示，不加全屏暗色或模糊。点击背景、Tab／Enter 未进入背景会话，背景节点不暴露为可操作内容。
- 最初卡片被 AnimatedContent 最小宽度撑满窗口，已用放松最小约束的全窗口 Box 修复；实际截图为约 420 dp 的卡片。`display-floating-options.png` 是返修前证据，不代表交付效果。
- 深色阴影不再取 TextPrimary，避免深色模式浅色文字导致白色光圈。浅色／深色和高级材质＋流光组合经过截图检查。
- Chat 打开新规则后，列表离开，Chat 左移并适配目标宽度，辅助页进入右侧。草稿留在同一 Chat 实例；返回后仍是原对话。录像中能看到过渡中间三段连续画布，而非瞬间互换两页。
- 新对话从侧栏进入只覆盖右 Chat。开启悬浮偏好后仍如此；标准问答进入新 Chat 问候页，拟人进入角色设定目录。
- 创建目录未选择模式时，人设／模拟设置的可点击父节点 `enabled=false`。选动作演绎后解锁；输入 `Atlas_QA`，在人设和模拟设置间切换后文本仍在。返回出现未保存确认，没有自动创建角色。
- 现有角色预览与编辑仍走原表单；思考和回复两种状态下保存父节点均禁用，显示“请等待角色思考/回复完毕后保存更改”。debug 最终提交校验两次均 `busyCommitRejected=true`。
- 悬浮模式修改离线角色名称后保存：15 秒离线学习期间页面占满窗口，Back、Tab、文字、Enter 都没有绕回聊天。只有一条模型请求，结束后名称才变为 `林间书房_QA`，消息数仍为 18，无误发送。
- 500 dp 自由窗口仍识别为平板，可切换列表／详情；稳定进入设置后扩大窗口，设置详情保留并恢复类别＋详情。测试需要等待真实 UI 更新；窗口模式的 `am start` 会新建测试 Activity，不将它造成的初始 Chat 当作路由恢复结论。
- 手机竖横屏保持原单页设置列表，实际查找“平板显示优化”的节点数量两次均为 0。
- 代码复核发现改写提示词时新常驻侧栏可能绕过导航锁；已禁用侧栏交互、回调复核，并让拟人改写拒绝不属于当前对话的消息 ID。隐藏 Chat 的返回锁也只在页面活跃时启用。
- 设置跳转模型编辑／日志／语音页使用稳定的路线动画持有者，不因分支切换失去进退动画。两次独立只读代码复核后无剩余重要问题。
- 最终检查 `logcat -b crash` 未观察到本轮交互崩溃。

## 关键截图与录像

所有文件位于本目录，均来自运行中的 Android 应用，不是设计效果图。

| 内容 | 文件 |
| --- | --- |
| 嵌入设置与初始模型页 | `embedded-settings.png` |
| 浅色悬浮设置／右侧位置 | `floating-settings-fixed.png`、`floating-right.png` |
| 深色流光悬浮设置 | `final-dark-floating-settings.png`、`final-dark-floating-options.png` |
| 连续 Chat＋新规则 | `embedded-rules-draft.png` |
| 悬浮偏好下仍嵌入的新对话 | `final-floating-forced-embedded-new.png` |
| 标准模式新问候页 | `final-standard-greeting.png` |
| 创建模式锁定／共享草稿／退出确认 | `creation-mode-locked.png`、`creation-draft-retained.png`、`creation-unsaved-confirm.png` |
| 平板竖屏创建／模拟设置 | `final-portrait-creation.png`、`final-portrait-character-editor.png` |
| 忙碌禁用保存 | `final-character-thinking-save-disabled.png`、`final-character-typing-save-disabled.png` |
| 悬浮保存时全窗口学习 | `final-floating-learning-exclusive.png`、`final-learning-blocked-input.png` |
| 窄设置与扩大后的设置 | `narrow-settings-before-growth.png`、`settings-after-growth-check.png` |
| 手机竖横屏设置 | `final-phone-settings.png`、`final-phone-landscape-settings.png` |
| 实际动画录像／中间帧 | `final-tablet-motion.mp4`、`motion-frame-01.png`、`motion-frame-06.png` |

## 限制与交付边界

软件 GPU 模拟器录像用于核对方向、布局关系和图层衔接，不作为真机 60/120 fps 的证据。10 秒录制请求输出约 6.99 秒、58 帧，真机流畅度仍需在用户平板上体验确认。离线学习验收也不替代真实模型服务的兼容性验证。

未提交、Push 或部署；没有修改服务器或数据库。保留任务开始前的 Claude 同步、安全核验、语音、反馈和备份相关工作。本轮文档与安装包均只在本地生成。
