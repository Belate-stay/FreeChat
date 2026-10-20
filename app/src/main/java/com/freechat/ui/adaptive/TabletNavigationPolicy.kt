package com.freechat.ui.adaptive

import kotlin.math.min

data class TabletCameraFrame(
    val listX: Float,
    val chatX: Float,
    val chatWidth: Float,
    val auxiliaryX: Float,
    val dividerX: Float,
)

data class TabletFloatingBounds(val x: Float, val y: Float, val width: Float, val height: Float)

/** Geometry is in dp. The same Chat edge positions both the auxiliary page and its divider. */
object TabletNavigationPolicy {
    /** Fullscreen and auxiliary navigation change placement, never the Chat's text constraints. */
    fun stableChatFrame(width: Float, supporting: Boolean, fullscreen: Boolean): TabletCameraFrame {
        val panes = TabletLayoutPolicy.panes(width)
        val chatX = when {
            fullscreen -> (width - panes.detailWidth) / 2f
            supporting -> if (panes.split) 0f else -panes.detailWidth - 1f
            panes.split -> panes.listWidth + panes.gap
            else -> 0f
        }
        val edge = chatX + panes.detailWidth
        return TabletCameraFrame(
            if (supporting || fullscreen || !panes.listVisible) -panes.listWidth - 1f else 0f,
            chatX, panes.detailWidth,
            if (supporting && !fullscreen) edge + 1f else width,
            edge,
        )
    }

    fun camera(width: Float, listWidth: Float, targetChatWidth: Float, progress: Float): TabletCameraFrame {
        val homeX = listWidth + 1f
        val homeWidth = width - homeX
        return cameraBetween(0f, -homeX, homeX, homeWidth, 0f, targetChatWidth, progress)
    }

    fun cameraBetween(
        listStartX: Float, listEndX: Float,
        chatStartX: Float, chatStartWidth: Float,
        chatEndX: Float, chatEndWidth: Float, progress: Float,
    ): TabletCameraFrame {
        val p = progress.coerceIn(0f, 1f)
        val chatX = chatStartX + (chatEndX - chatStartX) * p
        val chatWidth = chatStartWidth + (chatEndWidth - chatStartWidth) * p
        val edge = chatX + chatWidth
        return TabletCameraFrame(listStartX + (listEndX - listStartX) * p, chatX, chatWidth, edge + p, edge)
    }

    fun floatingBounds(width: Float, height: Float, side: TabletPane): TabletFloatingBounds {
        val inset = 24f
        val availableHeight = (height - inset * 2f).coerceAtLeast(1f)
        val cardWidth = min(420f, min((width - inset * 2f).coerceAtLeast(1f), availableHeight * .57f))
        val cardHeight = min(availableHeight, cardWidth * 1.9f)
        return TabletFloatingBounds(
            if (side == TabletPane.LIST) inset else width - inset - cardWidth,
            (height - cardHeight) / 2f, cardWidth, cardHeight,
        )
    }

    fun paneTravel(side: TabletPane, entering: Boolean): Float =
        if (side == TabletPane.DETAIL) 1f else -1f

    /** A cover moves the new page; Back removes that same page along its entry path. */
    fun pageTravel(side: TabletPane, entering: Boolean, back: Boolean): Float =
        if (entering == back) 0f else paneTravel(side, entering)
}
