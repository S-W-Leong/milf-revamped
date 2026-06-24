package ai.milf.client.accessibility

import ai.milf.client.protocol.Action
import ai.milf.client.security.ActionPolicy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        val policy = ActionPolicy(clock = { 1_000L }).also { it.recordApproval() }
        val dispatcher = ActionDispatcher(fake, policy)

        val result = dispatcher.dispatch(Action("t1", "tap", mapOf("x" to 10, "y" to 20)))

        assertEquals(true, result.ok)
        assertEquals("t1", result.id)
        assertEquals(listOf("tap:10,20"), fake.calls)
    }

    @Test
    fun nullDeviceFailsClearly() = runTest {
        val policy = ActionPolicy(clock = { 1_000L }).also { it.recordApproval() }
        val dispatcher = ActionDispatcher(null, policy)

        val result = dispatcher.dispatch(Action("n1", "tap", mapOf("x" to 10, "y" to 20)))

        assertEquals(false, result.ok)
        assertEquals("n1", result.id)
        assertEquals("Accessibility service is not enabled", result.error)
    }

    @Test
    fun tapAcceptsJsonNumberTypes() = runTest {
        val fake = FakeDeviceActions()
        val policy = ActionPolicy(clock = { 1_000L }).also { it.recordApproval() }
        val dispatcher = ActionDispatcher(fake, policy)

        val result = dispatcher.dispatch(Action("t2", "tap", mapOf("x" to 10L, "y" to 20.0)))

        assertEquals(true, result.ok)
        assertEquals(listOf("tap:10,20"), fake.calls)
    }

    @Test
    fun blockedSensitiveActionDoesNotCallDeviceActionsOrEchoArguments() = runTest {
        val fake = FakeDeviceActions()
        val dispatcher = ActionDispatcher(fake, ActionPolicy(clock = { 1_000L }))

        val result = dispatcher.dispatch(
            Action("b1", "input_text", mapOf("text" to "secret passcode", "clear" to true))
        )

        assertEquals(false, result.ok)
        assertEquals("b1", result.id)
        assertEquals("Local confirmation is required before this action", result.error)
        assertEquals(emptyList<String>(), fake.calls)
        assertFalse(result.error.orEmpty().contains("secret passcode"))
    }

    @Test
    fun getUiTreeRunsBeforeConfirmation() = runTest {
        val fake = FakeDeviceActions()
        val dispatcher = ActionDispatcher(fake, ActionPolicy(clock = { 1_000L }))

        val result = dispatcher.dispatch(Action("u1", "get_ui_tree", emptyMap()))

        assertEquals(true, result.ok)
        assertEquals(mapOf("nodes" to emptyList<Map<String, Any?>>()), result.result)
        assertEquals(listOf("get_ui_tree"), fake.calls)
    }
}

private class FakeDeviceActions : DeviceActions {
    val calls = mutableListOf<String>()

    override suspend fun tap(x: Int, y: Int) {
        calls += "tap:$x,$y"
    }

    override suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long) {
        calls += "swipe:$x1,$y1,$x2,$y2,$durationMs"
    }

    override suspend fun inputText(text: String, clear: Boolean): Boolean {
        calls += "input_text:$text,$clear"
        return true
    }

    override suspend fun pressButton(button: String): Boolean {
        calls += "press_button:$button"
        return true
    }

    override suspend fun startApp(packageName: String, activity: String?): String {
        calls += "start_app:$packageName,$activity"
        return packageName
    }

    override suspend fun screenshot(hideOverlay: Boolean): String {
        calls += "screenshot:$hideOverlay"
        return "png"
    }

    override suspend fun getUiTree(): Map<String, Any?> {
        calls += "get_ui_tree"
        return mapOf("nodes" to emptyList<Map<String, Any?>>())
    }
}
