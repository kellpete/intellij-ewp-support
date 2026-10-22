package com.github.kellpete.intellijewpsupport.model

import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Paths

object EwpMissingFileValidator {

    fun findMissingFiles(groups: List<EwpGroup>, parentPath: List<String> = emptyList()): List<EwpMissingFileIssue> {
        val issues = mutableListOf<EwpMissingFileIssue>()
        for (group in groups) {
            val path = parentPath + group.name
            for (file in group.files) {
                if (!existsOnDisk(file.resolvedPath)) {
                    issues += EwpMissingFileIssue(
                        groupPath = path,
                        rawPath = file.rawPath,
                        resolvedPath = file.resolvedPath,
                    )
                }
            }
            issues += findMissingFiles(group.groups, path)
        }
        return issues
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
