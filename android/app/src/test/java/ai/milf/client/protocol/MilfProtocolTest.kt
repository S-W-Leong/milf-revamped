package ai.milf.client.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MilfProtocolTest {
    @Test
    fun actionRoundTrips() {
        val raw = MilfProtocol.encode(
            Action(id = "1", name = "tap", args = mapOf("x" to 10, "y" to 20))
        )

        val decoded = MilfProtocol.decode(raw)

        assertTrue(decoded is Action)
        assertEquals("tap", (decoded as Action).name)
        assertEquals(10, decoded.args["x"])
    }

    @Test
    fun confirmResponseRoundTrips() {
        val raw = MilfProtocol.encode(ConfirmResponse(id = "c1", approved = true))

        assertEquals(ConfirmResponse(id = "c1", approved = true), MilfProtocol.decode(raw))
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownTypeRaises() {
        MilfProtocol.decode("""{"type":"Nope","data":{}}""")
    }
}
