package ai.milf.client

import java.nio.file.Files
import java.nio.file.Paths
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Test

class ManifestPermissionsTest {
    @Test
    fun manifestRequestsQueryAllPackages() {
        val manifest = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(Files.newInputStream(Paths.get("src/main/AndroidManifest.xml")))

        val permissions = manifest.getElementsByTagName("uses-permission")
        val hasQueryAllPackages = (0 until permissions.length).any { index ->
            val node = permissions.item(index)
            node.attributes
                ?.getNamedItem("android:name")
                ?.nodeValue == "android.permission.QUERY_ALL_PACKAGES"
        }

        assertTrue(hasQueryAllPackages)
    }
}
