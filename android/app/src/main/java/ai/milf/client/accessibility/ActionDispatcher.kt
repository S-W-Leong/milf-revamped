package ai.milf.client.accessibility

import ai.milf.client.protocol.Action
import ai.milf.client.protocol.ActionResult
import java.time.LocalDate

interface DeviceActions {
    suspend fun tap(x: Int, y: Int)
    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long)
    suspend fun inputText(text: String, clear: Boolean): Boolean
    suspend fun pressButton(button: String): Boolean
    suspend fun startApp(packageName: String, activity: String?): String
    suspend fun getApps(includeSystem: Boolean): List<Map<String, String>>
    suspend fun screenshot(hideOverlay: Boolean): String
    suspend fun getUiTree(): Map<String, Any?>
    suspend fun getDate(): String = LocalDate.now().toString()
}

class ActionDispatcher(
    private val device: DeviceActions?
) {
    suspend fun dispatch(action: Action): ActionResult {
        val activeDevice = device
            ?: return ActionResult(
                id = action.id,
                ok = false,
                error = "Accessibility service is not enabled"
            )

        return runCatching {
            when (action.name) {
                "tap" -> {
                    activeDevice.tap(action.intArg("x"), action.intArg("y"))
                    null
                }

                "swipe" -> {
                    activeDevice.swipe(
                        x1 = action.intArg("x1"),
                        y1 = action.intArg("y1"),
                        x2 = action.intArg("x2"),
                        y2 = action.intArg("y2"),
                        durationMs = action.longArg("duration_ms", 1_000L)
                    )
                    null
                }

                "input_text" -> {
                    val inserted = activeDevice.inputText(
                        text = action.stringArg("text"),
                        clear = action.boolArg("clear", false)
                    )
                    if (!inserted) {
                        throw IllegalStateException("input_text failed")
                    }
                    true
                }

                "press_button" -> activeDevice.pressButton(action.stringArg("button"))

                "start_app" -> activeDevice.startApp(
                    packageName = action.stringArg("package"),
                    activity = action.nullableStringArg("activity")
                )

                "get_apps" -> activeDevice.getApps(
                    includeSystem = action.boolArg("include_system", true)
                )

                "screenshot" -> activeDevice.screenshot(action.boolArg("hide_overlay", true))

                "get_ui_tree" -> activeDevice.getUiTree()

                "get_date" -> activeDevice.getDate()

                else -> throw IllegalArgumentException("Unsupported action: ${action.name}")
            }
        }.fold(
            onSuccess = { result -> ActionResult(action.id, ok = true, result = result) },
            onFailure = { error ->
                ActionResult(
                    id = action.id,
                    ok = false,
                    error = error.message ?: error::class.java.simpleName
                )
            }
        )
    }
}

private fun Action.intArg(name: String): Int = when (val value = args[name]) {
    is Int -> value
    is Long -> value.toInt()
    is Double -> value.toInt()
    is Float -> value.toInt()
    is Number -> value.toInt()
    else -> throw IllegalArgumentException("Missing int arg: $name")
}

private fun Action.longArg(name: String, default: Long): Long = when (val value = args[name]) {
    null -> default
    is Long -> value
    is Int -> value.toLong()
    is Double -> value.toLong()
    is Float -> value.toLong()
    is Number -> value.toLong()
    else -> throw IllegalArgumentException("Invalid long arg: $name")
}

private fun Action.stringArg(name: String): String =
    args[name] as? String ?: throw IllegalArgumentException("Missing string arg: $name")

private fun Action.nullableStringArg(name: String): String? =
    args[name] as? String

private fun Action.boolArg(name: String, default: Boolean): Boolean =
    args[name] as? Boolean ?: default
