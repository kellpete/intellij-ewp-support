package com.github.kellpete.intellijewpsupport.ewp.addfiles

import com.github.kellpete.intellijewpsupport.ewp.editor.EwpFileWriter
import com.github.kellpete.intellijewpsupport.ewp.editor.EwpGroupMapper
import com.github.kellpete.intellijewpsupport.service.EwpIndexService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile

object EwpAddFilesService {

    fun addFilesToEwps(
        project: Project,
        newFiles: List<VirtualFile>,
        selectedEwps: List<VirtualFile>,
    ) {
        if (newFiles.isEmpty() || selectedEwps.isEmpty()) return

        val indexService = project.service<EwpIndexService>()
        val contentRoots = ProjectRootManager.getInstance(project).contentRoots.map { it.path }

        for (ewpFile in selectedEwps) {
            val ewpProject = indexService.getProject(ewpFile) ?: continue
            val projectRoot = findProjectRoot(newFiles, contentRoots) ?: continue
            val mapper = EwpGroupMapper(ewpProject, projectRoot)
            val additions = newFiles.mapNotNull { file ->
                val parentDir = file.parent?.path ?: return@mapNotNull null
                EwpFileWriter.FileAddition(
                    absolutePath = file.path,
                    groupPath = mapper.mapDirectory(parentDir),
                )
            }
            EwpFileWriter.addFiles(ewpFile, project, additions)
            indexService.invalidate(ewpFile)
        }

        project.messageBus.syncPublisher(EwpProjectChangeListener.TOPIC).onEwpProjectsChanged()
    }

    private fun findProjectRoot(
        newFiles: List<VirtualFile>,
        contentRoots: List<String>,
    ): String? {
        val candidates = contentRoots.filter { root ->
            newFiles.all { file -> file.path.startsWith(root) }
        }
        return candidates.minByOrNull { it.length }
    }
}

interface EwpProjectChangeListener {
    fun onEwpProjectsChanged()

    companion object {
        val TOPIC = com.intellij.util.messages.Topic.create(
            "EWP Project Changed",
            EwpProjectChangeListener::class.java,
        )
    }
}
