package ai.milf.client

import ai.milf.client.overlay.SeniorOverlayService
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val audioPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { }
            val callPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { }

            LaunchedEffect(Unit) {
                viewModel.refreshAccessibilityStatus()
            }

            MilfUi(
                state = state,
                onBackendUrlChange = viewModel::setBackendUrl,
                onLangChange = viewModel::setLang,
                onDemoModeChange = viewModel::setDemoMode,
                onOpenAccessibility = {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                },
                onOpenOverlayPermission = {
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                    )
                },
                onOpenAssistSettings = {
                    startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
                },
                onRequestAudioPermission = {
                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                },
                onRequestCallPermission = {
                    callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
                },
                onStartOverlay = {
                    SeniorOverlayService.start(this, startListening = false)
                },
                onStopOverlay = {
                    SeniorOverlayService.stop(this)
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshAccessibilityStatus()
    }
}
