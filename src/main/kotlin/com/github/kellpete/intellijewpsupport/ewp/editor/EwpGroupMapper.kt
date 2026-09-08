package com.github.kellpete.intellijewpsupport.ewp.editor

import com.github.kellpete.intellijewpsupport.model.EwpGroup
import com.github.kellpete.intellijewpsupport.model.EwpProject
import com.intellij.openapi.util.io.FileUtil

class EwpGroupMapper(
    private val ewpProject: EwpProject,
    private val projectRoot: String,
) {

    private data class FileLocation(
        val directory: String,
        val groupPath: List<String>,
    )

    private val fileLocations: List<FileLocation> by lazy { collectFileLocations(ewpProject.groups, emptyList()) }

    fun mapDirectory(targetDirectory: String): List<String> {
        val normalizedTarget = normalizePath(targetDirectory)
        val normalizedRoot = normalizePath(projectRoot)

        return obviousMapping(normalizedTarget)
            ?: parentWalkMapping(normalizedTarget, normalizedRoot)
            ?: shortestPathMapping(normalizedTarget, normalizedRoot)
            ?: filesystemFallback(normalizedTarget, normalizedRoot)
    }

    private fun obviousMapping(directory: String): List<String>? {
        val matches = fileLocations.filter { it.directory == directory }
        if (matches.isEmpty()) return null
        return matches.first().groupPath
    }

    private fun parentWalkMapping(directory: String, projectRoot: String): List<String>? {
        var current = directory
        while (current.length >= projectRoot.length) {
            val mapping = obviousMapping(current)
            if (mapping != null) {
                val subdirs = relativeSubdirectories(current, directory)
                return mapping + subdirs
            }
            if (current == projectRoot) break
            current = parentDirectory(current) ?: break
        }
        return null
    }

    private fun shortestPathMapping(directory: String, projectRoot: String): List<String>? {
        val reference = fileLocations.minByOrNull { it.directory.length } ?: return null

        val targetComponents = pathComponents(directory, projectRoot)
        val sharedLength = commonPrefixLength(reference.directory, directory, projectRoot)
        if (sharedLength == 0) return null

        val baseGroupPath = reference.groupPath.take(sharedLength)
        val extraComponents = targetComponents.drop(sharedLength)
        return baseGroupPath + extraComponents
    }

    private fun filesystemFallback(directory: String, projectRoot: String): List<String> {
        return pathComponents(directory, projectRoot)
    }

    private fun collectFileLocations(groups: List<EwpGroup>, parentPath: List<String>): List<FileLocation> {
        val result = mutableListOf<FileLocation>()
        for (group in groups) {
            val groupPath = parentPath + group.name
            for (file in group.files) {
                val directory = parentDirectoryOf(file.resolvedPath) ?: continue
                result += FileLocation(directory, groupPath)
            }
            result += collectFileLocations(group.groups, groupPath)
        }
        return result
    }

    private fun parentDirectoryOf(resolvedPath: String?): String? {
        if (resolvedPath == null) return null
        val normalized = normalizePath(resolvedPath)
        return parentDirectory(normalized)
    }

    private fun parentDirectory(path: String): String? {
        val lastSlash = path.lastIndexOf('/')
        if (lastSlash <= 0) return null
        return path.substring(0, lastSlash)
    }

    private fun relativeSubdirectories(fromDirectory: String, toDirectory: String): List<String> {
        if (fromDirectory == toDirectory) return emptyList()
        val fromComponents = pathComponents(toDirectory, fromDirectory)
        return fromComponents
    }

    private fun pathComponents(path: String, root: String): List<String> {
        val normalizedPath = normalizePath(path)
        val normalizedRoot = normalizePath(root)
        if (!normalizedPath.startsWith(normalizedRoot)) {
            return normalizedPath.split('/').filter { it.isNotEmpty() && it != ".." }
        }
        val relative = normalizedPath.removePrefix(normalizedRoot).removePrefix("/")
        return if (relative.isEmpty()) emptyList() else relative.split('/').filter { it.isNotEmpty() }
    }

    private fun commonPrefixLength(first: String, second: String, projectRoot: String): Int {
        val firstComponents = pathComponents(first, projectRoot)
        val secondComponents = pathComponents(second, projectRoot)
        var length = 0
        while (length < firstComponents.size && length < secondComponents.size &&
            firstComponents[length] == secondComponents[length]
        ) {
            length++
        }
        return length
    }

    private fun normalizePath(path: String): String =
        FileUtil.toSystemIndependentName(path.trimEnd('/'))
}
