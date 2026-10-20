# FreeChat Beta 1.2.0.4 验收记录

日期：2026-10-16。versionCode **253**，versionName **Beta 1.2.0.4**。

## 构建和安装包

- 交付：`D:/Desktop/FreeChat/FreeChat_Beta_1.2.0.4.apk`
- 大小：**35,493,299** 字节。
- SHA256：`B5B7568DF225C7C662BFD28E697A6D00978B97050E90843FE43AB54B4CDED152`
- aapt：包名 com.freechat，versionCode 253，minSdk 26，targetSdk 34。
- apksigner：验证通过，v2，单签名者。Release 清单不含 TabletSeedQaActivity / TabletBusyQaReceiver。
- 最后完整构建：**BUILD SUCCESSFUL in 2m 39s**，98 tasks，15 executed / 83 up-to-date。

```powershell
$env:JAVA_HOME='C:/Users/M/.jdks/jbr-17.0.14'
$env:PATH="$env:JAVA_HOME/bin;$env:PATH"
./gradlew.bat '-Dorg.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1024m' :app:testDebugUnitTest :freechat-core:test :app:assembleDebug :app:assembleRelease --console=plain
```

| 测试 | 总数 | 通过 | 跳过 | 失败／错误 |
| --- | ---: | ---: | ---: | ---: |
| app JVM | 691 | 688 | 3 | 0 |
| freechat-core | 46 | 46 | 0 | 0 |

本版新增 20 项：方向策略 1、导航契约 10、设置边界契约 7、按钮布局契约 2。原两侧均向左退出的期望按最新需求改为原路退回。其余原人设保存、忙碌和学习隔离契约均随全量通过。

先复现再修改：首轮方向及路由契约 9 项中 5 失败；设置合并 6 项全部失败；审查新增覆盖层级／炫彩衬底 3 项失败。模拟器发现左侧分类栏的系统返回监听抢走更新日志返回后，补契约并移除重复监听；收藏详情也补了对应契约。首帧闪页约束先失败后修正。按钮布局先 2 项失败；扩展模型导入／收藏跳转约束后再次失败，补齐调用点后全量无失败。

## 运行环境和实际检查

仅使用独立 emulator-5560、API 37 x86_64 / SwiftShader，沿用独立 `_verify/v1202/emulator/userdata.img`。没有操作用户日常设备、登录真实账号、调用付费生成接口或修改线上服务。运行验证使用 Debug，同源 Release 已另做构建、版本、签名和清单检查。

| 场景 | 检查结果 |
| --- | --- |
| 横屏平板 1920×1200 / 240dpi | 账号在右侧，侧栏保持可操作；侧栏选对话以 Chat 覆盖账号 |
| 账号 → 设置 → 返回 | 账号作为铺底保留，设置两侧分别进入；返回两侧逆向退出后回原账号 |
| 关于 | 直接为原聚合内容，无额外关于FreeChat按钮；反馈在协议之后、评分之前 |
| 更新日志／协议 | 左设置栏保持；系统返回日志回关于，标题栏返回协议也回关于 |
| AI语音 | 语音调试在右栏，左设置栏保持 |
| 分类切换／反馈 | 外部或内部详情可由左分类选择新页覆盖；旧页不响应触控 |
| 设置嵌入 ↔ 悬浮 | 当前平板显示设置保留，不跳回模型主列表；左右位置可切换 |
| 悬浮阴影 | 浅色、深色截图检查；背景无额外遮暗／模糊，点击背景新对话无反应 |
| 竖屏平板 1200×1920 / 240dpi | 关于／日志仍双栏；创建角色进入模式、角色人设、模拟设置目录，返回回选择聊天模式 |
| 收藏详情 | 右侧进入，系统返回回列表／空详情；跳转原对话回 Chat |
| 手机 1080×2400 / 440dpi | 原完整设置列表及关于FreeChat二级页保留 |
| 横屏手机 2400×1080 / 440dpi | 一个返回入口、无平板优化入口，未误判为平板 |

`account-motion.mp4` 使用系统动画 3× 慢放检查方向和覆盖顺序；`account-motion-contact.png` 与 `account-workspace-contact.png` 为抽帧。录屏中的新 Chat 从右侧覆盖账号；返回账号向右退出；设置左页退出左侧、右页退出右侧，账号没有被 Chat 抢先替换。

`favorite-cover.mp4`、`favorite-cover-contact.png` 用于检查收藏跳转。`floating-right-light.png`、`floating-dark-liquid.png` 检查抬升阴影；后者开启了炫彩偏好，但现有设计在深色模式主动禁用炫彩渲染，未改变这一旧规则。真正浅色炫彩检查使用 `liquid-account.png` 和液态背景录屏。

`liquid-cover.mp4` 的抽帧暴露了首帧闪页：Chat 先完整显示一帧，LaunchedEffect 才把它移到屏幕外。修复为每次覆盖的新 Animatable 在首个组合就从屏外开始，不再用延迟 snap 设置起点；根工作台的派生状态随新实例刷新。复查使用 `liquid-cover-final.mp4`、`liquid-cover-final-start.png`、`liquid-cover-final-workspace.png`，逐帧由原页开始、随后新页从右侧覆盖，没有先闪现完整 Chat，也未见旧文字穿透新页。

## 追加：按钮宽度与居中

按最后补充要求，仅收窄平板嵌入面板的独立主操作按钮，最大 320dp；表单、设置行、模式选项、行内操作不缩窄，不改变保存／提交行为。覆盖账号、反馈、模型添加／导入／获取／保存、图标保存、角色创建／保存／下一步、音色创建／保存、收藏跳转。沿用原阴影、圆角和点击动效，手机与手机比例悬浮窗不应用该上限。

- `account-actions-landscape.png`：登录按钮实际点击区域 `[977,662][1457,734]`，480px／1.5 = 320dp，在右侧面板居中。
- `feedback-actions-landscape.png`、`feedback-actions-portrait.png`：横竖屏提交按钮保持相同合理宽度、居中，输入区保持原宽度；未提交真实反馈。
- `model-pool-actions-portrait.png`、`model-editor-actions-final.png`：模型操作独立居中，表单仍完整可用；未调用真实模型列表 API。
- `favorite-actions-final.png`：自动断言跳转按钮点击区域 `[977,1068][1457,1146]`，宽 320dp，中心与右面板相差不超过 2px。
- `phone-action-width-verified.png`：1080×2400／440dpi 手机从正常入口重进模型编辑，自动断言导入按钮为 `[55,319][1025,451]`、宽 970px（约 353dp），没有误用平板 320dp 上限。切换虚拟设备尺寸会重建页面，因此重新从侧栏／设置入口进入，不把旧页面假定为仍然存在。

最终调试包安装成功。恢复测试设备尺寸与系统动画速率后，AndroidRuntime:E / FreeChatShader:W 无输出；结束本次独立模拟器，不影响其他设备。最终交付文件与已校验 Release 的 SHA256 一致。

## 验收边界

- 软件 GPU 模拟器录屏不是实机帧率测试；不据此宣称稳定 60/120fps。仍需用户平板最终确认触感和帧稳定性。
- 反馈／我的分享保留既有登录与服务逻辑；未登录真实账号，不做真实分享撤回或更新下载安装等外部写入操作。登录态分享卡片顺序由契约检查。
- 无服务器／数据库改动，无部署、commit 或 push。保留此前工作区中已有的用户／Claude 修改。
