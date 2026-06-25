package ai.milf.client.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentTabControlsTest {
    @Test
    fun agentTabDoesNotExposeStartOrStopControls() {
        val source = File("src/main/java/ai/milf/client/MilfUi.kt").readText()
        val agentTab = source.substringAfter("private fun AgentTab(")
            .substringBefore("private fun LogsTab(")

        assertTrue(agentTab.contains("Save memory"))
        assertFalse(agentTab.contains("Text(\"Start\")"))
        assertFalse(agentTab.contains("Text(\"Stop\")"))
    }
}
