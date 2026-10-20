# FreeChat Beta 1.2.0.2 验收记录

日期：2026-10-16。交付 versionCode 251 / `Beta 1.2.0.2`。

## 交付与构建

- 安装包：`D:/Desktop/FreeChat/FreeChat_Beta_1.2.0.2.apk`
- 文件大小：35,427,763 字节。
- SHA-256：`EBD1FB5FD8854A5B8E991187E2E7CFDA2063CD722299ED109BCFC972942D7E49`
- aapt 确认包名 `com.freechat`、versionCode 251、versionName `Beta 1.2.0.2`；minSdk 26 / targetSdk 34 不变。
- apksigner 验签通过，APK Signature Scheme v2，单签名者。
- Release manifest 不含 `TabletSeedQaActivity` / `TabletBusyQaReceiver`；离线夹具仅在 debug 源集。
- 没有部署、提交或 Push；本轮开始前 Claude 的同步、安全核验、反馈、语音及备份改动保留。

串行构建命令（JBR 17）：

```powershell
$env:JAVA_HOME='C:/Users/M/.jdks/jbr-17.0.14'
$env:PATH="$env:JAVA_HOME/bin;$env:PATH"
./gradlew.bat '-Dorg.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1024m' :app:testDebugUnitTest :freechat-core:test :app:assembleDebug :app:assembleRelease --console=plain
```

最后一次完整执行：`BUILD SUCCESSFUL in 2m 52s`。

| 测试模块 | 总数 | 通过 | 跳过 | 失败／错误 |
| --- | ---: | ---: | ---: | ---: |
| app JVM | 648 | 645 | 3 | 0 |
| freechat-core | 46 | 46 | 0 | 0 |

新平板相关 28 项：布局策略 6、人设保存分类 5、导航与隔离契约 10、设置导航契约 7。先写回归复现，再修改实现；例如键盘隔离／功能定位 15 项中 3 项先失败，转场分隔线 10 项中 1 项先失败，修改后全量通过。

构建仍有已有弃用 API 和 Haze 实验 API 警告，没有为了消除这些警告重构无关代码。

## 隔离设备与尺寸

测试只针对 `emulator-5560`，API 37 / x86_64 / SwiftShader，独立 userdata 位于本目录 `emulator/`，没有清空或使用用户日常 AVD 数据。`emulator/` 已加入 gitignore。

| 场景 | 像素／密度 | 实际布局 |
| --- | --- | --- |
| 平板横屏 | 1920×1200 / 240 dpi | 1280×800 dp，双栏 |
| 平板竖屏 | 1200×1920 / 240 dpi | 800×1280 dp，双栏 |
| 平板自由窗口 | 窗口 Rect(150,100–900,1150) / 240 dpi | 宽 500 dp，临时单面板，仍是平板导航 |
| 自由窗口放大 | Rect(100,70–1800,1150) / 240 dpi | 自动恢复双栏 |
| 手机竖屏 | 1080×2400 / 440 dpi | 短边约 393 dp，原手机布局 |
| 手机横屏 | 2400×1080 / 440 dpi | 短边约 393 dp，不误判平板 |

## 已执行的交互与视觉检查

- 对话列表点选进入聊天，正文／输入框限宽，顶部操作可见。平板竖屏左侧较窄时底部功能以图标显示，不挤压文本。
- 聊天输入草稿后切换单栏全屏、恢复双栏、打开新规则，未发送文本仍在。新规则保留即时写入路径，不增加保存按钮。
- 从聊天打开模拟设置，左聊天／右表单；预览的人设为灰色层级，功能项可改但只是草稿。
- 修改功能开关但不保存，直接读取隔离存档确认旧 `deepThinkingMode` 保持不变；返回出现未保存提示，放弃后档案仍旧。
- 仅修改功能项并显式保存，存档改变，离线模型日志为零请求，没有进入学习。
- 修改角色名称，保存前存档名称不变；旋转后原始输入草稿仍在，旋转没有提交档案。
- 宽角色表单的人设／功能两栏分别滚动；人物栏全屏后点击“跳转到功能设置”，功能栏重新可见。
- 角色 thinking 与 typing 两种状态：保存按钮的**可点击父节点**为 `enabled=false`，图标变灰；点击无保存确认。能看到指定文案“请等待角色思考/回复完毕后保存更改”。
- 同时在真实 ViewModel 调用最终提交，debug 日志两种状态均为 `busyCommitRejected=true`；恢复 idle 后不排队自动保存。
- 人设保存用本机离线模型：15 秒延迟期间学习页占全窗口，没有聊天或侧栏可操作。尝试 Back、Tab、文字、Enter，仍停在学习页；结束后仅一条学习请求、聊天记录仍是 18 条，无新增聊天，学习成功后名称才变更。
- 设置五分类、子页返回、模型池＋编辑器、收藏列表＋详情均经过实际点击和截图检查。
- 500 dp **真实自由窗口**中可切换面板；进入收藏首先能看到列表，点选可到详情，放大后重新出现两栏及两个全屏控件。
- 主题组合检查：浅色／深色、高级材质开／关、流光开启、松绿与黑白；弹层限制在发起面板内，圆角与原有材质一致。没有复制第二套配色系统或液态背景时钟。
- 手机竖屏及横屏设置仍为原统一列表，没有平板分类栏、全屏或切换面板按钮。
- `logcat -b crash` 未观察到本轮交互崩溃。

