package ai.milf.client.overlay

import ai.milf.client.session.SeniorUiState
import ai.milf.client.ui.SeniorOverlayUi
import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlin.math.abs

internal object OverlayWindowSizing {
    fun expandedWidthPx(): Int = WindowManager.LayoutParams.MATCH_PARENT
    fun expandedHeightPx(): Int = WindowManager.LayoutParams.MATCH_PARENT
    fun collapsedSizeDp(): Int = 66

    fun railWidthPx(screenWidthPx: Int, density: Float): Int {
        val marginPx = (24 * density).toInt()
        val maxWidthPx = (560 * density).toInt()
        return minOf(screenWidthPx - marginPx * 2, maxWidthPx)
    }
}

class OverlayWindowController(
    private val context: Context,
    private val callbacks: Callbacks
) {
    interface Callbacks {
        fun onMicTap()
        fun onCommandTextChange(text: String)
        fun onSubmitText()
        fun onRunStop()
        fun onExitAgent()
        fun onOutsideExpandedTap()
        fun onExpandOverlay()
        fun onApprove()
        fun onDeny()
        fun onTransientMessageShown()
    }

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var composeView: ComposeView? = null
    private var params: WindowManager.LayoutParams? = null
    private var windowOwner: WindowOwner? = null
    private var removed = false
    private var collapsedX = dp(24)
    private var collapsedY = dp(240)
    private var currentState = SeniorUiState()

    fun show(initialState: SeniorUiState) {
        if (composeView != null) {
            update(initialState)
            return
        }

        removed = false
        currentState = initialState
        val owner = WindowOwner().also {
            it.restore()
            it.start()
        }
        val view = ComposeView(context).also {
            it.setViewTreeLifecycleOwner(owner)
            it.setViewTreeSavedStateRegistryOwner(owner)
            it.setOnTouchListener(DragTouchListener())
        }
        val initialParams = paramsFor(initialState)
        composeView = view
        params = initialParams
        windowOwner = owner
        view.setOverlayContent(initialState)
        addViewSafely(view, initialParams)
    }

    fun update(state: SeniorUiState) {
        val view = composeView ?: return
        if (removed || !view.isAttachedToWindow) return

        currentState = state
        val current = params ?: return
        val next = paramsFor(state)
        current.width = next.width
        current.height = next.height
        current.gravity = next.gravity
        current.x = next.x
        current.y = next.y
        current.flags = next.flags
        current.format = next.format
        params = current
        if (!updateViewLayoutSafely(view, current)) return
        view.setOverlayContent(state)
    }

    fun remove() {
        val view = composeView ?: return
        if (view.isAttachedToWindow) {
            removeViewSafely(view)
        }
        clearWindowState(view)
    }

    private fun addViewSafely(view: ComposeView, layoutParams: WindowManager.LayoutParams) {
        try {
            windowManager.addView(view, layoutParams)
        } catch (exception: RuntimeException) {
            Log.w(TAG, "Unable to add overlay window", exception)
            clearWindowState(view)
        }
    }

    private fun updateViewLayoutSafely(view: ComposeView, layoutParams: WindowManager.LayoutParams): Boolean {
        return try {
            windowManager.updateViewLayout(view, layoutParams)
            true
        } catch (exception: RuntimeException) {
            Log.w(TAG, "Unable to update overlay window", exception)
            clearWindowState(view)
            false
        }
    }

    private fun removeViewSafely(view: ComposeView) {
        try {
            windowManager.removeView(view)
        } catch (exception: RuntimeException) {
            Log.w(TAG, "Unable to remove overlay window", exception)
        }
    }

    private fun clearWindowState(view: ComposeView) {
        if (composeView === view) {
            composeView = null
        }
        params = null
        removed = true
        windowOwner?.destroy()
        windowOwner = null
        view.disposeComposition()
    }

    private fun ComposeView.setOverlayContent(state: SeniorUiState) {
        setContent {
            SeniorOverlayUi(
                state = state,
                onMicTap = callbacks::onMicTap,
                onCommandTextChange = callbacks::onCommandTextChange,
                onSubmitText = callbacks::onSubmitText,
                onRunStop = callbacks::onRunStop,
                onExitAgent = callbacks::onExitAgent,
                onOutsideExpandedTap = callbacks::onOutsideExpandedTap,
                onExpandOverlay = callbacks::onExpandOverlay,
                onApprove = callbacks::onApprove,
                onDeny = callbacks::onDeny,
                onTransientMessageShown = callbacks::onTransientMessageShown
            )
        }
    }

    private fun paramsFor(state: SeniorUiState): WindowManager.LayoutParams {
        val expanded = !state.isCollapsed
        return WindowManager.LayoutParams(
            if (expanded) OverlayWindowSizing.expandedWidthPx() else dp(OverlayWindowSizing.collapsedSizeDp()),
            if (expanded) OverlayWindowSizing.expandedHeightPx() else dp(OverlayWindowSizing.collapsedSizeDp()),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = if (expanded) Gravity.TOP or Gravity.START else Gravity.TOP or Gravity.END
            x = if (expanded) 0 else collapsedX
            y = if (expanded) 0 else collapsedY
        }
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private inner class DragTouchListener : View.OnTouchListener {
        private var startX = 0
        private var startY = 0
        private var downRawX = 0f
        private var downRawY = 0f
        private var dragging = false
        private val touchSlop = dp(8)

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            val current = params ?: return false
            if (!currentState.isCollapsed) return false

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = current.x
                    startY = current.y
                    downRawX = event.rawX
                    downRawY = event.rawY
                    dragging = false
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - downRawX
                    val deltaY = event.rawY - downRawY
                    dragging = dragging || abs(deltaX) > touchSlop || abs(deltaY) > touchSlop
                    if (!dragging) return true
                    collapsedX = startX - deltaX.toInt()
                    collapsedY = startY + deltaY.toInt()
                    current.x = collapsedX
                    current.y = collapsedY
                    if (view.isAttachedToWindow) {
                        updateViewLayoutSafely(view as ComposeView, current)
                    }
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    if (!dragging) {
                        view.performClick()
                        callbacks.onExpandOverlay()
                    }
                    dragging = false
                    return true
                }

                MotionEvent.ACTION_CANCEL -> {
                    dragging = false
                    return true
                }
            }
            return false
        }
    }

    private companion object {
        const val TAG = "OverlayWindowController"
    }
}

private class WindowOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private var destroyed = false
    private var restored = false

    override val lifecycle: Lifecycle = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry = savedStateController.savedStateRegistry

    fun restore() {
        if (!restored) {
            savedStateController.performRestore(null)
            restored = true
        }
    }

    fun start() {
        if (!destroyed) {
            lifecycleRegistry.currentState = Lifecycle.State.STARTED
        }
    }

    fun destroy() {
        if (!destroyed) {
            destroyed = true
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        }
    }
}
