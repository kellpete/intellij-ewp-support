package com.github.kellpete.intellijewpsupport.parser

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jdom.Element

class EwpSortFixerTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        snapshotCodeInsightSettings()
    }

    private fun snapshotCodeInsightSettings() {
        val current = CodeInsightSettings.getInstance()
        val snapshot = CodeInsightSettings()
        val element = Element("temp")
        current.writeExternal(element)
        snapshot.loadState(element)
        setDefaultCodeInsightSettings(snapshot)
    }

    fun testFixSortingClearsIssuesInEwpFile() {
        val ewpFile = myFixture.configureByText(
            "broken.ewp",
            """
            <project>
                <group>
                    <name>mixed</name>
                    <file><name>z.cpp</name></file>
                    <file><name>a.cpp</name></file>
                    <group><name>zebra</name></group>
                    <group><name>alpha</name></group>
                </group>
            </project>
            """.trimIndent(),
        ).virtualFile

        val parser = EwpParser()
        assertTrue(parser.parse(ewpFile).sortIssues.isNotEmpty())

        EwpSortFixer.fixSorting(ewpFile, project)

        val fixedProject = parser.parse(ewpFile)
        assertTrue(fixedProject.sortIssues.isEmpty())
    }
}
