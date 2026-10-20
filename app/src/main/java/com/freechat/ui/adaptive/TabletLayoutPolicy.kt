package com.freechat.ui.adaptive

enum class TabletPane { LIST, DETAIL }

data class TabletPanes(
    val listWidth: Float,
    val detailWidth: Float,
    val gap: Float,
    val listVisible: Boolean,
    val detailVisible: Boolean,
) {
    val split get() = listVisible && detailVisible
}

/** Device class is stable across rotation; pane count follows the usable window instead. */
object TabletLayoutPolicy {
    fun isTablet(maximumWindowSmallestDp: Int) = maximumWindowSmallestDp >= 600

    fun fullscreenAfterResize(wasNarrow: Boolean, isNarrow: Boolean, fullscreen: TabletPane?): TabletPane? =
        if (wasNarrow && !isNarrow) null else fullscreen

    fun panes(
        width: Float,
        balanced: Boolean = false,
        fullscreen: TabletPane? = null,
        focused: TabletPane = TabletPane.DETAIL,
    ): TabletPanes {
        val gap = 1f
        val list = if (balanced) (width * .48f).coerceIn(280f, 640f)
            else (width * .30f).coerceIn(220f, 340f)
        val chosen = fullscreen ?: focused.takeIf { width < if (balanced) 720f else 600f }
        return TabletPanes(
            listWidth = if (chosen == TabletPane.LIST) width else list,
            detailWidth = if (chosen == TabletPane.DETAIL) width else (width - list - gap).coerceAtLeast(1f),
            gap = gap,
            listVisible = chosen != TabletPane.DETAIL,
            detailVisible = chosen != TabletPane.LIST,
        )
    }
}
