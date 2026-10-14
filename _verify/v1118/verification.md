# FreeChat Beta 1.1.18 验证记录

日期：2026-10-10。versionCode：214；versionName：1.1.18。

## 本版完成项

1. 生图占位到结果：同一消息、同一固定区域内，像素圆点逐渐扩张融合，遮罩柔和收敛到实际图片边界，同时渐显已解码图片。图片采用统一 12dp 圆角。动画不改变消息高度或页面位置；低动效设置直接呈现结果。
2. 快速定位：右侧间距 20dp，略加强阴影和对比；按住后轨道加宽、拉长，标识放大。手势捕获轴保持固定，日期浮窗随当前可见记录更新，闲置 5 秒自动隐藏。
3. 识图上下文：标准模式沿用普通聊天的规则、记忆、引用、附件和历史预算；拟人模式携带人设、规则、记忆及历史，再交语言模型形成角色回复。有效深度思考设置传递到支持该能力的识图模型。
4. 图片持久化及删除：生成网址必须先下载原图、原子写入应用私有 filesDir 后才算成功；编码结果同样持久化。保留且有效的旧网址自动恢复成本地文件。修复图片同步压缩的 bounds-only 解码判断；删除最终引用时清理本地原图和同步副本，持久化排队删除云端载荷，不建立回收站。

## 构建及自动测试

- `:app:testDebugUnitTest :freechat-core:test :app:assembleDebug :app:assembleRelease`：成功。
- JVM 测试 XML 汇总：526 个；523 通过，3 跳过，0 失败，0 错误。
- 新增覆盖：远程图持久化、无效结果拒绝、网址/本地身份归一、共享图片最终引用删除、旧图恢复不改正文、下载期间删除不复活；识图保留上下文；渐显无空帧、固定占位尺寸、圆角遮罩收敛。
- 服务器 `node --test test/sync-deletion.test.js test/sync-deletion-http.test.js`：24/24 通过。仅使用本地临时数据库和 localhost HTTP，不修改真实数据库，不部署。
- 沙箱内首轮 HTTP 测试因 localhost 网络权限 EACCES 被阻止；授予测试所需本地网络权限后完整回归通过。
- `git diff --check`：通过（只有 Windows LF/CRLF 提示）。
- APK badging：`com.freechat`，214，1.1.18。
- APK v2 签名校验：通过，沿用项目现有 Android Debug 签名配置，未更换证书。

## 离线模型端到端测试

隔离模拟器 `emulator-5560`，使用独立 userdata 和只读 AVD；没有运行真实收费模型请求，也没有使用用户 API Key。

最终日志：

```text
ImageRoutingQA: PASS edits=4 vision=2 context=standard+companion local_delivery=1 compression=1 native=1 fallback=2 rejection=1 regeneration=2 chat=4
```

验证内容包括标准/拟人识图请求中的规则、记忆、历史、引用、人设、图片与思考设置；网址型生成结果仅持久化一份可显示原图；大于 4MiB 的原图保留，同时正常压缩成服务器可同步的载荷；原生/兼容生图、编辑、重新生成及失败路径。

## 视觉与交互验证

- 使用实际生产 `ChatBubble` 和 `GeneratedImage` 组件，离线 PNG 从生成占位切换成结果；浅色标准、深色拟人、低动效场景均完成。
- 最终圆角修复后的几何日志：标准消息 `(1184, 1256)`、拟人消息 `(1184, 1322)`，顶部 `y=347`，完成前后尺寸与坐标没有变化，`PASS stable=true`。
- 逐帧查看 `final-060/065/068/070/073/076/084.png`，确认点阵融合、图片渐显与统一圆角，无过渡空白帧；原始录屏 `final-reveal.mp4`，截取过渡片段 `image-reveal-preview.mp4`。
- 定位条静止截图 `rail-visible-final.png`，按住拖动截图 `rail-held-final.png`：轨道留白、浮起阴影、放大和日期浮窗均可见；拖动后列表由尾部 ROW 57–59 到开头 ROW 0–2；闲置后的 `after-rail.xml` 不含 SeekBar。
- 模拟器采用软件 GPU，截图/录屏用于验证视觉和位置连续性，不作为真实手机 60/120fps 性能保证。实际设备流畅度仍应安装后复核。

## 边界与交付

- 云端是同步副本；离线删除先完成本地清理并持久化排队，恢复联网后同步删除云端。
- 同一图片若仍被其他消息/对话引用，保留共享字节，避免误删其他可见图片。
- 旧图片若提供商链接已过期，且本地与云端均没有副本，无法恢复不存在的原始字节。
- 本次没有修改/部署服务器，没有操作真实数据库，没有提交或 Push。
- 安装包：`D:/Desktop/FreeChat/FreeChat_Beta_1.1.18.apk`。
