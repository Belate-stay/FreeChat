<div align="center">

<img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="120" height="120" alt="FreeChat Logo" />

# FreeChat

**自由对话，自由选择。**

一款 Android 端的多模型 AI 聊天应用 —— 既能当全能 AI 助手，也能成为你的专属拟人陪伴。

[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white)](https://github.com/Belate-stay/FreeChat)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://github.com/Belate-stay/FreeChat)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material3-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)](https://github.com/Belate-stay/FreeChat)
[![Version](https://img.shields.io/badge/Version-1.0.01-0A84FF?style=flat-square)](https://github.com/Belate-stay/FreeChat/releases)

</div>

---

## ✨ 项目简介

FreeChat 是一个 Android 端的多模型 AI 聊天应用，内置**两种模式**，一个 App 满足两种陪伴：

- 🤖 **标准模式** —— 一个能联网搜索、生成图片、识别图片、解析文件、生成 Office 文档、语音输入输出的全能 AI 助手。
- 💕 **拟人陪伴模式** —— 打造一个拥有独立人格的角色，支持 MBTI、性格、记忆、关系、情绪、剧情与作息模拟，陪你聊天、陪你成长。

所有模型均基于 **OpenAI 兼容 API**，模型、Key、地址完全由你自己掌控。对话数据仅保存在本地设备。

---

## ✨ 核心特性

### 🤖 标准模式 · AI 助手
- 🔍 **联网搜索** —— 实时检索最新信息，自动优化搜索关键词
- 🎨 **AI 生图 / 识图** —— 文字生成图片，多图理解分析
- 📄 **文件处理** —— 上传图片、音频、文本、Office 文档并智能解析
- 📊 **生成 Office 文档** —— 一句话生成 docx / xlsx / pptx
- 🎙️ **语音交互** —— 语音输入（ASR）+ AI 朗读（TTS，秒读不断流）
- 🧠 **思考过程展示** —— 实时呈现模型推理过程
- ✍️ **Markdown 渲染** —— 结构化排版输出

### 💕 拟人陪伴模式 · 你的专属角色
- 👤 **自由捏人** —— 头像、姓名、性别、年龄、MBTI 十六型 + 四维滑块、性格预设、人物形象
- 🧬 **深度学习人设** —— 首次创建即生成专属人格，编辑时增量更新不「失忆」
- 💞 **关系系统** —— 12 种关系预设，亲密度随聊天动态涨跌
- 🎭 **情绪系统** —— 多种情绪驱动回复节奏与语气
- 📖 **剧情模式** —— 小说式多段剧情输出
- 🌙 **作息模拟** —— 角色会睡觉、会醒，醒来自然解释漏回的消息
- 🧠 **记忆系统** —— 主线与细节分层记忆，聊得越久越懂你

### 🔌 自定义模型（开源版核心）
- 五类模型 —— **语言 / 生图 / 识图 / TTS / ASR** —— 全部支持自定义
- 任意 **OpenAI 兼容** API 均可接入（名称 / Key / 模型 ID / 地址 / 备注）
- 内置少量作者自用模型（不保证在线，可适度体验），相关 Key 不随源码分发

### 🎨 界面与体验
- 💎 **液态玻璃** —— 磨砂玻璃质感 UI，可开关「高级材质」
- 🔤 **多字体** —— 内置思源黑体、宋韵朗黑等多款字体，中英文混排
- 🌗 **多主题** —— 浅色 / 深色 / 黑色 / 跟随系统
- 🌐 **多语言** —— 简体中文 / 繁體中文 / English
- ✨ **丝滑动画** —— 思考光球、消息入场、删除粒子等动效

---

## 📥 下载

前往 [Releases](https://github.com/Belate-stay/FreeChat/releases) 下载最新 APK。

> 要求 Android 8.0（API 26）及以上。

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
app/src/main/java/com/freechat/
├── MainActivity.kt            # 单 Activity 宿主 + 页面路由
├── FreeChatApp.kt             # Application
├── ReplyService.kt            # 前台服务保活 + 系统通知
├── viewmodel/
│   └── ChatViewModel.kt       # 核心逻辑：对话 / API / 记忆 / 角色
├── data/
│   ├── SettingsRepository.kt  # DataStore 持久化
│   ├── TtsController.kt       # TTS 语音合成
│   ├── MiMoAsr.kt             # ASR 语音识别
│   └── MemoryManager.kt       # 记忆检索
├── model/                     # 数据模型（Conversation / Message / CharacterProfile …）
├── ui/
│   ├── screens/               # 页面（聊天 / 设置 / 角色 / 模型 / 协议 / 更新日志 …）
│   ├── components/            # 组件（气泡 / 输入框 / 抽屉 / Markdown …）
│   └── theme/                 # 主题（色彩 / 字体 / 磨砂玻璃）
└── util/                      # 工具（Office 解析与生成）
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
| 架构 | MVVM + StateFlow（单 Activity） |

---

## 📄 开源与免责声明

- 内置模型为作者自用 API，**不保证随时在线、额度有限**，仅供体验，请勿依赖。
- 本项目不采集、不上传对话数据，所有内容仅保存在本地设备。
- 首次启动需阅读并同意《用户协议与免责声明》。

---

## 👤 作者

[Belate](https://github.com/Belate-stay) · [GitHub 仓库](https://github.com/Belate-stay/FreeChat)

---

## 📜 许可证

本项目采用 [MIT License](LICENSE) 开源，Copyright © 2026 [Belate-stay](https://github.com/Belate-stay)
