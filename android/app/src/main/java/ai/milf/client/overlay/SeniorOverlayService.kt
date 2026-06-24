package ai.milf.client.overlay

import ai.milf.client.MilfApplication
import ai.milf.client.audio.ConfirmationVoiceRecognizer
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.provider.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SeniorOverlayService : Service() {
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var window: OverlayWindowController? = null
    private var confirmationVoice: ConfirmationVoiceRecognizer? = null

    override fun onCreate() {
        super.onCreate()
        val controller = (application as MilfApplication).sessionController
        confirmationVoice = ConfirmationVoiceRecognizer(
            context = this,
            onText = controller::onConfirmationSpeech,
            onError = { }
        )
        window = OverlayWindowController(
            context = this,
            callbacks = object : OverlayWindowController.Callbacks {
                override fun onBubbleTap() = controller.beginListening()
                override fun onStopListening() = controller.finishListeningAndRun()
                override fun onApprove() = controller.approveConfirmation()
                override fun onDeny() = controller.denyConfirmation()

                override fun onSpeakDecision() {
                    controller.uiState.value.confirmation?.let { confirmation ->
                        confirmationVoice?.listen(confirmation.lang)
                    }
                }

                override fun onWatchModeChange(enabled: Boolean) = controller.setWatchMode(enabled)
                override fun onRetry() = controller.retry()
                override fun onCallBuyer() = Unit
            }
        )
        scope.launch(Dispatchers.Main.immediate) {
            controller.uiState.collect { state ->
                if (Settings.canDrawOverlays(this@SeniorOverlayService)) {
                    window?.show(state)
                }
            }
        }
        controller.setOverlayEnabled(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val controller = (application as MilfApplication).sessionController
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.getBooleanExtra(EXTRA_START_LISTENING, false) == true) {
            controller.beginListening()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        confirmationVoice?.destroy()
        confirmationVoice = null
        window?.remove()
        window = null
        (application as MilfApplication).sessionController.setOverlayEnabled(false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val EXTRA_START_LISTENING = "ai.milf.client.START_LISTENING"

        fun start(context: Context, startListening: Boolean) {
            val intent = Intent(context, SeniorOverlayService::class.java)
                .putExtra(EXTRA_START_LISTENING, startListening)
            context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SeniorOverlayService::class.java))
        }
    }
}
