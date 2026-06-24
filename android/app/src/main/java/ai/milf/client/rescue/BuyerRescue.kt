package ai.milf.client.rescue

import android.content.Intent
import android.net.Uri

data class BuyerRescueTarget(
    val action: String,
    val uri: String
)

object BuyerRescue {
    fun targetFor(phone: String, hasCallPermission: Boolean): BuyerRescueTarget =
        BuyerRescueTarget(
            action = if (hasCallPermission) Intent.ACTION_CALL else Intent.ACTION_DIAL,
            uri = "tel:$phone"
        )

    fun intentFor(phone: String, hasCallPermission: Boolean): Intent {
        val target = targetFor(phone, hasCallPermission)
        return Intent(target.action).apply {
            data = Uri.parse(target.uri)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
