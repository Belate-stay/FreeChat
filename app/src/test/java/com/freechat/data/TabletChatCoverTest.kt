package com.freechat.data

import com.freechat.ui.adaptive.TabletChatCover
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class TabletChatCoverTest {
    @Test fun reselectingCurrentChatCancelsAnOlderSuspendedSwitch() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val capture = CompletableDeferred<Unit>()
            val cover = TabletChatCover(scope)
            cover.contentKey = "A"
            cover.capture = { capture.await(); null }
            val selected = mutableListOf<String>()
            cover.switchTo("B") { selected += "B"; true }
            cover.switchTo("A") { selected += "A"; true }
            capture.complete(Unit)
            yield()
            assertEquals("The latest click wins, even if GPU capture has not completed", listOf("A"), selected)
        } finally { scope.cancel() }
    }
}
