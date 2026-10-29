<div align="center">

<img src="docs/icon.png?v=1.2.3" width="120" height="120" alt="FreeChat Logo" />

# FreeChat

**自由对话，自由选择。**

Android 多模型 AI 聊天应用 —— 全能 AI 助手，与专属拟人陪伴，兼得。

[![Website](https://img.shields.io/badge/%E5%AE%98%E7%BD%91-freechater.com-4C6FFF?style=flat-square&logo=googlechrome&logoColor=white)](https://freechater.com/)
[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white)](https://github.com/Belate-stay/FreeChat)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://github.com/Belate-stay/FreeChat)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material3-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)](https://github.com/Belate-stay/FreeChat)
[![Version](https://img.shields.io/badge/Version-1.2.3-0A84FF?style=flat-square)](https://github.com/Belate-stay/FreeChat/releases)
[![License](https://img.shields.io/badge/License-MIT-yellow?style=flat-square)](LICENSE)

</div>

---

## 项目简介

FreeChat 是一款 Android 端多模型 AI 聊天应用，提供两种互补的使用模式：

- **标准模式** —— 全能 AI 助手：联网搜索、图片生成与识别、文件解析、Office 文档生成、语音输入与朗读。
- **拟人陪伴模式** —— 具备独立人格的角色：性格与记忆、关系与情绪、剧情与作息模拟，并可接入微信直接对话。

模型接入采用 OpenAI 兼容协议，模型、API Key 与接口地址完全由用户自主配置。对话默认仅保存在本机；登录账号后可选跨设备同步。**API Key 仅保存在本地，不上传服务器。**

---

## 核心特性

### 标准模式 · AI 助手

- **联网搜索** —— 实时检索并自动优化查询词，支持自定义搜索源（内置免费 AnySearch），引用来源统一收纳于「信息源」折叠框
- **图片生成与识别** —— 文生图（内置 ChatGPT-Image-2.5）与多图理解分析（内置 DeepSeek-V4.1-Flash 识图）
- **文件处理** —— 图片、音频、文本、Office 文档上传解析；附件内容进入上下文，长对话可随时引用
- **文档生成** —— 一句话生成 docx / xlsx / pptx
- **语音交互** —— 语音输入（内置 MiMo-V2.5-ASR）与 AI 朗读（TTS 流式不中断）；支持自定义音色（提示词设计 / 音频复刻，Beta）
- **推理过程展示** —— 模型思考过程实时可见，默认折叠，可展开回看
- **Markdown 渲染** —— 结构化排版输出，含嵌套与表格
- **数学公式显示**（1.2.3 新增）—— 绝大部分数学公式符号直接显示
- **收藏与分享** —— 消息集中收藏；可导出 jpg / Markdown，或生成在线网页分享链接（免安装预览、可撤销）
- **建议与反馈** —— 未登录亦可提交，可回看历史；「从夯到拉」评价卡一键反馈回复质量
- **内置 Claude 风格助理** —— 开箱即用

### 拟人陪伴模式 · 专属角色

- **角色构建** —— 头像、姓名、性别、年龄、MBTI 十六型与四维滑块、性格预设、人物形象
- **人设学习** —— 创建时生成专属人格，编辑时增量更新不失忆
- **三种对话模式** —— 微信聊天（逐条发消息）/ 动作演绎 / 剧情补足，创建时选定
- **世界观** —— 配角、规则与用户形象均可自定义，由 AI 统一演绎
- **关系系统** —— 12 种关系预设作方向参考，亲疏尺度由 AI 结合人设与上下文自行判断
- **AI 创造力** —— 1–10 档可调，决定 AI 引入新话题、新角色与新剧情的主动性
- **记忆与时间** —— 四层记忆（主线 / 细节 / 全局 / 感知）配删除保护；时间感知将角色与现实时间对应
- **原文学习** —— 导入小说原文作高权重文风参考，回复贴合原作
- **深度推演 / 作息模拟 / 回复缓冲 / 场景图生成** —— 回复前角色内心推演、按作息睡眠与醒来、连发消息合并理解、把当前剧情交给生图模型绘制（支持画幅与主体预设）
- **语音回复**（1.2.1 新增，Beta）—— 微信聊天档开启后角色可发送语音消息；长按语音键直接发送语音
- **主动智能**（1.2.1 重写，Beta）—— 由服务器调用 API 在合适的时机主动发消息，不依赖客户端后台运行（依赖微信消息推送）
- **连接微信**（Beta）—— 扫码绑定后用微信与角色对话：分条发送、正在输入、表情识别与回复，机制云端运行
- **角色卡与角色池**（1.2.3 新增）—— 拟人逻辑重写：角色与对话分离，可单独创建角色/对话，或将多个角色与指定的对话绑定
- **多角色群聊**（1.2.3 新增）—— 多个不同的角色在群中聊天对话，支持 拍一拍 和 @ 功能
- **多角色文本**（1.2.3 新增）—— 多个不同的角色共同参与剧情补足创作，无主/配角之分，剧情走向随用户与 AI 共同完成
- **内置角色池**（1.2.3 新增）—— 暂无内置角色卡，用户可选择上传自己的角色卡

### 模型接入

- 五类模型 —— **语言 / 生图 / 识图 / TTS / ASR** —— 全部支持自定义，任意 OpenAI 兼容 API 可接入
- **获取 API 教程**（1.2.1 新增）—— 应用内以 DeepSeek 为例逐步指导申请并填入自己的 API Key
- **导入已有多模态模型** —— 从内置模型池一键导入，免去重复填写
- 角色设定可**导入 / 导出**，一键分享或采用他人配好的角色
- 内置少量作者自用模型便于体验（额度有限，不保证在线，请勿依赖）

### 账号与同步（可选）

- **不登录也能用** —— 纯本地即可使用全部功能，登录只是多一个去处
- 登录后跨设备同步对话、角色、记忆与图片（内容寻址去重）
- 配合**网页版**，手机与浏览器之间无缝续聊
- 登录设备管理 —— 查看当前登录设备，可下线其他设备

### 界面与体验

- **平板工作台**（1.2.1 增强）—— 横竖屏自适应双栏布局，平板专属操作逻辑与动画
- **液态玻璃 · 流光炫彩 · 拟物态卡片** —— 磨砂玻璃质感 UI，「高级材质」可开关
- **多字体 / 多主题 / 多语言** —— 内置多款中文字体；浅色、深色、黑色、自定义色彩；简中 / 繁中 / 英文
- **动效系统** —— 思考灵感球、消息入场、粒子反馈、生成占位到成图的变形过渡、果冻滑动等
- **关于 FreeChat** —— 聚合页管理更新日志与协议；在线检查更新并预览更新内容，一键下载安装

---

## 下载

- **Android 客户端** —— 前往 [Releases](https://github.com/Belate-stay/FreeChat/releases) 或[官方网站](https://freechater.com/)下载最新安装包
  > 要求 Android 8.0（API 26）及以上。
- **网页版** —— 免安装，浏览器直接打开[官方网站](https://freechater.com/)，与手机端账号互通

---

## 构建

```bash
git clone https://github.com/Belate-stay/FreeChat.git
cd FreeChat
# 用 Android Studio 打开，或命令行构建（需 JDK 17）
./gradlew assembleDebug
```

环境要求：

- Android Studio 或 JDK 17 + Android SDK 35
- minSdk 26 / targetSdk 34

> 内置模型所依赖的 API Key 出于安全考虑不随源码分发。clone 后请在「设置 → 模型」中添加自己的 API，即可正常使用。

---

## 项目结构

```
freechat-core/                 # 共享模块：数据模型 + 拟人机制（App 与服务器大脑同码）
freechat-companion/            # 服务器大脑（可选自建）：微信通道的拟人生成服务
app/src/main/java/com/freechat/
├── MainActivity.kt            # 单 Activity 宿主 + 页面路由
├── FreeChatApp.kt             # Application
├── ReplyService.kt            # 前台服务保活 + 系统通知
├── viewmodel/
│   └── ChatViewModel.kt       # 核心逻辑：对话 / API / 记忆 / 角色
├── data/
│   ├── SettingsRepository.kt  # DataStore 持久化
│   ├── LocalStore.kt          # 对话与角色存档
│   ├── TtsController.kt       # TTS 语音合成
│   ├── MiMoAsr.kt             # ASR 语音识别
│   ├── SearchConfigStore.kt   # 自备搜索凭据（KeyStore 加密，不同步）
│   └── MemoryManager.kt       # 记忆检索
├── sync/                      # 账号与跨端同步（登录 / 合流 / 冲突处理 / 图片上云）
├── proactive/                 # 主动智能（客户端开关与设置，决策由云端完成）
├── ui/
│   ├── screens/               # 页面（聊天 / 设置 / 角色 / 账号 / 收藏 / 协议 / 更新日志 …）
│   ├── components/            # 组件（气泡 / 输入框 / 抽屉 / Markdown / 弹层 …）
│   └── theme/                 # 主题（色彩 / 字体 / 磨砂玻璃 / 拟物态）
└── util/                      # 工具（Office 解析与生成 / 分享）
```

---

## 技术栈

| 分类 | 技术 |
|------|------|
| 语言 | Kotlin 2.0 |
| UI | Jetpack Compose + Material3 |
| 网络 | OkHttp 4.12（SSE 流式） |
| 序列化 | Gson |
| 存储 | DataStore Preferences + 本地 JSON |
| 图片 | Coil |
| 特效 | Haze（液态玻璃） |
| 同步 | 自建同步服务端（Node，可选登录） |
| 架构 | MVVM + StateFlow（单 Activity） |

---

## 开源与用户协议

- 内置模型为作者自用 API，**不保证随时在线、额度有限**，仅供体验，请勿依赖。
- 本项目不采集、不上传对话数据：不登录时所有内容只保存在本地设备；登录账号后同步的也只有您主动同步的对话、记忆与图片，**API Key 仅保存在本地、不上传服务器**。
- 「自定义语音音色」仅限使用您本人或已获授权的声音样本，严禁克隆他人声音用于冒充、欺诈等违法用途；音频样本会发送至第三方语音服务（小米 MiMo）处理，仅保存在本机、不参与云同步。
- 首次启动需阅读并同意《用户协议与使用条款》。

---

## 作者

[Belate](https://github.com/Belate-stay) · [GitHub 仓库](https://github.com/Belate-stay/FreeChat) · [官方网站](https://freechater.com/) · [CSDN 主页](https://blog.csdn.net/weixin_51354748)

---

## 许可证

本项目采用 [MIT License](LICENSE) 开源，Copyright © 2026 [Belate-stay](https://github.com/Belate-stay)。
