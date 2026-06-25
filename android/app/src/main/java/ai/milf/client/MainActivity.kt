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
import kotlinx.coroutines.delay

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
            ) { viewModel.refreshSetupStatus() }
            val callPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { viewModel.refreshSetupStatus() }

            LaunchedEffect(Unit) {
                viewModel.refreshSetupStatus()
            }
            LaunchedEffect(state.backendUrl, state.backendConnectionRequested) {
                while (state.backendConnectionRequested) {
                    viewModel.refreshBackendConnection()
                    delay(3_000)
                }
            }

            MilfUi(
                state = state,
                onBackendUrlChange = viewModel::setBackendUrl,
                onConnectBackend = viewModel::connectBackend,
                onDisconnectBackend = viewModel::disconnectBackend,
                onLangChange = viewModel::setLang,
                onSpeechInputModeChange = viewModel::setSpeechInputMode,
                onAgentMemoryChange = viewModel::setAgentMemoryDraft,
                onAgentMemorySave = viewModel::saveAgentMemory,
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
                    if (viewModel.canStartHelper()) {
                        SeniorOverlayService.start(this, startListening = false)
                        val homeIntent = Intent(Intent.ACTION_MAIN)
                            .addCategory(Intent.CATEGORY_HOME)
                            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(homeIntent)
                    }
                },
                onSetAppScreen = viewModel::setAppScreen,
                onConfigTabChange = viewModel::setConfigTab
            )
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshSetupStatus()
    }
}
