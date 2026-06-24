package ai.milf.client.accessibility

import android.view.accessibility.AccessibilityWindowInfo

internal data class AccessibilityRootCandidate<T>(
    val root: T,
    val packageName: String,
    val type: Int,
    val isActive: Boolean
)

internal object AccessibilityRootSelector {
    fun <T> select(
        candidates: List<AccessibilityRootCandidate<T>>,
        ownPackageName: String
    ): T? {
        return candidates.firstOrNull {
            it.packageName.isNotBlank() &&
                it.packageName != ownPackageName &&
                it.type == AccessibilityWindowInfo.TYPE_APPLICATION
        }?.root
            ?: candidates.firstOrNull {
                it.packageName.isNotBlank() && it.packageName != ownPackageName
            }?.root
            ?: candidates.firstOrNull { it.isActive }?.root
            ?: candidates.firstOrNull()?.root
    }
}
