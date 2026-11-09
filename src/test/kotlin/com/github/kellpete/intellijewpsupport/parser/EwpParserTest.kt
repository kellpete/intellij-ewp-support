package com.github.kellpete.intellijewpsupport.parser

import com.github.kellpete.intellijewpsupport.ewp.editor.EwpFileWriter
import com.github.kellpete.intellijewpsupport.model.EwpSortIssueType
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jdom.Element
import java.nio.file.Paths

@TestDataPath("\$CONTENT_ROOT/src/test/testData/sample")
class EwpParserTest : BasePlatformTestCase() {

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

    fun testParsesSampleEwpStructure() {
        val ewpFile = myFixture.configureByFile("sample.ewp").virtualFile
        val ewpProject = EwpParser().parse(ewpFile)

        assertEquals("sample.ewp", ewpProject.ewpFile.name)
        assertEquals(2, ewpProject.groups.size)
        assertEquals("config", ewpProject.groups[0].name)
        assertEquals("src", ewpProject.groups[1].name)

        val configGroup = ewpProject.groups[0]
        assertEquals(1, configGroup.groups.size)
        assertEquals("stm32", configGroup.groups[0].name)
        assertEquals(2, configGroup.files.size)

        val servicesGroup = ewpProject.groups[1].groups.single()
        assertEquals("services", servicesGroup.name)
        val parameterGroup = servicesGroup.groups.single()
        assertEquals("watchdog", parameterGroup.name)
        assertEquals(2, parameterGroup.files.size)
        assertEquals(1, ewpProject.groups[1].files.size)
    }

    fun testParsesSampleEwpFileCount() {
        val ewpFile = myFixture.configureByFile("sample.ewp").virtualFile
        val ewpProject = EwpParser().parse(ewpFile)

        assertEquals(6, countFiles(ewpProject.groups))
    }

    fun testSampleEwpHasNoSortIssues() {
        val ewpFile = myFixture.configureByFile("sample.ewp").virtualFile
        val ewpProject = EwpParser().parse(ewpFile)

        assertTrue(ewpProject.sortIssues.isEmpty())
    }

    fun testDetectsGroupsAfterFiles() {
        val ewpFile = myFixture.configureByText(
            "broken.ewp",
            """
            <project>
                <group>
                    <name>mixed</name>
                    <file><name>first.cpp</name></file>
                    <group><name>late</name></group>
                </group>
            </project>
            """.trimIndent(),
        ).virtualFile

        val ewpProject = EwpParser().parse(ewpFile)

        assertEquals(1, ewpProject.sortIssues.size)
        assertEquals(EwpSortIssueType.GROUPS_AFTER_FILES, ewpProject.sortIssues.single().type)
        assertEquals(listOf("mixed"), ewpProject.sortIssues.single().groupPath)
    }

    fun testDetectsMissingFilesOnDisk() {
        val presentFile = kotlin.io.path.createTempFile(prefix = "ewp-present-", suffix = ".cpp").toFile()
        presentFile.writeText("// present")
        try {
            val presentPath = presentFile.absolutePath
            val ewpFile = myFixture.configureByText(
                "project.ewp",
                """
                <project>
                    <group>
                        <name>src</name>
                        <file><name>$presentPath</name></file>
                        <file><name>${'$'}PROJ_DIR${'$'}\absent.cpp</name></file>
                    </group>
                </project>
                """.trimIndent(),
            ).virtualFile

            val ewpProject = EwpParser().parse(ewpFile)

            assertEquals(1, ewpProject.missingFileCount)
            assertEquals("\$PROJ_DIR\$\\absent.cpp", ewpProject.missingFiles.single().rawPath)
            assertEquals(listOf("src"), ewpProject.missingFiles.single().groupPath)
        } finally {
            presentFile.delete()
        }
    }

    fun testRemoveMissingFilesClearsIssues() {
        val ewpFile = myFixture.configureByText(
            "project.ewp",
            """
            <project>
                <group>
                    <name>src</name>
                    <file><name>${'$'}PROJ_DIR${'$'}\absent.cpp</name></file>
                </group>
            </project>
            """.trimIndent(),
        ).virtualFile

        val parser = EwpParser()
        val missing = parser.parse(ewpFile).missingFiles
        assertEquals(1, missing.size)

        EwpFileWriter.removeFiles(
            ewpFile,
            project,
            absolutePaths = emptyList(),
            rawPaths = missing.map { it.rawPath },
        )

        val fixed = parser.parse(ewpFile)
        assertTrue(fixed.missingFiles.isEmpty())
        assertTrue(fixed.groups.isEmpty())
    }

    private fun countFiles(groups: List<com.github.kellpete.intellijewpsupport.model.EwpGroup>): Int =
        groups.sumOf { group -> group.files.size + countFiles(group.groups) }

    override fun getTestDataPath(): String =
        Paths.get("src/test/testData/sample").toAbsolutePath().toString()
}
