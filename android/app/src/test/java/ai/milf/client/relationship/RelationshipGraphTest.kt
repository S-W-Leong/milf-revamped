package ai.milf.client.relationship

import org.junit.Assert.assertEquals
import org.junit.Test

class RelationshipGraphTest {
    @Test
    fun demoResolvesGrandsonByBackendContactId() {
        val contact = RelationshipGraph.demo().contact("wei-grandson")

        assertEquals("Wei", contact?.displayName)
        assertEquals("grandson", contact?.relationship)
        assertEquals("WhatsApp", contact?.preferredApp)
        assertEquals("video", contact?.preferredChannel)
    }

    @Test
    fun demoExposesEscapeContact() {
        val contact = RelationshipGraph.demo().escapeContact

        assertEquals("buyer-daughter", contact.id)
        assertEquals("+15551234567", contact.phone)
    }
}
