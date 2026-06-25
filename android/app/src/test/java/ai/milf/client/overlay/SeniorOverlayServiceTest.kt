package ai.milf.client.overlay

import android.app.Service
import org.junit.Assert.assertEquals
import org.junit.Test

class SeniorOverlayServiceTest {
    @Test
    fun overlayServiceDoesNotAskAndroidToRestartAfterTaskRemovalOrProcessKill() {
        assertEquals(Service.START_NOT_STICKY, OverlayServiceLifecyclePolicy.validStartMode)
    }
}
