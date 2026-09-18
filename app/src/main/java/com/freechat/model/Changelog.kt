package com.freechat.model

/**
 * 更新日志：软件内只展示「正式版（Version）」的更新，内容由开发者提供原文直接粘贴。
 * 所有版本（含 Beta）的完整更新日志见桌面 Markdown（D:\Desktop\FreeChat-Changelog.md）。
 */
data class ChangelogSection(
    val title: String,          // 更新方向概括
    val items: List<String>     // 该方向下的更新项
)

data class ChangelogEntry(
    val version: String,
    val date: String,
    val sections: List<ChangelogSection>
)

object ChangelogData {
    val entries: List<ChangelogEntry> = listOf(
        ChangelogEntry(
            version = "Version 1.0.64",
            date = "2026-09-19",
            sections = listOf(
                ChangelogSection("拟人优化", listOf(
                    "新增 “配角”、“规则”以及“用户形象”角色形象设定项，精细化角色与世界的建立。",
                    "新增 “参考原型”功能，AI搜索学习知名角色的相关人设。",
                    "新增 “剧情补足”对话模式，小说长文本的形式创作剧情发展，提供更完整的创作体验。",
                    "新增 “深度推演”机制，流程化审问确认，提高回复质量。",
                    "新增 “AI创造力”选项，定制回复温度，用户和AI对剧情把控度可量化。",
                    "新增 微信聊天模式下的“主动智能”功能，本地延长API缓存，测试功能。",
                    "优化 不同模式间的回复风格限制与正则，提升回复的统一度与准确性。"
                )),
                ChangelogSection("渲染优化", listOf(
                    "新增 流光炫彩背景效果，引入动态渐变光晕，提升观感。",
                    "优化 非悬浮卡片效果，引入“拟物态”设计风格，提升观感。",
                    "优化 高级材质下的页面渲染统一性，多个卡片适配磨砂玻璃。",
                    "优化 部分页面下的过渡动画，提升流畅度。"
                )),
                ChangelogSection("新增功能", listOf(
                    "新增 内置“Claude风格助理”，仿照Claude的输出风格提供高效帮助。",
                    "新增 “关于作者”的详细介绍与反馈方式。",
                    "新增 账号系统与网页版对话，支持数据同步（API Key留在本地不同步）。",
                    "新增 登录状态下控制账号登录设备的功能。"
                )),
                ChangelogSection("体验优化", listOf(
                    "优化 部分场景的交互操作逻辑。",
                    "优化 多页面长按进入多选批量操作。",
                    "优化 设置、新规则等页面的排版布局和字体显示。",
                    "优化 全屏输入的主动与被动触发方式。",
                    "优化 提示词时键盘与页面高度的显示。",
                    "优化 思考过程的显示逻辑。"
                )),
                ChangelogSection("漏洞修补", listOf(
                    "修复 新规则因逻辑错误导致的设置项不生效的问题。",
                    "修复 MiMo-V2.5-Ultraspeed模型因小米服务收回而导致API失效的问题。",
                    "修复 联网搜索因SerpAPI额度用尽而导致的无法联网搜索问题。",
                    "修复 语言随机乱跳的问题。",
                    "修复 服务器端同步异常的问题。",
                    "修复 用户协议反复闪跳的问题。"
                )),
                ChangelogSection("其他更新", listOf(
                    "更新 用户协议与免责声明。"
                ))
            )
        ),
        ChangelogEntry(
            version = "Version 1.0.18",
            date = "2026-09-10",
            sections = listOf(
                ChangelogSection("新增功能", listOf(
                    "新增 收藏系统，消息收藏后可统一查看。",
                    "新增 角色设定可导入/导出，可一键分享角色或采用已配置的角色。",
                    "新增 消息分享功能，可以jpg/Markdown的格式保存或分享。"
                )),
                ChangelogSection("体验优化", listOf(
                    "优化 新标题命名逻辑",
                    "优化 全拼输入的唤出条件",
                    "优化 AI回复时聊天位置驻停",
                    "优化 高质量检索回复下的角色理解力、AI推理能力以及记忆力，提升AI回复质量"
                )),
                ChangelogSection("渲染优化", listOf(
                    "优化 表格内部纵向属性对齐，提升可读性。",
                    "优化 统一高级材质下的视觉效果。"
                )),
                ChangelogSection("漏洞修补", listOf(
                    "修复 高级材质下标题栏无隔挡的bug",
                    "修复 URL路径自动配全导致路径失效的bug",
                    "修复 部分场景下识图模型失效的bug"
                ))
            )
        ),
        ChangelogEntry(
            version = "Version 1.0.01",
            date = "2026-09-01",
            sections = listOf(
                ChangelogSection("新增功能", listOf(
                    "新增 高质量回复功能，开启后记忆感知由'AI概括'改为'原文摘录'，提高优先级，减少AI幻觉并提升记忆力。使用户可以在\"高性能\"与\"低收费\"之间选择。"
                )),
                ChangelogSection("其他更新", listOf(
                    "新增 开源声明提示。"
                ))
            )
        ),
        ChangelogEntry(
            version = "Version 1.0",
            date = "2026-09-01",
            sections = listOf(
                ChangelogSection("体验优化", listOf(
                    "重编 Debug转为Release，提升响应速度，减少启动掉帧。",
                    "优化 内置字体后台预加载，减少渲染卡顿。",
                    "优化 app内过度动画，提升流畅度。"
                )),
                ChangelogSection("漏洞修补", listOf(
                    "修复 全屏输入展开后光标位置重置的bug。"
                ))
            )
        )
    )
}
