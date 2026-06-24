package ai.milf.client.overlay

import ai.milf.client.session.SeniorUiState
import ai.milf.client.session.SeniorUxScreen
import ai.milf.client.ui.SeniorOverlayUi
import android.content.Context
import android.graphics.PixelFormat
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

class OverlayWindowController(
    private val context: Context,
    private val callbacks: Callbacks
) : LifecycleOwner, SavedStateRegistryOwner {
    interface Callbacks {
        fun onBubbleTap()
        fun onStopListening()
        fun onApprove()
        fun onDeny()
        fun onSpeakDecision()
        fun onWatchModeChange(enabled: Boolean)
        fun onRetry()
        fun onCallBuyer()
    }

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private var composeView: ComposeView? = null
    private var params: WindowManager.LayoutParams? = null
    private var removed = false
    private var collapsedX = dp(24)
    private var collapsedY = dp(240)
    private var currentState = SeniorUiState()

    override val lifecycle: Lifecycle = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry = savedStateController.savedStateRegistry

    fun show(initialState: SeniorUiState) {
        if (composeView != null) {
            update(initialState)
            return
        }

        removed = false
        currentState = initialState
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        val view = ComposeView(context).also {
            it.setViewTreeLifecycleOwner(this)
            it.setViewTreeSavedStateRegistryOwner(this)
            it.setOnTouchListener(DragTouchListener())
        }
        val initialParams = paramsFor(initialState)
        composeView = view
        params = initialParams
        view.setOverlayContent(initialState)
        windowManager.addView(view, initialParams)
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
        windowManager.updateViewLayout(view, current)
        view.setOverlayContent(state)
    }

    fun remove() {
        val view = composeView ?: return
        composeView = null
        params = null
        removed = true
        if (view.isAttachedToWindow) {
            windowManager.removeView(view)
        }
        view.disposeComposition()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }

    private fun ComposeView.setOverlayContent(state: SeniorUiState) {
        setContent {
            SeniorOverlayUi(
                state = state,
                onBubbleTap = callbacks::onBubbleTap,
                onStopListening = callbacks::onStopListening,
                onApprove = callbacks::onApprove,
                onDeny = callbacks::onDeny,
                onSpeakDecision = callbacks::onSpeakDecision,
                onWatchModeChange = callbacks::onWatchModeChange,
                onRetry = callbacks::onRetry,
                onCallBuyer = callbacks::onCallBuyer
            )
        }
    }

    private fun paramsFor(state: SeniorUiState): WindowManager.LayoutParams {
        val expanded = isExpanded(state)
        return WindowManager.LayoutParams(
            if (expanded) WindowManager.LayoutParams.MATCH_PARENT else dp(96),
            if (expanded) WindowManager.LayoutParams.MATCH_PARENT else dp(96),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = if (expanded) Gravity.CENTER else Gravity.TOP or Gravity.END
            x = if (expanded) 0 else collapsedX
            y = if (expanded) 0 else collapsedY
        }
    }

    private fun isExpanded(state: SeniorUiState): Boolean =
        state.screen != SeniorUxScreen.Idle &&
            (state.screen != SeniorUxScreen.Working || state.watchMode || state.demoMode)

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
            if (current.width == WindowManager.LayoutParams.MATCH_PARENT) return false

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
                    collapsedX = startX - deltaX.toInt()
                    collapsedY = startY + deltaY.toInt()
                    current.x = collapsedX
                    current.y = collapsedY
                    if (view.isAttachedToWindow) {
                        windowManager.updateViewLayout(view, current)
                    }
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    if (!dragging) {
                        view.performClick()
                        if (currentState.screen == SeniorUxScreen.Working) {
                            callbacks.onWatchModeChange(true)
                        } else {
                            callbacks.onBubbleTap()
                        }
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
}
