package com.github.kellpete.intellijewpsupport.parser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EwpDomSortFixerTest {

    @Test
    fun fixesUnsortedSubgroupsAndFiles() {
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

        val output = EwpDomSortFixer.sortAndFormat(input)

        assertTrue(output.indexOf("<name>alpha</name>") < output.indexOf("<name>zebra</name>"))
        assertTrue(output.indexOf("<name>a.cpp</name>") < output.indexOf("<name>z.cpp</name>"))
        assertTrue(output.indexOf("<name>zebra</name>") < output.indexOf("<name>a.cpp</name>"))
    }

    @Test
    fun fixesGroupsAfterFiles() {
        val input = """
            <project>
                <group>
                    <name>mixed</name>
                    <file><name>first.cpp</name></file>
                    <group><name>late</name></group>
                </group>
            </project>
            """.trimIndent()

        val output = EwpDomSortFixer.sortAndFormat(input)

        assertTrue(output.indexOf("<name>late</name>") < output.indexOf("<name>first.cpp</name>"))
    }

    @Test
    fun sortsTopLevelGroups() {
        val input = """
            <project>
                <group><name>zebra</name></group>
                <group><name>alpha</name></group>
            </project>
            """.trimIndent()

        val output = EwpDomSortFixer.sortAndFormat(input)

        assertTrue(output.indexOf("<name>alpha</name>") < output.indexOf("<name>zebra</name>"))
    }

    @Test
    fun enforcesExpandedSyntaxForEmptyElementsByDefault() {
        val input = """
            <project>
                <configuration>
                    <settings>
                        <name>General</name>
                        <state />
                    </settings>
                </configuration>
            </project>
            """.trimIndent()

        val output = EwpDomSortFixer.sortAndFormat(input)

        assertTrue(output.contains("<state></state>"))
        assertFalse(output.contains("<state />"))
        assertFalse(output.contains("<state/>"))
    }

    @Test
    fun enforcesSelfClosingSyntaxForEmptyDataElements() {
        val input = """
            <project>
                <configuration>
                    <settings>
                        <name>General</name>
                        <data></data>
                    </settings>
                </configuration>
            </project>
            """.trimIndent()

        val output = EwpDomSortFixer.sortAndFormat(input)

        assertTrue(output.contains("<data />") || output.contains("<data/>"))
        assertFalse(output.contains("<data></data>"))
    }

    @Test
    fun enforcesIndentationAndFormatting() {
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

        val output = EwpDomSortFixer.sortAndFormat(input)

        assertTrue(output.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"))
        assertTrue(output.endsWith("\n"))
        assertTrue(output.contains("    <configuration>"))
        assertTrue(output.contains("        <settings>"))
        assertTrue(output.contains("            <state></state>"))
        assertTrue(output.indexOf("<name>alpha</name>") < output.indexOf("<name>z.cpp</name>"))
    }

    @Test
    fun sortsFilesByFilenameNotFullPath() {
        val input = """
            <project>
                <group>
                    <name>root</name>
                    <file><name>c:\foo\bar\zebra.cpp</name></file>
                    <file><name>c:\aaa\bar\alpha.cpp</name></file>
                </group>
            </project>
            """.trimIndent()

        val output = EwpDomSortFixer.sortAndFormat(input)

        assertTrue(
            output.indexOf("c:\\aaa\\bar\\alpha.cpp") < output.indexOf("c:\\foo\\bar\\zebra.cpp"),
        )
    }

    @Test
    fun sortsWithXmlDeclAndConfiguration() {
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

        val output = EwpDomSortFixer.sortAndFormat(input)

        assertTrue(output.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"))
        assertTrue(output.contains("<state></state>"))
        assertTrue(output.contains("Default mode"))
        assertTrue(output.indexOf("<name>alpha</name>") < output.indexOf("<name>z.cpp</name>"))
    }
}
