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
