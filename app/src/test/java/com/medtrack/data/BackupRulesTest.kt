package com.medtrack.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Android stops an app's whole cloud backup once it passes 25 MB, so photos stay out of it (medicines and history must
 * always fit); a phone-to-phone transfer has no such limit and keeps them. The setup preferences always stay out:
 * permissions aren't restored, so a new phone must run Get set again.
 */
class BackupRulesTest {
    private val setupOnly = setOf("sharedpref:medtrack.xml")
    private val expected = setupOnly + "file:photos/"

    private fun excludes(file: String, section: String?): Set<String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/xml/$file"))
        val root = section?.let { doc.getElementsByTagName(it).item(0) } ?: doc.documentElement
        val nodes = (root as org.w3c.dom.Element).getElementsByTagName("exclude")
        return (0 until nodes.length).map { nodes.item(it) as org.w3c.dom.Element }
            .map { "${it.getAttribute("domain")}:${it.getAttribute("path")}" }.toSet()
    }

    @Test fun cloudBackupLeavesOutPhotosAndSetup() = assertEquals(expected, excludes("data_extraction_rules.xml", "cloud-backup"))

    @Test fun deviceTransferKeepsPhotosButNotSetup() = assertEquals(setupOnly, excludes("data_extraction_rules.xml", "device-transfer"))

    @Test fun olderAndroidLeavesOutPhotosAndSetup() = assertEquals(expected, excludes("backup_rules.xml", null))
}
