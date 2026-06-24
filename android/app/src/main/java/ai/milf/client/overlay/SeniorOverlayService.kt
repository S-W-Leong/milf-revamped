package ai.milf.client.overlay

import ai.milf.client.MainActivity
import ai.milf.client.MilfApplication
import ai.milf.client.R
import ai.milf.client.audio.ConfirmationVoiceRecognizer
import ai.milf.client.session.MilfSessionController
import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SeniorOverlayService : Service() {
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var window: OverlayWindowController? = null
    private var confirmationVoice: ConfirmationVoiceRecognizer? = null
    private var foregroundStarted = false

    override fun onCreate() {
        super.onCreate()
        val controller = (application as MilfApplication).sessionController
        createNotificationChannel()
        if (Settings.canDrawOverlays(this) && hasRecordAudioPermission()) {
            startForegroundSafely()
        }
        confirmationVoice = ConfirmationVoiceRecognizer(
            context = this,
            onText = controller::onConfirmationSpeech,
            onError = { }
        )
        window = OverlayWindowController(
            context = this,
            callbacks = object : OverlayWindowController.Callbacks {
                override fun onBubbleTap() {
                    beginListeningSafely(controller)
                }

                override fun onStopListening() = controller.finishListeningAndRun()
                override fun onApprove() = controller.approveConfirmation()
                override fun onDeny() = controller.denyConfirmation()

                override fun onSpeakDecision() {
                    controller.uiState.value.confirmation?.let { confirmation ->
                        confirmationVoice?.listen(confirmation.lang)
                    }
                }

                override fun onWatchModeChange(enabled: Boolean) = controller.setWatchMode(enabled)
                override fun onRetry() {
                    beginListeningSafely(controller)
                }

                override fun onCallBuyer() = controller.callBuyer()
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
            openSetupActivity()
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.getBooleanExtra(EXTRA_START_LISTENING, false) == true) {
            if (!beginListeningSafely(controller)) {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        val controller = (application as MilfApplication).sessionController
        controller.cancelActiveSession()
        controller.setOverlayEnabled(false)
        scope.cancel()
        confirmationVoice?.destroy()
        confirmationVoice = null
        window?.remove()
        window = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun beginListeningSafely(controller: MilfSessionController): Boolean {
        if (!hasRecordAudioPermission()) {
            openSetupActivity()
            return false
        }
        if (!foregroundStarted && !startForegroundSafely()) {
            openSetupActivity()
            return false
        }
        return try {
            controller.beginListening()
            true
        } catch (exception: SecurityException) {
            Log.w(TAG, "Microphone permission was unavailable when starting listening", exception)
            controller.cancelActiveSession()
            openSetupActivity()
            false
        }
    }

    private fun hasRecordAudioPermission(): Boolean =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun openSetupActivity() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.overlay_running),
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }

    private fun startForegroundSafely(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    overlayNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, overlayNotification())
            }
            foregroundStarted = true
            true
        } catch (exception: RuntimeException) {
            Log.w(TAG, "Unable to start overlay foreground service", exception)
            false
        }
    }

    private fun overlayNotification(): Notification {
        val setupIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            setupIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_helper)
            .setContentTitle(getString(R.string.overlay_running))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val EXTRA_START_LISTENING = "ai.milf.client.START_LISTENING"
        private const val NOTIFICATION_CHANNEL_ID = "milf_overlay"
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "SeniorOverlayService"

        fun start(context: Context, startListening: Boolean) {
            val intent = Intent(context, SeniorOverlayService::class.java)
                .putExtra(EXTRA_START_LISTENING, startListening)
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                Settings.canDrawOverlays(context) &&
                context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            ) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SeniorOverlayService::class.java))
        }
    }
}
