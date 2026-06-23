package ai.milf.client.security

class AccessibilityPackagePolicy(
    private val allowedPackages: Set<String> = DEFAULT_ALLOWED_PACKAGES
) {
    fun isAllowed(packageName: CharSequence?): Boolean {
        val value = packageName?.toString()?.trim() ?: return false
        return value in allowedPackages
    }

    companion object {
        val DEFAULT_ALLOWED_PACKAGES = setOf(
            "ai.milf.client",
            "com.whatsapp",
            "com.google.android.apps.nexuslauncher",
            "com.android.launcher",
            "com.android.launcher3",
            "com.miui.home",
            "com.sec.android.app.launcher",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.settings"
        )
    }
}
