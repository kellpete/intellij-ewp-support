package com.github.kellpete.intellijewpsupport.ewp.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class EwpFileWriterTest {

    @Test
    fun addsFileToExistingGroup() {
        val input = loadSampleEwp()
        val output = EwpFileWriter.addFilesToXml(
            input,
            "/project/firmware",
            listOf(
                EwpFileWriter.FileAddition(
                    absolutePath = "/project/src/tools/helper.cpp",
                    groupPath = listOf("src", "tools"),
                ),
            ),
        )

        assertTrue(output.contains("\$PROJ_DIR\$\\..\\src\\tools\\helper.cpp"))
        assertTrue(output.indexOf("<name>tools</name>") < output.indexOf("helper.cpp"))
    }

    @Test
    fun createsMissingGroups() {
        val input = loadSampleEwp()
        val output = EwpFileWriter.addFilesToXml(
            input,
            "/project/firmware",
            listOf(
                EwpFileWriter.FileAddition(
                    absolutePath = "/project/src/newdir/newfile.cpp",
                    groupPath = listOf("src", "newdir"),
                ),
            ),
        )

        assertTrue(output.contains("<name>newdir</name>"))
        assertTrue(output.contains("newfile.cpp"))
    }

    @Test
    fun doesNotDuplicateExistingFile() {
        val input = loadSampleEwp()
        val output = EwpFileWriter.addFilesToXml(
            input,
            "/project/firmware",
            listOf(
                EwpFileWriter.FileAddition(
                    absolutePath = "/project/src/main.cpp",
                    groupPath = listOf("src"),
                ),
            ),
        )

        assertEquals(1, output.split("main.cpp").size - 1)
    }

    @Test
    fun keepsSortedOutput() {
        val input = loadSampleEwp()
        val output = EwpFileWriter.addFilesToXml(
            input,
            "/project/firmware",
            listOf(
                EwpFileWriter.FileAddition(
                    absolutePath = "/project/src/zebra.cpp",
                    groupPath = listOf("src"),
                ),
                EwpFileWriter.FileAddition(
                    absolutePath = "/project/src/alpha.cpp",
                    groupPath = listOf("src"),
                ),
            ),
        )

        assertTrue(output.indexOf("alpha.cpp") < output.indexOf("main.cpp"))
        assertTrue(output.indexOf("main.cpp") < output.indexOf("zebra.cpp"))
    }

    @Test
    fun removesGroupAndNestedFiles() {
        val input = loadSampleEwp()
        val output = EwpFileWriter.removeFromEwpToXml(
            input,
            "/project/firmware",
            groupPaths = listOf(listOf("src", "services", "watchdog")),
        )

        assertFalse(output.contains("Service.cpp"))
        assertFalse(output.contains("Service.hpp"))
        assertTrue(output.contains("main.cpp"))
        assertFalse(output.contains("<name>watchdog</name>"))
    }

    @Test
    fun removesExistingFile() {
        val input = loadSampleEwp()
        val output = EwpFileWriter.removeFilesToXml(
            input,
            "/project/firmware",
            listOf("/project/src/main.cpp"),
        )

        assertFalse(output.contains("main.cpp"))
        assertTrue(output.contains("Service.cpp"))
    }

    @Test
    fun removeIsNoOpWhenPathAbsent() {
        val input = loadSampleEwp()
        val output = EwpFileWriter.removeFilesToXml(
            input,
            "/project/firmware",
            listOf("/project/src/missing.cpp"),
        )

        assertEquals(input, output)
    }

    @Test
    fun prunesEmptyGroupsAfterRemove() {
        val input = loadSampleEwp()
        val output = EwpFileWriter.removeFilesToXml(
            input,
            "/project/firmware",
            listOf(
                "/project/src/services/wd/Service.cpp",
                "/project/src/services/wd/Service.hpp",
            ),
        )

        assertFalse(output.contains("Service.cpp"))
        assertFalse(output.contains("Service.hpp"))
        assertFalse(output.contains("<name>watchdog</name>"))
        assertFalse(output.contains("<name>services</name>"))
        assertTrue(output.contains("<name>src</name>"))
        assertTrue(output.contains("main.cpp"))
    }

    @Test
    fun movesFileToNewGroup() {
        val input = loadSampleEwp()
        val output = EwpFileWriter.moveFilesToXml(
            input,
            "/project/firmware",
            listOf(
                EwpFileWriter.FileMove(
                    oldAbsolutePath = "/project/src/main.cpp",
                    newAbsolutePath = "/project/src/tools/main.cpp",
                    newGroupPath = listOf("src", "tools"),
                ),
            ),
        )

        assertFalse(output.contains("\$PROJ_DIR\$\\..\\src\\main.cpp"))
        assertTrue(output.contains("\$PROJ_DIR\$\\..\\src\\tools\\main.cpp"))
        assertTrue(output.contains("<name>tools</name>"))
    }

    @Test
    fun renameKeepsProvidedGroup() {
        val input = loadSampleEwp()
        val output = EwpFileWriter.moveFilesToXml(
            input,
            "/project/firmware",
            listOf(
                EwpFileWriter.FileMove(
                    oldAbsolutePath = "/project/src/main.cpp",
                    newAbsolutePath = "/project/src/app.cpp",
                    newGroupPath = listOf("src"),
                ),
            ),
        )

        assertFalse(output.contains("main.cpp"))
        assertTrue(output.contains("\$PROJ_DIR\$\\..\\src\\app.cpp"))
        assertTrue(output.contains("<name>src</name>"))
    }

    @Test
    fun createsEmptyGroupUnderExistingParent() {
        val input = loadSampleEwp()
        val output = EwpFileWriter.createGroupInXml(input, listOf("src", "tools"))

        assertTrue(output.contains("<name>tools</name>"))
        assertTrue(output.indexOf("<name>src</name>") < output.indexOf("<name>tools</name>"))
    }

    @Test
    fun createGroupIsNoOpWhenGroupExists() {
        val input = loadSampleEwp()
        val output = EwpFileWriter.createGroupInXml(input, listOf("src"))

        assertEquals(input, output)
    }

    @Test
    fun createsNestedGroupWithMissingParents() {
        val input = loadSampleEwp()
        val output = EwpFileWriter.createGroupInXml(input, listOf("libs", "vendor"))

        assertTrue(output.contains("<name>libs</name>"))
        assertTrue(output.contains("<name>vendor</name>"))
    }

    @Test
    fun removesCppAndHppTogether() {
        val input = loadSampleEwp()
        val withPair = EwpFileWriter.addFilesToXml(
            input,
            "/project/firmware",
            listOf(
                EwpFileWriter.FileAddition("/project/src/Foo.cpp", listOf("src")),
                EwpFileWriter.FileAddition("/project/src/Foo.hpp", listOf("src")),
            ),
        )

        val output = EwpFileWriter.removeFilesToXml(
            withPair,
            "/project/firmware",
            listOf("/project/src/Foo.cpp", "/project/src/Foo.hpp"),
        )

        assertFalse(output.contains("Foo.cpp"))
        assertFalse(output.contains("Foo.hpp"))
        assertTrue(output.contains("main.cpp"))
    }

    @Test
    fun movesFileToAnotherGroup() {
        val input = loadSampleEwp()
        val output = EwpFileWriter.moveNodesInXml(
            input,
            fileMoves = listOf(
                EwpFileWriter.TreeFileMove(
                    rawPath = "\$PROJ_DIR$\\..\\src\\services\\wd\\Service.cpp",
                    sourceGroupPath = listOf("src", "services", "watchdog"),
                    targetGroupPath = listOf("config"),
                ),
            ),
        )

        // Service.cpp is now in config, after the existing files in sorted order.
        assertTrue(output.indexOf("<name>config</name>") < output.indexOf("Service.cpp"))
        assertTrue(output.indexOf("AppSettings.hpp") < output.indexOf("OtherSettings.hpp"))
        assertTrue(output.indexOf("OtherSettings.hpp") < output.indexOf("Service.cpp"))
        // Service.hpp remains in the source group.
        assertTrue(output.indexOf("<name>watchdog</name>") < output.indexOf("Service.hpp"))
    }

    @Test
    fun movesFileCreatesTargetGroupIfAbsent() {
        val input = loadSampleEwp()
        val output = EwpFileWriter.moveNodesInXml(
            input,
            fileMoves = listOf(
                EwpFileWriter.TreeFileMove(
                    rawPath = "\$PROJ_DIR$\\..\\src\\main.cpp",
                    sourceGroupPath = listOf("src"),
                    targetGroupPath = listOf("newgroup"),
                ),
            ),
        )

        assertTrue(output.contains("<name>newgroup</name>"))
        assertTrue(output.indexOf("<name>newgroup</name>") < output.indexOf("main.cpp"))
    }

    @Test
    fun movesFileRejectsSameGroup() {
        val input = loadSampleEwp()
        val output = EwpFileWriter.moveNodesInXml(
            input,
            fileMoves = listOf(
                EwpFileWriter.TreeFileMove(
                    rawPath = "\$PROJ_DIR$\\..\\src\\main.cpp",
                    sourceGroupPath = listOf("src"),
                    targetGroupPath = listOf("src"),
                ),
            ),
        )

        assertEquals(input, output)
    }

    @Test
    fun movesFileToTopLevel() {
        val input = loadSampleEwp()
        val output = EwpFileWriter.moveNodesInXml(
            input,
            fileMoves = listOf(
                EwpFileWriter.TreeFileMove(
                    rawPath = "\$PROJ_DIR$\\..\\src\\main.cpp",
                    sourceGroupPath = listOf("src"),
                    targetGroupPath = emptyList(),
                ),
            ),
        )

        assertTrue(output != input)
        assertTrue(output.contains("main.cpp"))
    }

    @Test
    fun movesGroupToAnotherGroup() {
        val input = loadSampleEwp()
        val output = EwpFileWriter.moveNodesInXml(
            input,
            groupMoves = listOf(
                EwpFileWriter.TreeGroupMove(
                    sourceGroupPath = listOf("src", "services"),
                    targetParentGroupPath = listOf("config"),
                ),
            ),
        )

        // services is now inside config, sorted before stm32.
        assertTrue(output.indexOf("<name>config</name>") < output.indexOf("<name>services</name>"))
        assertTrue(output.indexOf("<name>services</name>") < output.indexOf("<name>stm32</name>"))
        // Files inside services are preserved.
        assertTrue(output.indexOf("<name>services</name>") < output.indexOf("Service.cpp"))
        assertTrue(output.indexOf("Service.cpp") < output.indexOf("Service.hpp"))
    }

    @Test
    fun movesGroupToRoot() {
        val input = loadSampleEwp()
        val output = EwpFileWriter.moveNodesInXml(
            input,
            groupMoves = listOf(
                EwpFileWriter.TreeGroupMove(
                    sourceGroupPath = listOf("src", "services"),
                    targetParentGroupPath = emptyList(),
                ),
            ),
        )

        // services is now top-level between config and src (alphabetical: config < services < src)
        assertTrue(output.indexOf("<name>config</name>") < output.indexOf("<name>services</name>"))
        assertTrue(output.indexOf("<name>services</name>") < output.indexOf("<name>src</name>"))
        // projectSettings is after all groups
        assertTrue(output.indexOf("<name>src</name>") < output.indexOf("<projectSettings>"))
    }

    @Test
    fun movesGroupRejectsSelfAndDescendant() {
        val input = loadSampleEwp()
        val selfMove = EwpFileWriter.moveNodesInXml(
            input,
            groupMoves = listOf(
                EwpFileWriter.TreeGroupMove(
                    sourceGroupPath = listOf("src"),
                    targetParentGroupPath = listOf("src"),
                ),
            ),
        )
        assertEquals(input, selfMove)

        val descendantMove = EwpFileWriter.moveNodesInXml(
            input,
            groupMoves = listOf(
                EwpFileWriter.TreeGroupMove(
                    sourceGroupPath = listOf("src"),
                    targetParentGroupPath = listOf("src", "services"),
                ),
            ),
        )
        assertEquals(input, descendantMove)
    }

    @Test
    fun movesGroupRejectsSameParent() {
        val input = loadSampleEwp()
        val sameParent = EwpFileWriter.moveNodesInXml(
            input,
            groupMoves = listOf(
                EwpFileWriter.TreeGroupMove(
                    sourceGroupPath = listOf("config", "ra6m5"),
                    targetParentGroupPath = listOf("config"),
                ),
            ),
        )
        assertEquals(input, sameParent)

        val alreadyRoot = EwpFileWriter.moveNodesInXml(
            input,
            groupMoves = listOf(
                EwpFileWriter.TreeGroupMove(
                    sourceGroupPath = listOf("config"),
                    targetParentGroupPath = emptyList(),
                ),
            ),
        )
        assertEquals(input, alreadyRoot)
    }

    @Test
    fun movesGroupRejectsDuplicateNameInTarget() {
        val input = loadSampleEwp()
        // Add a group named "stm32" inside "src" first
        val withStm32InSrc = EwpFileWriter.createGroupInXml(input, listOf("src", "stm32"))
        // Try moving config/stm32 into src
        val output = EwpFileWriter.moveNodesInXml(
            withStm32InSrc,
            groupMoves = listOf(
                EwpFileWriter.TreeGroupMove(
                    sourceGroupPath = listOf("config", "stm32"),
                    targetParentGroupPath = listOf("src"),
                ),
            ),
        )

        assertEquals(withStm32InSrc, output)
    }

    @Test
    fun movesMultipleFilesAndGroupsTogether() {
        val input = loadSampleEwp()
        val output = EwpFileWriter.moveNodesInXml(
            input,
            fileMoves = listOf(
                EwpFileWriter.TreeFileMove(
                    rawPath = "\$PROJ_DIR$\\..\\src\\main.cpp",
                    sourceGroupPath = listOf("src"),
                    targetGroupPath = listOf("config"),
                ),
            ),
            groupMoves = listOf(
                EwpFileWriter.TreeGroupMove(
                    sourceGroupPath = listOf("src", "services", "watchdog"),
                    targetParentGroupPath = listOf("config"),
                ),
            ),
        )

        // In config: subgroups services and stm32, then files AppSettings.hpp, main.cpp, OtherSettings.hpp.
        assertTrue(output.indexOf("<name>config</name>") < output.indexOf("<name>stm32</name>"))
        assertTrue(output.indexOf("<name>stm32</name>") < output.indexOf("<name>watchdog</name>"))
        assertTrue(output.indexOf("<name>watchdog</name>") < output.indexOf("AppSettings.hpp"))
        assertTrue(output.indexOf("AppSettings.hpp") < output.indexOf("main.cpp"))
        assertTrue(output.indexOf("main.cpp") < output.indexOf("OtherSettings.hpp"))
    }

    private fun loadSampleEwp(): String {
        val path = Paths.get("src/test/testData/sample/sample.ewp").toAbsolutePath()
        return Files.readString(path)
    }
}
