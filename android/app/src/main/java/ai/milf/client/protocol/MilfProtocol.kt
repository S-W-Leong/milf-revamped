package ai.milf.client.protocol

import org.json.JSONObject

interface MilfMessage

data class Action(
    val id: String,
    val name: String,
    val args: Map<String, Any?>
) : MilfMessage

data class ActionResult(
    val id: String,
    val ok: Boolean,
    val result: Any? = null,
    val error: String? = null
) : MilfMessage

data class Narration(
    val text: String,
    val lang: String
) : MilfMessage

data class ConfirmRequest(
    val id: String,
    val summary: String,
    val lang: String,
    val contactId: String? = null
) : MilfMessage

data class ConfirmResponse(
    val id: String,
    val approved: Boolean
) : MilfMessage

data class TaskComplete(
    val summary: String,
    val lang: String,
    val contactId: String? = null
) : MilfMessage

data class TaskFailure(
    val message: String,
    val lang: String,
    val recoveryContactId: String? = null
) : MilfMessage

data class Audio(
    val goalAudioB64: String,
    val lang: String
) : MilfMessage

object MilfProtocol {
    fun encode(message: MilfMessage): String {
        val data = when (message) {
            is Action -> JSONObject()
                .put("id", message.id)
                .put("name", message.name)
                .put("args", JSONObject().putMap(message.args))

            is ActionResult -> JSONObject()
                .put("id", message.id)
                .put("ok", message.ok)
                .putNullable("result", message.result)
                .putNullable("error", message.error)

            is Narration -> JSONObject()
                .put("text", message.text)
                .put("lang", message.lang)

            is ConfirmRequest -> JSONObject()
                .put("id", message.id)
                .put("summary", message.summary)
                .put("lang", message.lang)
                .putNullable("contact_id", message.contactId)

            is ConfirmResponse -> JSONObject()
                .put("id", message.id)
                .put("approved", message.approved)

            is TaskComplete -> JSONObject()
                .put("summary", message.summary)
                .put("lang", message.lang)
                .putNullable("contact_id", message.contactId)

            is TaskFailure -> JSONObject()
                .put("message", message.message)
                .put("lang", message.lang)
                .putNullable("recovery_contact_id", message.recoveryContactId)

            is Audio -> JSONObject()
                .put("goal_audio_b64", message.goalAudioB64)
                .put("lang", message.lang)

            else -> throw IllegalArgumentException("Unknown message class: ${message::class.simpleName}")
        }
        return JSONObject()
            .put("type", typeName(message))
            .put("data", data)
            .toString()
    }

    fun decode(raw: String): MilfMessage {
        val envelope = JSONObject(raw)
        val type = envelope.getString("type")
        val data = envelope.getJSONObject("data")
        return when (type) {
            "Action" -> Action(
                id = data.getString("id"),
                name = data.getString("name"),
                args = data.getJSONObject("args").toMap()
            )

            "ActionResult" -> ActionResult(
                id = data.getString("id"),
                ok = data.getBoolean("ok"),
                result = data.optNullable("result"),
                error = data.optStringOrNull("error")
            )

            "Narration" -> Narration(
                text = data.getString("text"),
                lang = data.getString("lang")
            )

            "ConfirmRequest" -> ConfirmRequest(
                id = data.getString("id"),
                summary = data.getString("summary"),
                lang = data.getString("lang"),
                contactId = data.optStringOrNull("contact_id")
            )

            "ConfirmResponse" -> ConfirmResponse(
                id = data.getString("id"),
                approved = data.getBoolean("approved")
            )

            "TaskComplete" -> TaskComplete(
                summary = data.getString("summary"),
                lang = data.getString("lang"),
                contactId = data.optStringOrNull("contact_id")
            )

            "TaskFailure" -> TaskFailure(
                message = data.getString("message"),
                lang = data.getString("lang"),
                recoveryContactId = data.optStringOrNull("recovery_contact_id")
            )

            "Audio" -> Audio(
                goalAudioB64 = data.getString("goal_audio_b64"),
                lang = data.getString("lang")
            )

            else -> throw IllegalArgumentException("Unknown message type: $type")
        }
    }

    private fun typeName(message: MilfMessage): String = when (message) {
        is Action -> "Action"
        is ActionResult -> "ActionResult"
        is Narration -> "Narration"
        is ConfirmRequest -> "ConfirmRequest"
        is ConfirmResponse -> "ConfirmResponse"
        is TaskComplete -> "TaskComplete"
        is TaskFailure -> "TaskFailure"
        is Audio -> "Audio"
        else -> throw IllegalArgumentException("Unknown message class: ${message::class.simpleName}")
    }
}

private fun JSONObject.putMap(values: Map<String, Any?>): JSONObject {
    values.forEach { (key, value) ->
        putNullable(key, value)
    }
    return this
}

private fun JSONObject.putNullable(key: String, value: Any?): JSONObject =
    put(key, value ?: JSONObject.NULL)

private fun JSONObject.toMap(): Map<String, Any?> {
    val out = linkedMapOf<String, Any?>()
    keys().forEach { key ->
        out[key] = optNullable(key)
    }
    return out
}

private fun JSONObject.optNullable(key: String): Any? =
    if (!has(key) || isNull(key)) null else get(key)

private fun JSONObject.optStringOrNull(key: String): String? =
    if (!has(key) || isNull(key)) null else getString(key)
