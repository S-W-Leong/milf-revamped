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

        viewModel.showConfirmationForTest("c1", "Call Wei now?", "en")
        viewModel.approveConfirmation()

        assertEquals(listOf(true), sent)
        assertEquals(null, viewModel.uiState.value.confirmation)
    }
}
