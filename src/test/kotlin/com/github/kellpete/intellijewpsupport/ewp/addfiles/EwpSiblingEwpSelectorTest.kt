package com.github.kellpete.intellijewpsupport.ewp.addfiles

import com.github.kellpete.intellijewpsupport.model.EwpFileEntry
import com.github.kellpete.intellijewpsupport.model.EwpGroup
import com.github.kellpete.intellijewpsupport.model.EwpProject
import com.intellij.openapi.vfs.VirtualFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class EwpSiblingEwpSelectorTest {

    private val ewpA = mockEwp("firmware.ewp")
    private val ewpB = mockEwp("bootloader.ewp")

    @Test
    fun preselectsEwpWithSiblingInSameDirectory() {
        val projects = mapOf(
            ewpA to projectWithFile("/project/src/main.cpp", listOf("src")),
            ewpB to projectWithFile("/project/boot/main.cpp", listOf("boot")),
        )
        val newFile = mockFile("/project/src/helper.cpp")

        val selected = EwpSiblingEwpSelector.findEwpsWithSiblingFiles(
            listOf(newFile),
            listOf(ewpA, ewpB),
            projects,
        )

        assertEquals(setOf(ewpA), selected)
    }

    @Test
    fun needsDialogWhenFileMissingFromAtLeastOneEwp() {
        val projects = mapOf(
            ewpA to projectWithFile("/project/src/main.cpp", listOf("src")),
            ewpB to projectWithFile("/project/boot/main.cpp", listOf("boot")),
        )
        val newFile = mockFile("/project/src/helper.cpp")

        assertTrue(EwpSiblingEwpSelector.needsDialog(listOf(newFile), listOf(ewpA, ewpB), projects))
    }

    @Test
    fun skipsDialogWhenFileAlreadyInAllEwps() {
        val projects = mapOf(
            ewpA to projectWithFile("/project/src/helper.cpp", listOf("src")),
            ewpB to projectWithFile("/project/src/helper.cpp", listOf("src")),
        )
        val newFile = mockFile("/project/src/helper.cpp")

        assertFalse(EwpSiblingEwpSelector.needsDialog(listOf(newFile), listOf(ewpA, ewpB), projects))
    }

    private fun mockEwp(name: String): VirtualFile {
        val file = mock(VirtualFile::class.java)
        `when`(file.name).thenReturn(name)
        return file
    }

    private fun mockFile(path: String): VirtualFile {
        val file = mock(VirtualFile::class.java)
        `when`(file.path).thenReturn(path)
        val parent = mock(VirtualFile::class.java)
        val parentPath = path.substringBeforeLast('/')
        `when`(parent.path).thenReturn(parentPath)
        `when`(file.parent).thenReturn(parent)
        return file
    }

    private fun projectWithFile(resolvedPath: String, groupPath: List<String>): EwpProject {
        val ewpFile = mock(VirtualFile::class.java)
        val group = buildGroup(groupPath, resolvedPath)
        return EwpProject(ewpFile = ewpFile, groups = listOf(group), sortIssues = emptyList())
    }

    private fun buildGroup(groupPath: List<String>, resolvedPath: String): EwpGroup {
        if (groupPath.isEmpty()) {
            return EwpGroup(
                name = "root",
                groups = emptyList(),
                files = listOf(EwpFileEntry(rawPath = resolvedPath, resolvedPath = resolvedPath)),
            )
        }
        val name = groupPath.first()
        val child = buildGroup(groupPath.drop(1), resolvedPath)
        return EwpGroup(name = name, groups = listOf(child), files = emptyList())
    }
}
