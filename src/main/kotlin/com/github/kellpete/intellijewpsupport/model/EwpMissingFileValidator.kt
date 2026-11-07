package com.github.kellpete.intellijewpsupport.model

import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Paths

object EwpMissingFileValidator {

    fun findMissingFiles(groups: List<EwpGroup>, parentPath: List<String> = emptyList()): List<EwpMissingFileIssue> {
        val issues = mutableListOf<EwpMissingFileIssue>()
        for (group in groups) {
            val path = parentPath + group.name
            issues += findMissingInFiles(group.files, path)
            issues += findMissingFiles(group.groups, path)
        }
        return issues
    }

    fun findMissingInFiles(files: List<EwpFileEntry>, groupPath: List<String>): List<EwpMissingFileIssue> =
        files.filter { !existsOnDisk(it.resolvedPath) }.map {
            EwpMissingFileIssue(groupPath = groupPath, rawPath = it.rawPath, resolvedPath = it.resolvedPath)
        }

    private fun existsOnDisk(resolvedPath: String?): Boolean {
        if (resolvedPath.isNullOrBlank()) return false
        return try {
            Files.exists(Paths.get(resolvedPath))
        } catch (_: InvalidPathException) {
            false
        } catch (_: RuntimeException) {
            false
        }
    }
}
