<div align="center">

<img src="docs/icon.png?v=1.1.0" width="120" height="120" alt="FreeChat Logo" />

# FreeChat

**自由对话，自由选择。**

一款 Android 端的多模型 AI 聊天应用 —— 既能当全能 AI 助手，也能成为你的专属拟人陪伴。

[![Website](https://img.shields.io/badge/%E5%AE%98%E7%BD%91-118.178.227.178-4C6FFF?style=flat-square&logo=googlechrome&logoColor=white)](https://118.178.227.178/)
[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white)](https://github.com/Belate-stay/FreeChat)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://github.com/Belate-stay/FreeChat)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material3-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)](https://github.com/Belate-stay/FreeChat)
[![Version](https://img.shields.io/badge/Version-1.1.0-0A84FF?style=flat-square)](https://github.com/Belate-stay/FreeChat/releases)
[![License](https://img.shields.io/badge/License-MIT-yellow?style=flat-square)](LICENSE)

</div>

---

## ✨ 项目简介

FreeChat 是一个 Android 端的多模型 AI 聊天应用，内置**两种模式**，一个 App 满足两种陪伴：

- 🤖 **标准模式** —— 一个能联网搜索、生成图片、识别图片、解析文件、生成 Office 文档、语音输入输出的全能 AI 助手。
- 💕 **拟人陪伴模式** —— 打造一个拥有独立人格的角色，支持 MBTI、性格、记忆、关系、情绪、剧情与作息模拟，可连接微信直接对话，陪你聊天、陪你成长。

所有模型均基于 **OpenAI 兼容 API**，模型、Key、地址完全由你自己掌控。对话默认只存在你自己的设备上；登录账号后可选择跨设备同步，**API Key 仅保存在本地、不上传服务器**。

---

## ✨ 核心特性

### 🤖 标准模式 · AI 助手
- 🔍 **联网搜索** —— 实时检索最新信息，自动优化搜索关键词；可自定义搜索源 API（内置免费 AnySearch），引用来源收进「信息源」折叠框
- 🎨 **AI 生图 / 识图** —— 文字生成图片，多图理解分析
- 📄 **文件处理** —— 上传图片、音频、文本、Office 文档并智能解析；文档**附件进上下文**，长对话也能随时调取资料
- 📊 **生成 Office 文档** —— 一句话生成 docx / xlsx / pptx
- 🎙️ **语音交互** —— 语音输入（ASR）+ AI 朗读（TTS，秒读不断流）
- 🧠 **思考过程展示** —— 实时呈现模型推理过程
- ✍️ **Markdown 渲染** —— 结构化排版输出
- ⭐ **收藏与分享** —— 消息收藏后集中查看；以 jpg / Markdown 分享或保存；对话可生成**在线网页链接**分享（免安装预览，可撤销）
- 📝 **建议与反馈** —— 未登录也可提交，可查看「我的反馈」历史
- 🤖 **内置「Claude 风格助理」** —— 开箱即用，贴合 Claude 的说话方式

### 💕 拟人陪伴模式 · 你的专属角色
- 👤 **自由捏人** —— 头像、姓名、性别、年龄、MBTI 十六型 + 四维滑块、性格预设、人物形象
- 🧬 **深度学习人设** —— 首次创建即生成专属人格，编辑时增量更新不「失忆」
- 🎭 **三种对话模式** —— 微信聊天（逐条发消息）/ 动作演绎（只演这一个角色）/ 剧情补足（小说式正文，可写旁白与其他角色），创建时选定、发出第一条消息后锁定
- 🧑🤝🧑 **配角 / 规则 / 用户形象** —— 这个世界里还有谁、按什么规矩运转、你自己是谁，都交给你写
- 💞 **关系系统** —— 12 种关系预设作方向参考，亲疏尺度由 AI 结合人设与上下文自行判断（不写死数值、不套模板）
- 🎨 **AI 创造力** —— 1-10 档可调，决定 AI 有多主动地引入新话题、新角色、新剧情
- 🧩 **参考原型** —— 填一个知名角色名，AI 先学你的设定，再用它的既有设定作补充（你的设定优先）
- 💗 **情绪系统** —— 多种情绪驱动回复节奏与语气
- 🧠 **记忆系统** —— 四层记忆（主线剧情 / 细节 / 全局 / 感知），删除保护联动，聊得越久越懂你
- ⏳ **时间感知** —— 角色与现实时间对应，历史消息带时间标注，叙事档自有世界历法
- 💬 **回复缓冲** —— 连发几条先攒着，一次想清楚再回（微信端与本地同语义）
- 🖼️ **生成当前场景图** —— 把正在发生的剧情交给生图模型画出来（动作演绎 / 剧情补足档）
- 📚 **原文学习** —— 导入小说原文作高权重文风参考，回复文笔贴合原作
- 🔎 **深度推演** —— 回复前先以角色的身份在心里过一遍，并带着回忆的关键词二次检索记忆（可选，代价是每轮多一次调用）
- 🌙 **作息模拟** —— 角色会睡觉、会醒，醒来自然解释漏回的消息
- 📮 **主动智能（Beta）** —— 会在合适的时间主动找你，由 AI 自己判断时机与要不要开口
- 📱 **连接微信（Beta）** —— 扫码绑定后直接用微信和角色聊天，分条发送、正在输入，机制云端运行不依赖客户端后台

### 🔌 自定义模型（开源版核心）
- 五类模型 —— **语言 / 生图 / 识图 / TTS / ASR** —— 全部支持自定义
- 任意 **OpenAI 兼容** API 均可接入（名称 / Key / 模型 ID / 地址 / 备注）
- 内置少量作者自用模型（不保证在线，可适度体验），相关 Key 不随源码分发
- 角色设定可 **导入 / 导出**，一键分享自己的角色或采用别人配好的角色

### ☁️ 账号与同步（可选）
- **不登录也能用** —— 纯本地即可使用全部功能，登录只是多个去处
- 登录后可跨设备同步对话、角色卡、记忆与**图片**（内容寻址去重）；**API Key 仅保存本地、不上传服务器**
- 配合 **网页版对话**，手机与浏览器之间接着聊
- 可查看账号当前登录的设备，并下线其它设备

### 🎨 界面与体验
- 💎 **液态玻璃** —— 磨砂玻璃质感 UI，可开关「高级材质」
- 🌈 **流光炫彩** —— 动态渐变光晕背景，与磨砂玻璃叠加（渲染改造后功耗更低）
- 🧊 **拟物态卡片** —— 非悬浮卡片采用拟物化质感
- 🔤 **多字体** —— 内置思源黑体、宋韵朗黑等多款字体，中英文混排
- 🌗 **多主题** —— 浅色 / 深色 / 黑色 / 跟随系统，支持**自定义色彩**
- 🌐 **多语言** —— 简体中文 / 繁體中文 / English
- ✨ **丝滑动画** —— 思考光球、消息入场、删除粒子、生成占位动效等
- 🎯 **细节** —— 启动器图标可切换、首页问候语按时段更换、长对话时间线快速定位

---

## 📥 下载

- **Android 客户端** —— 前往 [Releases](https://github.com/Belate-stay/FreeChat/releases) 下载最新 APK
  > 要求 Android 8.0（API 26）及以上。
- **网页版** —— 免安装，浏览器直接打开 [官方网站](https://118.178.227.178/)，与手机端账号互通

---

## 🔨 构建

```bash
git clone https://github.com/Belate-stay/FreeChat.git
cd FreeChat
# 用 Android Studio 打开，或命令行构建（需 JDK 17）
./gradlew assembleDebug
```

环境要求：

- Android Studio 或 JDK 17 + Android SDK 35
- minSdk 26 / targetSdk 34

> 内置模型所依赖的 API Key 出于安全考虑不随源码分发。clone 后请通过「设置 → 模型」添加你自己的 API，即可正常使用。

---

## 📁 项目结构

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
├── proactive/                 # 主动智能（本地定时唤醒）
├── ui/
│   ├── screens/               # 页面（聊天 / 设置 / 角色 / 账号 / 收藏 / 协议 / 更新日志 …）
│   ├── components/            # 组件（气泡 / 输入框 / 抽屉 / Markdown / 弹层 …）
│   └── theme/                 # 主题（色彩 / 字体 / 磨砂玻璃 / 拟物态）
└── util/                      # 工具（Office 解析与生成 / 分享）
```

---

## 🧩 技术栈

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

## 📄 开源与免责声明

- 内置模型为作者自用 API，**不保证随时在线、额度有限**，仅供体验，请勿依赖。
- 本项目不采集、不上传对话数据：不登录时所有内容只保存在本地设备；登录账号后同步的也只有您主动同步的对话、记忆与图片，**API Key 仅保存在本地、不上传服务器**。
- 首次启动需阅读并同意《免责声明》。

---

## 👤 作者

[Belate](https://github.com/Belate-stay) · [GitHub 仓库](https://github.com/Belate-stay/FreeChat) · [官方网站](https://118.178.227.178/) · [CSDN 主页](https://blog.csdn.net/weixin_51354748)

---

## 📜 许可证

本项目采用 [MIT License](LICENSE) 开源，Copyright © 2026 [Belate-stay](https://github.com/Belate-stay)。
