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
