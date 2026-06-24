package ai.milf.client.assist

import ai.milf.client.overlay.SeniorOverlayService
import android.app.Activity
import android.os.Bundle

class AssistEntryActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SeniorOverlayService.start(this, startListening = true)
        finish()
    }
}
