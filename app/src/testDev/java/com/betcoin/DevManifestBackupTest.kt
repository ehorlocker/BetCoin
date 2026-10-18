package com.betcoin

import com.google.common.truth.Truth.assertThat
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Test
import org.w3c.dom.Element

/**
 * The dev flavor must never back up test data (spec §14 Backup Rules).
 *
 * Reads the dev variant's merged manifest, which Gradle passes in through the
 * `betcoin.mergedManifest` system property (see app/build.gradle.kts), so the
 * check covers the manifest that actually ships, not just src/dev.
 */
class DevManifestBackupTest {

    @Test
    fun devMergedManifest_disablesBackup() {
        val path = System.getProperty("betcoin.mergedManifest")
        assertThat(path).isNotNull()

        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val document = factory.newDocumentBuilder().parse(File(path!!))
        val application = document.getElementsByTagName("application").item(0) as Element

        assertThat(application.getAttributeNS(ANDROID_NS, "allowBackup")).isEqualTo("false")
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
