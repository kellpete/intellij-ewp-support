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

    private fun loadSampleEwp(): String {
        val path = Paths.get("src/test/testData/sample/sample.ewp").toAbsolutePath()
        return Files.readString(path)
    }
}
