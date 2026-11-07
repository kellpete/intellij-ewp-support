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
            groupPaths = listOf(listOf("src", "services", "parameter")),
        )

        assertFalse(output.contains("RedundantParameters"))
        assertTrue(output.contains("main.cpp"))
        assertFalse(output.contains("<name>parameter</name>"))
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
        assertTrue(output.contains("RedundantParameters.cpp"))
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
                "/project/src/services/parameter/RedundantParameters.cpp",
                "/project/src/services/parameter/RedundantParameters.hpp",
            ),
        )

        assertFalse(output.contains("RedundantParameters"))
        assertFalse(output.contains("<name>parameter</name>"))
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
                    rawPath = "\$PROJ_DIR$\\..\\src\\services\\parameter\\RedundantParameters.cpp",
                    sourceGroupPath = listOf("src", "services", "parameter"),
                    targetGroupPath = listOf("config"),
                ),
            ),
        )

        // RedundantParameters.cpp is now in config, sorted between NodeSettings.hpp and ThreadSettings.hpp
        assertTrue(output.indexOf("<name>config</name>") < output.indexOf("RedundantParameters.cpp"))
        assertTrue(output.indexOf("NodeSettings.hpp") < output.indexOf("RedundantParameters.cpp"))
        assertTrue(output.indexOf("RedundantParameters.cpp") < output.indexOf("ThreadSettings.hpp"))
        // parameter still has RedundantParameters.hpp
        assertTrue(output.indexOf("<name>parameter</name>") < output.indexOf("RedundantParameters.hpp"))
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
    fun movesFileRejectsDuplicateInTarget() {
        val input = loadSampleEwp()
        val output = EwpFileWriter.moveNodesInXml(
            input,
            fileMoves = listOf(
                EwpFileWriter.TreeFileMove(
                    rawPath = "\$PROJ_DIR$\\..\\config\\NodeSettings.hpp",
                    sourceGroupPath = listOf("config"),
                    targetGroupPath = listOf("config"),
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
                    sourceGroupPath = listOf("src", "services", "parameter"),
                    targetParentGroupPath = listOf("config"),
                ),
            ),
        )

        // parameter is now inside config, sorted before ra6m5 (alphabetical: parameter < ra6m5)
        assertTrue(output.indexOf("<name>config</name>") < output.indexOf("<name>parameter</name>"))
        assertTrue(output.indexOf("<name>parameter</name>") < output.indexOf("<name>ra6m5</name>"))
        // Files inside parameter are preserved
        assertTrue(output.indexOf("<name>parameter</name>") < output.indexOf("RedundantParameters.cpp"))
        assertTrue(output.indexOf("RedundantParameters.cpp") < output.indexOf("RedundantParameters.hpp"))
        // services group in src is preserved (not pruned)
        assertTrue(output.contains("<name>services</name>"))
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
        // Add a group named "ra6m5" inside "src" first
        val withRa6m5InSrc = EwpFileWriter.createGroupInXml(input, listOf("src", "ra6m5"))
        // Try moving config/ra6m5 into src
        val output = EwpFileWriter.moveNodesInXml(
            withRa6m5InSrc,
            groupMoves = listOf(
                EwpFileWriter.TreeGroupMove(
                    sourceGroupPath = listOf("config", "ra6m5"),
                    targetParentGroupPath = listOf("src"),
                ),
            ),
        )

        assertEquals(withRa6m5InSrc, output)
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
                    sourceGroupPath = listOf("src", "services", "parameter"),
                    targetParentGroupPath = listOf("config"),
                ),
            ),
        )

        // In config: subgroups parameter and ra6m5, files main.cpp, NodeSettings.hpp, ThreadSettings.hpp
        assertTrue(output.indexOf("<name>config</name>") < output.indexOf("<name>parameter</name>"))
        assertTrue(output.indexOf("<name>parameter</name>") < output.indexOf("<name>ra6m5</name>"))
        assertTrue(output.indexOf("<name>ra6m5</name>") < output.indexOf("main.cpp"))
        assertTrue(output.indexOf("main.cpp") < output.indexOf("NodeSettings.hpp"))
        assertTrue(output.indexOf("NodeSettings.hpp") < output.indexOf("ThreadSettings.hpp"))
    }

    private fun loadSampleEwp(): String {
        val path = Paths.get("src/test/testData/sample/sample.ewp").toAbsolutePath()
        return Files.readString(path)
    }
}
