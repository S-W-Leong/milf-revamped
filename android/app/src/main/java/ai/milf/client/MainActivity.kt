package ai.milf.client

import android.Manifest
import android.content.Intent
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
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                if (granted) {
                    viewModel.startRecording()
                }
            }

            LaunchedEffect(Unit) {
                viewModel.refreshAccessibilityStatus()
            }

            MilfUi(
                state = state,
                onBackendUrlChange = viewModel::setBackendUrl,
                onLangChange = viewModel::setLang,
                onMicPressed = {
                    if (state.isRecording) {
                        viewModel.stopAndRun()
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onApprove = viewModel::approveConfirmation,
                onDeny = viewModel::denyConfirmation,
                onOpenAccessibility = {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshAccessibilityStatus()
    }
}
