package ai.milf.client.session

import org.junit.Assert.assertEquals
import org.junit.Test

class BackendUrlDefaultsTest {
    @Test
    fun defaultBackendUrlUsesDeployedRenderService() {
        assertEquals(
            "wss://milf-revamped.onrender.com/",
            SeniorUiState().backendUrl
        )
    }
}
