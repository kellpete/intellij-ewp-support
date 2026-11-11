package com.github.kellpete.intellijewpsupport.ewp.editor

import com.github.kellpete.intellijewpsupport.model.EwpFileEntry
import com.github.kellpete.intellijewpsupport.model.EwpGroup
import com.github.kellpete.intellijewpsupport.model.EwpProject
import com.intellij.testFramework.LightVirtualFile
import org.junit.Assert.assertEquals
import org.junit.Test

class EwpGroupMapperTest {

    private val ewpFile = LightVirtualFile("test.ewp")

    @Test
    fun usesObviousMappingForExistingDirectory() {
        val mapper = mapper(
            files = listOf(
                fileAt("/project/src/main.cpp", listOf("src")),
            ),
        )

        assertEquals(listOf("src"), mapper.mapDirectory("/project/src"))
    }

    @Test
    fun obviousMappingPrefersGroupWithMostFilesInDirectory() {
        val mapper = mapper(
            files = listOf(
                fileAt("/project/src/a.cpp", listOf("app")),
                fileAt("/project/src/b.cpp", listOf("app")),
                fileAt("/project/src/c.cpp", listOf("other")),
            ),
        )

        assertEquals(listOf("app"), mapper.mapDirectory("/project/src"))
    }

    @Test
    fun usesParentWalkToAddSubgroup() {
        val mapper = mapper(
            files = listOf(
                fileAt("/project/src/main.cpp", listOf("app")),
            ),
        )

        assertEquals(listOf("app", "tools"), mapper.mapDirectory("/project/src/tools"))
    }

    @Test
    fun usesShortestPathFallback() {
        val mapper = mapper(
            files = listOf(
                fileAt("/project/src/app/main.cpp", listOf("source", "application")),
            ),
        )

        assertEquals(listOf("source", "tools"), mapper.mapDirectory("/project/src/tools"))
    }

    @Test
    fun usesFilesystemFallbackWhenNoFilesExist() {
        val mapper = mapper(files = emptyList())

        assertEquals(listOf("src", "tools"), mapper.mapDirectory("/project/src/tools"))
    }

    @Test
    fun mapGroupToDirectoryUsesObviousMapping() {
        val mapper = mapper(
            files = listOf(
                fileAt("/project/src/main.cpp", listOf("src")),
            ),
        )

        assertEquals("/project/src", mapper.mapGroupToDirectory(listOf("src")))
    }

    @Test
    fun mapGroupToDirectoryUsesParentWalk() {
        val mapper = mapper(
            files = listOf(
                fileAt("/project/src/main.cpp", listOf("app")),
            ),
        )

        assertEquals("/project/src/tools", mapper.mapGroupToDirectory(listOf("app", "tools")))
    }

    @Test
    fun mapGroupToDirectoryUsesShortestPathFallback() {
        val mapper = mapper(
            files = listOf(
                fileAt("/project/src/app/main.cpp", listOf("source", "application")),
            ),
        )

        assertEquals("/project/src/tools", mapper.mapGroupToDirectory(listOf("source", "tools")))
    }

    @Test
    fun mapGroupToDirectoryUsesFilesystemFallbackWhenNoFilesExist() {
        val mapper = mapper(files = emptyList())

        assertEquals("/project/src/tools", mapper.mapGroupToDirectory(listOf("src", "tools")))
    }

    private fun mapper(files: List<Pair<String, List<String>>>): EwpGroupMapper {
        val groups = buildGroups(files)
        val project = EwpProject(ewpFile = ewpFile, groups = groups, sortIssues = emptyList())
        return EwpGroupMapper(project, "/project")
    }

    private fun buildGroups(files: List<Pair<String, List<String>>>): List<EwpGroup> {
        val rootGroups = mutableMapOf<String, EwpGroupBuilder>()
        for ((path, groupPath) in files) {
            val rootName = groupPath.first()
            val builder = rootGroups.getOrPut(rootName) { EwpGroupBuilder(rootName) }
            builder.addFile(path, groupPath.drop(1))
        }
        return rootGroups.values.map { it.build() }
    }

    private fun fileAt(resolvedPath: String, groupPath: List<String>): Pair<String, List<String>> =
        resolvedPath to groupPath

    private class EwpGroupBuilder(private val name: String) {
        private val subgroups = mutableMapOf<String, EwpGroupBuilder>()
        private val files = mutableListOf<EwpFileEntry>()

        fun addFile(resolvedPath: String, remainingGroupPath: List<String>) {
            if (remainingGroupPath.isEmpty()) {
                files += EwpFileEntry(rawPath = resolvedPath, resolvedPath = resolvedPath)
                return
            }
            val childName = remainingGroupPath.first()
            subgroups.getOrPut(childName) { EwpGroupBuilder(childName) }
                .addFile(resolvedPath, remainingGroupPath.drop(1))
        }

        fun build(): EwpGroup = EwpGroup(
            name = name,
            groups = subgroups.values.map { it.build() },
            files = files,
        )
    }
}
