package com.github.kellpete.intellijewpsupport.ewp

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jdom.Element

class EwpPathReferenceTest : BasePlatformTestCase() {

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

    fun testCollectsProjDirPathReferences() {
        // Avoid a top-level `src/` folder: bundled CLion STM32 detection treats it like a Cube project
        // and logs errors during async startup (flaky TestLogger failures on CI).
        myFixture.addFileToProject("lib/main.txt", "content\n")
        val ewpPsi = myFixture.configureByText(
            "project.ewp",
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
                <group>
                    <name>src</name>
                    <file>
                        <name>${'$'}PROJ_DIR${'$'}\lib\main.txt</name>
                    </file>
                </group>
            </project>
            """.trimIndent(),
        )

        val ewpFile = assertInstanceOf(ewpPsi, EwpPsiFile::class.java)
        val references = ewpFile.references
        assertEquals(1, references.size)

        val resolved = references[0].resolve()
        assertNotNull(resolved)
        assertEquals("main.txt", resolved!!.containingFile.name)
    }

    fun testIgnoresGroupNamesWithoutProjDir() {
        val ewpPsi = myFixture.configureByText(
            "project.ewp",
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
                <group>
                    <name>src</name>
                </group>
            </project>
            """.trimIndent(),
        )

        val ewpFile = assertInstanceOf(ewpPsi, EwpPsiFile::class.java)
        assertEmpty(ewpFile.references)
    }

    fun testSoftReferenceWhenTargetMissing() {
        val ewpPsi = myFixture.configureByText(
            "project.ewp",
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
                <file>
                    <name>${'$'}PROJ_DIR${'$'}\missing.cpp</name>
                </file>
            </project>
            """.trimIndent(),
        )

        val ewpFile = assertInstanceOf(ewpPsi, EwpPsiFile::class.java)
        assertEquals(1, ewpFile.references.size)
        assertNull(ewpFile.references[0].resolve())
        assertTrue(ewpFile.references[0].isSoft)
    }
}
