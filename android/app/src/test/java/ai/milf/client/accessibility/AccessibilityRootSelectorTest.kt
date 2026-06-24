package ai.milf.client.accessibility

import android.view.accessibility.AccessibilityWindowInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class AccessibilityRootSelectorTest {
    @Test
    fun prefersUnderlyingAppWindowOverMilfOverlay() {
        val selected = AccessibilityRootSelector.select(
            candidates = listOf(
                AccessibilityRootCandidate(
                    root = "milf",
                    packageName = "ai.milf.client",
                    type = AccessibilityWindowInfo.TYPE_APPLICATION,
                    isActive = true
                ),
                AccessibilityRootCandidate(
                    root = "whatsapp",
                    packageName = "com.whatsapp",
                    type = AccessibilityWindowInfo.TYPE_APPLICATION,
                    isActive = false
                )
            ),
            ownPackageName = "ai.milf.client"
        )

        assertEquals("whatsapp", selected)
    }

    @Test
    fun fallsBackToOwnActiveWindowWhenNoOtherAppWindowExists() {
        val selected = AccessibilityRootSelector.select(
            candidates = listOf(
                AccessibilityRootCandidate(
                    root = "milf",
                    packageName = "ai.milf.client",
                    type = AccessibilityWindowInfo.TYPE_APPLICATION,
                    isActive = true
                )
            ),
            ownPackageName = "ai.milf.client"
        )

        assertEquals("milf", selected)
    }
}
