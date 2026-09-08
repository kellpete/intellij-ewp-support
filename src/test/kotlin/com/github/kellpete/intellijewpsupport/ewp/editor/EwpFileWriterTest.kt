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

    private fun loadSampleEwp(): String {
        val path = Paths.get("src/test/testData/sample/sample.ewp").toAbsolutePath()
        return Files.readString(path)
    }
}
