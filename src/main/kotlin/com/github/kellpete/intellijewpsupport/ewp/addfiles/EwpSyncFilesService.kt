package com.github.kellpete.intellijewpsupport.ewp.addfiles

import com.github.kellpete.intellijewpsupport.ewp.editor.EwpFileWriter
import com.github.kellpete.intellijewpsupport.ewp.editor.EwpGroupMapper
import com.github.kellpete.intellijewpsupport.model.EwpGroup
import com.github.kellpete.intellijewpsupport.model.EwpProject
import com.github.kellpete.intellijewpsupport.path.EwpPathResolver
import com.github.kellpete.intellijewpsupport.service.EwpIndexService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile

object EwpSyncFilesService {

    fun removePathsFromAllEwps(project: Project, absolutePaths: Collection<String>) {
        if (absolutePaths.isEmpty()) return

        val indexService = project.service<EwpIndexService>()
        val ewpFiles = indexService.findEwpFiles()
        if (ewpFiles.isEmpty()) return

        val normalizedTargets = absolutePaths.map { normalizePath(it) }.toSet()
        var changed = false

        for (ewpFile in ewpFiles) {
            val ewpProject = indexService.getProject(ewpFile) ?: continue
            val pathsToRemove = collectMatchingListedPaths(ewpProject, ewpFile, normalizedTargets)
            if (pathsToRemove.isEmpty()) continue

            EwpFileWriter.removeFiles(ewpFile, project, pathsToRemove)
            indexService.invalidate(ewpFile)
            changed = true
        }

        if (changed) {
            project.messageBus.syncPublisher(EwpProjectChangeListener.TOPIC).onEwpProjectsChanged()
        }
    }

    fun movePathsInAllEwps(project: Project, moves: List<PathMove>) {
        if (moves.isEmpty()) return

        val indexService = project.service<EwpIndexService>()
        val ewpFiles = indexService.findEwpFiles()
        if (ewpFiles.isEmpty()) return

        val contentRoots = ProjectRootManager.getInstance(project).contentRoots.map { it.path }
        var changed = false

        for (ewpFile in ewpFiles) {
            val ewpProject = indexService.getProject(ewpFile) ?: continue
            val fileMoves = moves.mapNotNull { move ->
                val oldPath = normalizePath(move.oldAbsolutePath)
                if (!isPathListed(ewpProject, ewpFile, oldPath)) return@mapNotNull null

                val newParent = parentDirectory(move.newAbsolutePath) ?: return@mapNotNull null
                val sameDirectory = parentDirectory(move.oldAbsolutePath) == newParent
                val groupPath = if (sameDirectory) {
                    findGroupPathForFile(ewpProject, ewpFile, oldPath)
                        ?: mapGroupPath(ewpProject, contentRoots, newParent, move.newAbsolutePath)
                } else {
                    mapGroupPath(ewpProject, contentRoots, newParent, move.newAbsolutePath)
                } ?: return@mapNotNull null

                EwpFileWriter.FileMove(
                    oldAbsolutePath = move.oldAbsolutePath,
                    newAbsolutePath = move.newAbsolutePath,
                    newGroupPath = groupPath,
                )
            }

            if (fileMoves.isEmpty()) continue

            EwpFileWriter.moveFiles(ewpFile, project, fileMoves)
            indexService.invalidate(ewpFile)
            changed = true
        }

        if (changed) {
            project.messageBus.syncPublisher(EwpProjectChangeListener.TOPIC).onEwpProjectsChanged()
        }
    }

    data class PathMove(
        val oldAbsolutePath: String,
        val newAbsolutePath: String,
    )

    private fun mapGroupPath(
        ewpProject: EwpProject,
        contentRoots: List<String>,
        newParent: String,
        newAbsolutePath: String,
    ): List<String>? {
        val projectRoot = findProjectRoot(listOf(newAbsolutePath), contentRoots) ?: return null
        return EwpGroupMapper(ewpProject, projectRoot).mapDirectory(newParent)
    }

    private fun collectMatchingListedPaths(
        ewpProject: EwpProject,
        ewpFile: VirtualFile,
        normalizedTargets: Set<String>,
    ): List<String> {
        val matched = mutableListOf<String>()
        collectResolvedPaths(ewpProject.groups, ewpFile) { resolved ->
            val normalized = normalizePath(resolved)
            if (normalizedTargets.any { target -> pathEqualsOrUnder(normalized, target) }) {
                matched += normalized
            }
        }
        return matched
    }

    private fun isPathListed(ewpProject: EwpProject, ewpFile: VirtualFile, normalizedAbsolutePath: String): Boolean {
        var found = false
        collectResolvedPaths(ewpProject.groups, ewpFile) { resolved ->
            if (normalizePath(resolved) == normalizedAbsolutePath) {
                found = true
            }
        }
        return found
    }

    private fun findGroupPathForFile(
        ewpProject: EwpProject,
        ewpFile: VirtualFile,
        normalizedAbsolutePath: String,
    ): List<String>? = findGroupPathForFile(ewpProject.groups, ewpFile, normalizedAbsolutePath, emptyList())
    private fun findGroupPathForFile(
        groups: List<EwpGroup>,
        ewpFile: VirtualFile,
        normalizedAbsolutePath: String,
        parentPath: List<String>,
    ): List<String>? {
        for (group in groups) {
            val groupPath = parentPath + group.name
            for (file in group.files) {
                val resolved = file.resolvedPath ?: EwpPathResolver.resolve(ewpFile, file.rawPath)
                if (resolved != null && normalizePath(resolved) == normalizedAbsolutePath) {
                    return groupPath
                }
            }
            findGroupPathForFile(group.groups, ewpFile, normalizedAbsolutePath, groupPath)?.let { return it }
        }
        return null
    }

    private fun collectResolvedPaths(
        groups: List<EwpGroup>,
        ewpFile: VirtualFile,
        consumer: (String) -> Unit,
    ) {
        for (group in groups) {
            for (file in group.files) {
                val resolved = file.resolvedPath ?: EwpPathResolver.resolve(ewpFile, file.rawPath)
                if (resolved != null) {
                    consumer(resolved)
                }
            }
            collectResolvedPaths(group.groups, ewpFile, consumer)
        }
    }

    private fun pathEqualsOrUnder(path: String, targetOrDirectory: String): Boolean {
        if (path == targetOrDirectory) return true
        val prefix = targetOrDirectory.trimEnd('/') + "/"
        return path.startsWith(prefix)
    }

    private fun findProjectRoot(absolutePaths: List<String>, contentRoots: List<String>): String? {
        val candidates = contentRoots.filter { root ->
            absolutePaths.all { path -> normalizePath(path).startsWith(normalizePath(root)) }
        }
        return candidates.minByOrNull { it.length }
    }

    private fun parentDirectory(path: String): String? {
        val normalized = normalizePath(path)
        val lastSlash = normalized.lastIndexOf('/')
        if (lastSlash <= 0) return null
        return normalized.substring(0, lastSlash)
    }

    private fun normalizePath(path: String): String =
        FileUtil.toSystemIndependentName(path.trimEnd('/'))
}
