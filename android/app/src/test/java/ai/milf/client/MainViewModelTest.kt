package ai.milf.client

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MainViewModelTest {
    @Test
    fun approveConfirmationClearsPendingRequest() = runTest {
        val sent = mutableListOf<Boolean>()
        val viewModel = MainViewModel(
            dependencies = MainViewModel.Dependencies.fake(
                sendConfirm = { approved -> sent += approved }
            )
        )

        viewModel.startRecording()
        viewModel.stopAndRun()
        viewModel.showConfirmationForTest("c1", "Call Wei now?", "en")
        viewModel.approveConfirmation()

        assertEquals(listOf(true), sent)
        assertEquals(null, viewModel.uiState.value.confirmation)
    }

    @Test
    fun confirmationSpeechSendsDecision() = runTest {
        val sent = mutableListOf<Boolean>()
        val viewModel = MainViewModel(
            dependencies = MainViewModel.Dependencies.fake(
                sendConfirm = { approved -> sent += approved }
            )
        )

        viewModel.startRecording()
        viewModel.stopAndRun()
        viewModel.showConfirmationForTest("c1", "Call Wei now?", "en")
        viewModel.onConfirmationSpeech("tak nak")

        assertEquals(listOf(false), sent)
        assertEquals(null, viewModel.uiState.value.confirmation)
    }
}
