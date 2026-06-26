package ai.milf.client.accessibility

import ai.milf.client.protocol.Action
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionDispatcherTest {
    @Test
    fun unsupportedActionFails() = runTest {
        val dispatcher = ActionDispatcher(FakeDeviceActions())

        val result = dispatcher.dispatch(Action("1", "install_app", emptyMap()))

        assertEquals(false, result.ok)
        assertEquals("1", result.id)
        assertEquals("Unsupported action: install_app", result.error)
    }

    @Test
    fun tapCallsDeviceActions() = runTest {
        val fake = FakeDeviceActions()
        val dispatcher = ActionDispatcher(fake)

        val result = dispatcher.dispatch(Action("t1", "tap", mapOf("x" to 10, "y" to 20)))

        assertEquals(true, result.ok)
        assertEquals("t1", result.id)
        assertEquals(listOf("tap:10,20"), fake.calls)
    }

    @Test
    fun nullDeviceFailsClearly() = runTest {
        val dispatcher = ActionDispatcher(null)

        val result = dispatcher.dispatch(Action("n1", "tap", mapOf("x" to 10, "y" to 20)))

        assertEquals(false, result.ok)
        assertEquals("n1", result.id)
        assertEquals("Accessibility service is not enabled", result.error)
    }

    @Test
    fun tapAcceptsJsonNumberTypes() = runTest {
        val fake = FakeDeviceActions()
        val dispatcher = ActionDispatcher(fake)

        val result = dispatcher.dispatch(Action("t2", "tap", mapOf("x" to 10L, "y" to 20.0)))

        assertEquals(true, result.ok)
        assertEquals(listOf("tap:10,20"), fake.calls)
    }

    @Test
    fun screenshotWithHideOverlayRunsCaptureInsideOverlayGuard() = runTest {
        val fake = FakeDeviceActions()
        val overlay = FakeOverlayGuard()
        val dispatcher = ActionDispatcher(fake, overlay)

        val result = dispatcher.dispatch(Action("s1", "screenshot", mapOf("hide_overlay" to true)))

        assertEquals(true, result.ok)
        assertEquals("png", result.result)
        assertEquals(listOf("hide:start", "hide:end"), overlay.calls)
        assertEquals(listOf("screenshot:true"), fake.calls)
    }

    @Test
    fun getDateReturnsDeviceDate() = runTest {
        val dispatcher = ActionDispatcher(FakeDeviceActions())

        val result = dispatcher.dispatch(Action("d1", "get_date", emptyMap()))

        assertEquals(true, result.ok)
        assertEquals("d1", result.id)
        assertEquals("2026-06-24", result.result)
    }

    @Test
    fun getAppsReturnsInstalledApps() = runTest {
        val dispatcher = ActionDispatcher(FakeDeviceActions())

        val result = dispatcher.dispatch(Action("a1", "get_apps", mapOf("include_system" to false)))

        assertEquals(true, result.ok)
        assertEquals("a1", result.id)
        assertEquals(
            listOf(mapOf("package" to "com.whatsapp", "label" to "WhatsApp")),
            result.result
        )
    }

    @Test
    fun inputTextFailureReturnsFailedActionResult() = runTest {
        val dispatcher = ActionDispatcher(FakeDeviceActions(inputTextResult = false))

        val result = dispatcher.dispatch(Action("i1", "input_text", mapOf("text" to "Wei")))

        assertEquals(false, result.ok)
        assertEquals("i1", result.id)
        assertEquals("input_text failed", result.error)
    }

    @Test
    fun emptyUiTreeUsesMobileRunStateContract() {
        val state = UiTreeSerializer.serialize(null, screenWidth = 1080, screenHeight = 2400)

        assertTrue(state.containsKey("a11y_tree"))
        assertTrue(state.containsKey("phone_state"))
        assertTrue(state.containsKey("device_context"))
        assertEquals(
            mapOf("width" to 1080, "height" to 2400),
            (state["device_context"] as Map<*, *>)["screen_bounds"]
        )
    }
}

private class FakeDeviceActions(
    private val inputTextResult: Boolean = true
) : DeviceActions {
    val calls = mutableListOf<String>()

    override suspend fun tap(x: Int, y: Int) {
        calls += "tap:$x,$y"
    }

    override suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long) {
        calls += "swipe:$x1,$y1,$x2,$y2,$durationMs"
    }

    override suspend fun inputText(text: String, clear: Boolean): Boolean = inputTextResult

    override suspend fun pressButton(button: String): Boolean = true

    override suspend fun startApp(packageName: String, activity: String?): String = packageName

    override suspend fun screenshot(hideOverlay: Boolean): String {
        calls += "screenshot:$hideOverlay"
        return "png"
    }

    override suspend fun getUiTree(): Map<String, Any?> = emptyMap()

    override suspend fun getDate(): String = "2026-06-24"

    override suspend fun getApps(includeSystem: Boolean): List<Map<String, String>> =
        listOf(mapOf("package" to "com.whatsapp", "label" to "WhatsApp"))
}

private class FakeOverlayGuard : OverlayGuard {
    val calls = mutableListOf<String>()

    override suspend fun <T> hideWhile(block: suspend () -> T): T {
        calls += "hide:start"
        return try {
            block()
        } finally {
            calls += "hide:end"
        }
    }
}
