package ai.milf.client.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

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

    @Test
    fun confirmRequestRoundTripsContactId() {
        val message = ConfirmRequest(
            id = "c1",
            summary = "Send message?",
            lang = "en",
            contactId = "contact-42"
        )
        val raw = MilfProtocol.encode(message)
        val data = JSONObject(raw).getJSONObject("data")

        assertEquals("contact-42", data.getString("contact_id"))
        assertEquals(message, MilfProtocol.decode(raw))
    }

    @Test
    fun taskCompleteRoundTripsContactId() {
        val message = TaskComplete(summary = "Message sent", lang = "en", contactId = "contact-42")
        val raw = MilfProtocol.encode(message)
        val envelope = JSONObject(raw)

        assertEquals("TaskComplete", envelope.getString("type"))
        assertEquals("contact-42", envelope.getJSONObject("data").getString("contact_id"))
        assertEquals(message, MilfProtocol.decode(raw))
    }

    @Test
    fun taskFailureRoundTripsRecoveryContactId() {
        val message = TaskFailure(
            message = "Could not identify the recipient",
            lang = "en",
            recoveryContactId = null
        )
        val raw = MilfProtocol.encode(message)
        val envelope = JSONObject(raw)

        assertEquals("TaskFailure", envelope.getString("type"))
        assertTrue(envelope.getJSONObject("data").isNull("recovery_contact_id"))
        assertEquals(message, MilfProtocol.decode(raw))
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownTypeRaises() {
        MilfProtocol.decode("""{"type":"Nope","data":{}}""")
    }
}
