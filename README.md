# FreeChat

自由对话，自由选择 — 莫兰迪暖棕配色，Copilot 风格 AI 聊天 Android 应用。

## 功能

- 💬 **对话集** — 左侧滑出抽屉，历史对话保存/切换/删除
- 🎨 **深浅主题** — 默认跟随系统，设置中可固定浅色/深色
- 🤖 **模型切换** — DeepSeek V4-Pro / V4-Flash / R1，在设置中选择
- 🌾 **莫兰迪配色** — 低饱和暖棕，简约高级舒适
- ✨ **丝滑动画** — 抽屉滑入、页面切换、按钮状态都有过渡动画

## 项目结构（20 个源文件）

```
app/src/main/java/com/freechat/
├── FreeChatApp.kt
├── MainActivity.kt                # 抽屉宿主 + 页面路由
├── model/
│   ├── Conversation.kt            # 对话历史数据
│   ├── Message.kt                 # 消息数据
│   ├── ModelInfo.kt               # AI 模型信息
│   └── ThemeMode.kt               # 主题模式枚举
├── data/
│   ├── ApiConfig.kt
│   └── SettingsRepository.kt      # 模型+主题持久化
├── viewmodel/
│   └── ChatViewModel.kt           # 核心逻辑 + API 调用 + 对话管理
├── ui/
│   ├── theme/
│   │   ├── Color.kt               # 莫兰迪浅/深完整色板
│   │   ├── Theme.kt               # 双主题 Material3
│   │   └── Type.kt                # 字体排版
│   ├── screens/
│   │   ├── ChatScreen.kt          # 聊天主页面
│   │   └── SettingsScreen.kt      # 模型+主题设置
│   └── components/
│       ├── ChatBubble.kt          # 聊天气泡
│       ├── ChatInput.kt           # 底部输入栏
│       ├── DrawerContent.kt       # 侧滑抽屉内容
│       ├── SuggestionChips.kt     # 建议快捷词条
│       ├── TypingIndicator.kt     # AI 打字动画
│       └── WelcomeHeader.kt       # 欢迎头
```
