package ai.milf.client.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MilfAccessibilityService : AccessibilityService(), DeviceActions {
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) {
            instance = null
        }
        super.onDestroy()
    }

    override suspend fun tap(x: Int, y: Int) {
        dispatchPath(durationMs = 50L) {
            moveTo(x.toFloat(), y.toFloat())
            lineTo(x.toFloat(), y.toFloat())
        }
    }

    override suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long) {
        dispatchPath(durationMs = durationMs) {
            moveTo(x1.toFloat(), y1.toFloat())
            lineTo(x2.toFloat(), y2.toFloat())
        }
    }

    override suspend fun inputText(text: String, clear: Boolean): Boolean {
        val root = rootForTargetApp() ?: return false
        return try {
            val node = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
            try {
                if (clear) {
                    node.performAction(
                        AccessibilityNodeInfo.ACTION_SET_TEXT,
                        Bundle().apply {
                            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
                        }
                    )
                }
                node.performAction(
                    AccessibilityNodeInfo.ACTION_SET_TEXT,
                    Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                    }
                )
            } finally {
                node.recycleUnlessSameAs(root)
            }
        } finally {
            root.recycle()
        }
    }

    override suspend fun pressButton(button: String): Boolean = when (button) {
        "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
        "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
        "enter" -> pressEnterOnFocusedInput()

        else -> false
    }

    override suspend fun startApp(packageName: String, activity: String?): String {
        val intent = if (activity != null) {
            Intent().setClassName(packageName, activity)
        } else {
            packageManager.getLaunchIntentForPackage(packageName)
                ?: throw IllegalArgumentException("No launch intent for package: $packageName")
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        return packageName
    }

    override suspend fun getApps(includeSystem: Boolean): List<Map<String, String>> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(launcherIntent, 0)
            .asSequence()
            .filter { info ->
                includeSystem ||
                    (info.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0
            }
            .map { info ->
                mapOf(
                    "package" to info.activityInfo.packageName,
                    "label" to info.loadLabel(packageManager).toString()
                )
            }
            .distinctBy { it["package"] }
            .sortedBy { it["label"]?.lowercase().orEmpty() }
            .toList()
    }

    override suspend fun screenshot(hideOverlay: Boolean): String =
        ScreenshotCapture.captureBase64Png(this)

    override suspend fun getUiTree(): Map<String, Any?> {
        val metrics = resources.displayMetrics
        val root = rootForTargetApp() ?: return UiTreeSerializer.serialize(
            null,
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels
        )
        return try {
            UiTreeSerializer.serialize(
                root,
                screenWidth = metrics.widthPixels,
                screenHeight = metrics.heightPixels
            )
        } finally {
            root.recycle()
        }
    }

    private fun rootForTargetApp(): AccessibilityNodeInfo? {
        val candidates = windows.mapNotNull { window ->
            val root = window.root ?: return@mapNotNull null
            AccessibilityRootCandidate(
                root = root,
                packageName = root.packageName?.toString().orEmpty(),
                type = window.type,
                isActive = window.isActive
            )
        }
        val selected = AccessibilityRootSelector.select(candidates, packageName)
        candidates.forEach { candidate ->
            if (candidate.root !== selected) {
                candidate.root.recycle()
            }
        }
        return selected ?: rootInActiveWindow
    }

    private fun pressEnterOnFocusedInput(): Boolean {
        val root = rootInActiveWindow ?: return false
        return try {
            val node = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
            try {
                node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
            } finally {
                node.recycleUnlessSameAs(root)
            }
        } finally {
            root.recycle()
        }
    }

    private suspend fun dispatchPath(
        durationMs: Long,
        build: Path.() -> Unit
    ) = suspendCancellableCoroutine { continuation ->
        val gesture = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(
                    Path().apply(build),
                    0L,
                    durationMs.coerceAtLeast(1L)
                )
            )
            .build()
        val accepted = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (continuation.isActive) {
                        continuation.resume(Unit)
                    }
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(IllegalStateException("Gesture cancelled"))
                    }
                }
            },
            null
        )
        if (!accepted && continuation.isActive) {
            continuation.resumeWithException(IllegalStateException("Gesture dispatch was not accepted"))
        }
    }

    companion object {
        @Volatile
        var instance: MilfAccessibilityService? = null
            private set
    }
}

private fun AccessibilityNodeInfo.recycleUnlessSameAs(other: AccessibilityNodeInfo) {
    if (this !== other) {
        recycle()
    }
}