## 关键截图和录像

以下文件均在本目录，已实际打开检查，不是生成的效果图。

| 内容 | 文件 |
| --- | --- |
| 横屏列表与聊天 | `tablet-landscape-conversation.png` |
| 竖屏列表与聊天 | `tablet-portrait-chat.png` |
| 聊天＋新规则 | `tablet-rules.png` |
| 聊天＋模拟设置（竖屏） | `tablet-portrait-character.png` |
| 人设／功能宽表单 | `tablet-character-wide.png` |
| 人物全屏后恢复功能栏 | `tablet-function-jump-restored.png` |
| 未保存确认 | `tablet-character-unsaved.png` |
| 全窗口独占学习 | `tablet-learning-exclusive.png` |
| 分类设置最终截图 | `tablet-final-liquid-settings.png` |
| 模型池＋编辑器 | `tablet-model-editor.png` |
| 深色聊天／设置 | `tablet-dark-chat.png`、`tablet-dark-settings.png` |
| 主题菜单／黑白 | `tablet-theme-sheet.png`、`tablet-monochrome-return.png` |
| 普通材质 | `tablet-material-off.png` |
| 窄窗口收藏列表 | `tablet-narrow-favorites-list.png` |
| 窗口放大恢复双栏 | `tablet-window-grown-split.png` |
| 手机竖／横屏 | `phone-settings.png`、`phone-landscape-settings.png` |
| 最终转场录屏／中间帧 | `tablet-final-motion.mp4`、`final-motion-frame-a.png`、`final-motion-frame-b.png` |

旧录像 `tablet-motion-check.mp4` 与 `motion-expand-frame.png` 保留了分隔线提前落位的返修证据，不是最终效果。最终分隔线随实际面板边界移动并与两栏透明度同步，不再横切卡片。

## 重放方式

1. 只在独立模拟器启动 debug seed：`adb -s emulator-5560 shell am start -n com.freechat/com.freechat.qa.TabletSeedQaActivity`。**此操作会替换测试设备的对话，不要对真实用户设备执行。**
2. 需要深色／流光组合时加 `--ez dark true --ez advanced true --ez liquid true`；默认中文、浅色、松绿。
3. 角色保存忙碌测试：`adb -s emulator-5560 shell am broadcast -n com.freechat/com.freechat.qa.TabletBusyQaReceiver --es phase thinking`，也可传 `typing` 或 `idle`。
4. 学习流程只连接本机夹具：`node _verify/v1202/offline-model.cjs`，然后 `adb -s emulator-5560 reverse tcp:18764 tcp:18764`。模型 key 是字面量 `offline-test`，不是任何真实凭据；日志仅含路径、消息数和 stream 标志。
5. 交互帮助函数：在 PowerShell `. ./_verify/v1202/device-checks.ps1`，使用 `Tap-TabletNode`、`Save-TabletShot`、`Show-TabletLabels`。UI dump 未就绪会重试并拒绝读取旧 XML，不能用缓存树冒充本次检查。
6. 真窄窗口：测试设备上启用 freeform，`am start --windowingMode 5 -n com.freechat/com.freechat.MainActivity`，从 `dumpsys activity activities` 取**当次**任务 ID，再用 `am task resize <id> 150 100 900 1150`；不要硬编码本次 ID 9 给其他设备。

## 明确的验证边界

- 软件 GPU 模拟器只能验证布局、状态和视觉连贯性，录屏不是流畅度基准；不能据此声称真实平板稳定 60／120 fps。需要真机补测性能、触控／手写笔、不同厂商分屏与键盘。
- Android 8–12 的平板设备识别回退、全部系统字体缩放、全部语言和所有主题组合未逐设备穷举。
- 没有请求用户付费模型、修改 API 配置、连接账号或验证线上云同步；学习失败保留草稿与目标绑定做了源码／契约复核，未作真实服务商故障注入。
- 仅本地 Android 客户端实现；服务器和数据库未改动、未部署。
