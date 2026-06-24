package ai.milf.client.rescue

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Test

class BuyerRescueTest {
    @Test
    fun buildsCallTargetWhenPermissionGranted() {
        val target = BuyerRescue.targetFor("+15551234567", hasCallPermission = true)

        assertEquals(Intent.ACTION_CALL, target.action)
        assertEquals("tel:+15551234567", target.uri)
    }

    @Test
    fun buildsDialTargetWhenPermissionMissing() {
        val target = BuyerRescue.targetFor("+15551234567", hasCallPermission = false)

        assertEquals(Intent.ACTION_DIAL, target.action)
        assertEquals("tel:+15551234567", target.uri)
    }

    @Test
    fun trimsPhoneForTarget() {
        val target = BuyerRescue.targetFor(" +15551234567 ", hasCallPermission = true)

        assertEquals(Intent.ACTION_CALL, target.action)
        assertEquals("tel:+15551234567", target.uri)
    }
}
