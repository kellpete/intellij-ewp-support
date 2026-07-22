package com.github.kellpete.intellijewpsupport.parser

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class EwpSortFixerTest : BasePlatformTestCase() {

    fun testFixesUnsortedSubgroupsAndFiles() {
        val input = """
            <project>
                <group>
                    <name>root</name>
                    <group><name>zebra</name></group>
                    <group><name>alpha</name></group>
                    <file><name>z.cpp</name></file>
                    <file><name>a.cpp</name></file>
                </group>
            </project>
            """.trimIndent()

        val output = EwpTextSortFixer.sortGroupsInText(input)

        assertTrue(output.indexOf("<group><name>alpha</name></group>") < output.indexOf("<group><name>zebra</name></group>"))
        assertTrue(output.indexOf("<file><name>a.cpp</name></file>") < output.indexOf("<file><name>z.cpp</name></file>"))
        assertTrue(output.indexOf("<group><name>zebra</name></group>") < output.indexOf("<file><name>a.cpp</name></file>"))
    }

    fun testFixesGroupsAfterFiles() {
        val input = """
            <project>
                <group>
                    <name>mixed</name>
                    <file><name>first.cpp</name></file>
                    <group><name>late</name></group>
                </group>
            </project>
            """.trimIndent()

        val output = EwpTextSortFixer.sortGroupsInText(input)

        assertTrue(output.indexOf("<group><name>late</name></group>") < output.indexOf("<file><name>first.cpp</name></file>"))
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

    fun testSortsTopLevelGroups() {
        val input = """
            <project>
                <group><name>zebra</name></group>
                <group><name>alpha</name></group>
            </project>
            """.trimIndent()

        val output = EwpTextSortFixer.sortGroupsInText(input)

        assertTrue(output.indexOf("<group><name>alpha</name></group>") < output.indexOf("<group><name>zebra</name></group>"))
    }

    fun testPreservesEmptyElementSyntax() {
        val input = """
            <project>
                <configuration>
                    <settings>
                        <name>General</name>
                        <state></state>
                    </settings>
                </configuration>
                <group>
                    <name>mixed</name>
                    <file><name>z.cpp</name></file>
                    <group><name>alpha</name></group>
                </group>
            </project>
            """.trimIndent()

        val output = EwpTextSortFixer.sortGroupsInText(input)

        assertTrue(output.contains("<state></state>"))
        assertFalse(output.contains("<state />"))
        assertFalse(output.contains("<state/>"))
    }

    fun testPreservesIndentationAndLineEndings() {
        val input = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +
            "<project>\r\n" +
            "    <configuration>\r\n" +
            "        <settings>\r\n" +
            "            <name>General</name>\r\n" +
            "            <state></state>\r\n" +
            "        </settings>\r\n" +
            "    </configuration>\r\n" +
            "    <group>\r\n" +
            "        <name>mixed</name>\r\n" +
            "        <file>\r\n" +
            "            <name>z.cpp</name>\r\n" +
            "        </file>\r\n" +
            "        <group>\r\n" +
            "            <name>alpha</name>\r\n" +
            "        </group>\r\n" +
            "    </group>\r\n" +
            "</project>\r\n"

        val output = EwpTextSortFixer.sortGroupsInText(input)

        assertTrue(output.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n"))
        assertTrue(output.endsWith("\r\n"))
        assertTrue(output.contains("\r\n"))
        assertFalse(output.replace("\r\n", "").contains('\n'))
        assertTrue(output.contains("    <configuration>"))
        assertTrue(output.contains("        <settings>"))
        assertTrue(output.contains("            <state></state>"))
        assertTrue(output.indexOf("        <group>") < output.indexOf("        <file>"))
    }

    fun testDoesNotChangeAlreadySortedSample() {
        val input = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
                <fileVersion>4</fileVersion>
                <group>
                    <name>config</name>
                    <file>
                        <name>a.cpp</name>
                    </file>
                </group>
            </project>
            """.trimIndent()

        assertEquals(input, EwpTextSortFixer.sortGroupsInText(input))
    }

    fun testDetectsGroupsAfterFilesStillWorksAfterTextSort() {
        val input = """
            <project>
                <group>
                    <name>mixed</name>
                    <file><name>first.cpp</name></file>
                    <group><name>late</name></group>
                </group>
            </project>
            """.trimIndent()

        val output = EwpTextSortFixer.sortGroupsInText(input)
        val ewpFile = myFixture.configureByText("sorted.ewp", output).virtualFile
        val issue = EwpParser().parse(ewpFile).sortIssues

        assertTrue(issue.isEmpty())
    }

    fun testSortsFilesByFilenameNotFullPath() {
        val input = """
            <project>
                <group>
                    <name>root</name>
                    <file><name>c:\foo\bar\zebra.cpp</name></file>
                    <file><name>c:\aaa\bar\alpha.cpp</name></file>
                </group>
            </project>
            """.trimIndent()

        val output = EwpTextSortFixer.sortGroupsInText(input)

        assertTrue(
            output.indexOf("<file><name>c:\\aaa\\bar\\alpha.cpp</name></file>") <
                output.indexOf("<file><name>c:\\foo\\bar\\zebra.cpp</name></file>"),
        )
    }

    fun testSortsWithXmlDeclCommentAndConfiguration() {
        val input = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!-- IAR project -->
            <project>
                <fileVersion>4</fileVersion>
                <configuration>
                    <name>Debug</name>
                    <settings>
                        <name>General</name>
                        <state></state>
                        <option>
                            <name>OGChipSelectEditMenu</name>
                            <state><![CDATA[Default mode]]></state>
                        </option>
                    </settings>
                </configuration>
                <group>
                    <name>mixed</name>
                    <file><name>z.cpp</name></file>
                    <group><name>alpha</name></group>
                </group>
            </project>
            """.trimIndent()

        val output = EwpTextSortFixer.sortGroupsInText(input)

        assertTrue(input != output)
        assertTrue(output.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!-- IAR project -->"))
        assertTrue(output.contains("<state></state>"))
        assertTrue(output.contains("<![CDATA[Default mode]]>"))
        assertTrue(output.indexOf("<group><name>alpha</name></group>") < output.indexOf("<file><name>z.cpp</name></file>"))
    }
}
