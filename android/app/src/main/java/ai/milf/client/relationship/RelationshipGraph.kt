package ai.milf.client.relationship

import ai.milf.client.R

data class RelationshipContact(
    val id: String,
    val displayName: String,
    val relationship: String,
    val aliases: List<String>,
    val preferredApp: String,
    val preferredChannel: String,
    val photoResId: Int,
    val phone: String? = null
)

class RelationshipGraph(
    private val contacts: List<RelationshipContact>,
    val escapeContact: RelationshipContact
) {
    fun contact(id: String?): RelationshipContact? =
        contacts.firstOrNull { it.id == id }

    companion object {
        fun demo(): RelationshipGraph {
            val wei = RelationshipContact(
                id = "wei-grandson",
                displayName = "Wei",
                relationship = "grandson",
                aliases = listOf("grandson", "cucu", "Ah Xuan", "Ah Boy"),
                preferredApp = "WhatsApp",
                preferredChannel = "video",
                photoResId = R.drawable.contact_wei_avatar
            )
            val daughter = RelationshipContact(
                id = "buyer-daughter",
                displayName = "Daughter",
                relationship = "daughter",
                aliases = listOf("daughter", "my daughter", "anak perempuan"),
                preferredApp = "Phone",
                preferredChannel = "voice",
                photoResId = R.drawable.contact_wei_avatar,
                phone = "+15551234567"
            )
            return RelationshipGraph(
                contacts = listOf(wei, daughter),
                escapeContact = daughter
            )
        }
    }
}
