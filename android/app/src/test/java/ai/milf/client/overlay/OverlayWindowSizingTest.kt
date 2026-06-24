package ai.milf.client.overlay

import ai.milf.client.session.SeniorUiState
import ai.milf.client.session.SeniorUxScreen
import android.view.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayWindowSizingTest {
    @Test
    fun expandedWindowUsesRailBoundsSoOutsideTapsReachApps() {
        assertEquals(312, OverlayWindowSizing.expandedWidthPx(screenWidthPx = 360, density = 1f))
        assertEquals(560, OverlayWindowSizing.expandedWidthPx(screenWidthPx = 900, density = 1f))
        assertEquals(62, OverlayWindowSizing.expandedHeightPx(density = 1f))
        assertEquals(22, OverlayWindowSizing.expandedBottomOffsetPx(density = 1f))
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
    fun expandedWindowDoesNotTrapTouchesOutsideRailBounds() {
        val flags = OverlayWindowSizing.windowFlags(
            SeniorUiState(screen = SeniorUxScreen.Idle, isCollapsed = false)
        )

        assertEquals(
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        )
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
