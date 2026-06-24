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
    fun textGoalRoundTrips() {
        val raw = MilfProtocol.encode(
            TextGoal(goalText = "I want to see my grandson", lang = "en")
        )

        assertEquals(
            TextGoal(goalText = "I want to see my grandson", lang = "en"),
            MilfProtocol.decode(raw)
        )
    }

    @Test
    fun textGoalRoundTripsSessionId() {
        val raw = MilfProtocol.encode(
            TextGoal(goalText = "search movie", lang = "en", sessionId = "session-123")
        )
        val data = JSONObject(raw).getJSONObject("data")

        assertEquals("session-123", data.getString("session_id"))
        assertEquals(
            TextGoal(goalText = "search movie", lang = "en", sessionId = "session-123"),
            MilfProtocol.decode(raw)
        )
    }

    @Test
    fun audioRoundTripsSessionId() {
        val raw = MilfProtocol.encode(
            Audio(goalAudioB64 = "AQID", lang = "en", sessionId = "session-123")
        )
        val data = JSONObject(raw).getJSONObject("data")

        assertEquals("session-123", data.getString("session_id"))
        assertEquals(
            Audio(goalAudioB64 = "AQID", lang = "en", sessionId = "session-123"),
            MilfProtocol.decode(raw)
        )
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
            recoveryContactId = "buyer-daughter"
        )
        val raw = MilfProtocol.encode(message)
        val envelope = JSONObject(raw)

        assertEquals("TaskFailure", envelope.getString("type"))
        assertEquals("buyer-daughter", envelope.getJSONObject("data").getString("recovery_contact_id"))
        assertEquals(message, MilfProtocol.decode(raw))
    }

    @Test
    fun taskFailureRoundTripsNullRecoveryContactId() {
        val message = TaskFailure(
            message = "Could not identify the recipient",
            lang = "en",
            recoveryContactId = null
        )
        val raw = MilfProtocol.encode(message)
        val envelope = JSONObject(raw)

        assertTrue(envelope.getJSONObject("data").isNull("recovery_contact_id"))
        assertEquals(message, MilfProtocol.decode(raw))
    }

    @Test
    fun actionResultRoundTripsNestedMapResult() {
        val state = mapOf(
            "a11y_tree" to mapOf("children" to emptyList<Map<String, Any?>>()),
            "phone_state" to mapOf("packageName" to "com.whatsapp"),
            "device_context" to mapOf(
                "screen_bounds" to mapOf("width" to 1080, "height" to 2400)
            )
        )
        val raw = MilfProtocol.encode(ActionResult(id = "a1", ok = true, result = state))
        val result = JSONObject(raw)
            .getJSONObject("data")
            .getJSONObject("result")

        assertEquals("com.whatsapp", result.getJSONObject("phone_state").getString("packageName"))

        val decoded = MilfProtocol.decode(raw) as ActionResult
        assertEquals(state, decoded.result)
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownTypeRaises() {
        MilfProtocol.decode("""{"type":"Nope","data":{}}""")
    }
}
