package com.github.kellpete.intellijewpsupport.ewp.addfiles

import com.github.kellpete.intellijewpsupport.model.EwpGroup
import com.github.kellpete.intellijewpsupport.model.EwpProject
import com.github.kellpete.intellijewpsupport.path.EwpPathResolver
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile

object EwpSiblingEwpSelector {

    fun findEwpsWithSiblingFiles(
        newFiles: Collection<VirtualFile>,
        ewpFiles: List<VirtualFile>,
        ewpProjects: Map<VirtualFile, EwpProject>,
    ): Set<VirtualFile> {
        val newDirectories = newFiles.mapNotNull { file ->
            file.parent?.path?.let { normalizePath(it) }
        }.toSet()
        if (newDirectories.isEmpty()) return emptySet()

        return ewpFiles.filter { ewpFile ->
            val ewpProject = ewpProjects[ewpFile] ?: return@filter false
            hasFileInDirectories(ewpProject, ewpFile, newDirectories)
        }.toSet()
    }

    fun isFileListedInEwp(ewpFile: VirtualFile, ewpProject: EwpProject, targetFile: VirtualFile): Boolean {
        val targetPath = normalizePath(targetFile.path)
        return collectResolvedPaths(ewpProject.groups, ewpFile).contains(targetPath)
    }

    fun needsDialog(
        newFiles: Collection<VirtualFile>,
        ewpFiles: List<VirtualFile>,
        ewpProjects: Map<VirtualFile, EwpProject>,
    ): Boolean {
        if (ewpFiles.isEmpty() || newFiles.isEmpty()) return false

        return newFiles.any { newFile ->
            ewpFiles.any { ewpFile ->
                val ewpProject = ewpProjects[ewpFile] ?: return@any true
                !isFileListedInEwp(ewpFile, ewpProject, newFile)
            }
        }
    }

    private fun hasFileInDirectories(
        ewpProject: EwpProject,
        ewpFile: VirtualFile,
        directories: Set<String>,
    ): Boolean {
        return collectResolvedPaths(ewpProject.groups, ewpFile).any { resolvedPath ->
            parentDirectory(resolvedPath) in directories
        }
    }

    private fun collectResolvedPaths(groups: List<EwpGroup>, ewpFile: VirtualFile): Set<String> {
        val paths = mutableSetOf<String>()
        collectResolvedPaths(groups, ewpFile, paths)
        return paths
    }

    private fun collectResolvedPaths(groups: List<EwpGroup>, ewpFile: VirtualFile, paths: MutableSet<String>) {
        for (group in groups) {
            for (file in group.files) {
                val resolved = file.resolvedPath ?: EwpPathResolver.resolve(ewpFile, file.rawPath)
                if (resolved != null) {
                    paths += normalizePath(resolved)
                }
            }
            collectResolvedPaths(group.groups, ewpFile, paths)
        }
    }

    private fun parentDirectory(path: String): String? {
        val lastSlash = path.lastIndexOf('/')
        if (lastSlash <= 0) return null
        return path.substring(0, lastSlash)
    }

    private fun normalizePath(path: String): String =
        FileUtil.toSystemIndependentName(path.trimEnd('/'))
}
