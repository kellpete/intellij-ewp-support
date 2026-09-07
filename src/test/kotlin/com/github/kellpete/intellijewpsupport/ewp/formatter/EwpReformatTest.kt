package com.github.kellpete.intellijewpsupport.ewp.formatter

import com.github.kellpete.intellijewpsupport.ewp.EwpLanguage
import com.github.kellpete.intellijewpsupport.ewp.EwpPsiFile
import com.github.kellpete.intellijewpsupport.parser.EwpParser
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jdom.Element

class EwpReformatTest : BasePlatformTestCase() {

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

    fun testEwpFileUsesEwpLanguageNotXml() {
        val psiFile = myFixture.configureByText(
            "project.ewp",
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <project></project>
            """.trimIndent(),
        )

        val ewpFile = assertInstanceOf(psiFile, EwpPsiFile::class.java)
        assertEquals(EwpLanguage, ewpFile.language)
        assertFalse(ewpFile.language.id.equals("XML", ignoreCase = true))
    }

    fun testReformatSortsEwpFile() {
        val psiFile = myFixture.configureByText(
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
        )

        CodeStyleManager.getInstance(project).reformatText(psiFile, 0, psiFile.textLength)

        val text = myFixture.editor.document.text
        assertTrue(text.indexOf("<name>alpha</name>") < text.indexOf("<name>zebra</name>"))
        assertTrue(text.indexOf("<name>a.cpp</name>") < text.indexOf("<name>z.cpp</name>"))
        assertTrue(text.indexOf("<name>zebra</name>") < text.indexOf("<name>a.cpp</name>"))

        val parser = EwpParser()
        assertTrue(parser.parse(psiFile.virtualFile).sortIssues.isEmpty())
    }

    fun testReformatPreservesEmptyDataAsSelfClosing() {
        myFixture.configureByText(
            "data.ewp",
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
                <configuration>
                    <settings>
                        <name>General</name>
                        <data></data>
                    </settings>
                </configuration>
            </project>
            """.trimIndent(),
        )

        val psiFile = myFixture.file
        CodeStyleManager.getInstance(project).reformatText(psiFile, 0, psiFile.textLength)

        val text = myFixture.editor.document.text
        assertFalse(text.contains("<data></data>"))
        assertTrue(text.contains("<data />") || text.contains("<data/>"))
    }
}
