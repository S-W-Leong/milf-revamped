package ai.milf.client.overlay

import ai.milf.client.session.SeniorUiState
import ai.milf.client.session.SeniorUxScreen
import android.view.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayWindowSizingTest {
    @Test
    fun expandedWindowCoversScreenForOutsideTapCatcher() {
        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, OverlayWindowSizing.expandedWidthPx())
        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, OverlayWindowSizing.expandedHeightPx())
    }

    @Test
    fun collapsedWindowUsesBubbleBounds() {
        assertEquals(66, OverlayWindowSizing.collapsedSizeDp())
    }

    @Test
    fun railWidthLeavesSideMargins() {
        assertEquals(312, OverlayWindowSizing.railWidthPx(screenWidthPx = 360, density = 1f))
        assertEquals(560, OverlayWindowSizing.railWidthPx(screenWidthPx = 900, density = 1f))
    }

    @Test
    fun expandedIdleWindowCanFocusInputForKeyboard() {
        val flags = OverlayWindowSizing.windowFlags(
            SeniorUiState(screen = SeniorUxScreen.Idle, isCollapsed = false)
        )

        assertEquals(0, flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
    }

    @Test
    fun collapsedWindowDoesNotTakeKeyboardFocus() {
        val flags = OverlayWindowSizing.windowFlags(
            SeniorUiState(screen = SeniorUxScreen.Idle, isCollapsed = true)
        )

        assertEquals(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        )
    }
}
