package ai.milf.client.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityPackagePolicyTest {
    @Test
    fun allowsMilfWhatsappLauncherAndSystemSetupPackages() {
        val policy = AccessibilityPackagePolicy()

        assertTrue(policy.isAllowed("ai.milf.client"))
        assertTrue(policy.isAllowed("com.whatsapp"))
        assertTrue(policy.isAllowed("com.google.android.apps.nexuslauncher"))
        assertTrue(policy.isAllowed("com.android.permissioncontroller"))
        assertTrue(policy.isAllowed("com.android.settings"))
    }

    @Test
    fun rejectsUnrelatedPackagesAndMissingPackageNames() {
        val policy = AccessibilityPackagePolicy()

        assertFalse(policy.isAllowed("com.bank.app"))
        assertFalse(policy.isAllowed("com.social.feed"))
        assertFalse(policy.isAllowed(null))
        assertFalse(policy.isAllowed(" "))
    }
}
