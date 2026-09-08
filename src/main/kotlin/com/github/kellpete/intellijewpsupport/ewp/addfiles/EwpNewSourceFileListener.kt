package com.github.kellpete.intellijewpsupport.ewp.addfiles

import com.github.kellpete.intellijewpsupport.MyBundle
import com.github.kellpete.intellijewpsupport.service.EwpIndexService
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.Alarm
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class EwpNewSourceFileListener(private val project: Project) : Disposable {

    private val pendingFiles = ConcurrentHashMap.newKeySet<VirtualFile>()
    private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, project)

    init {
        project.messageBus.connect(this).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                if (project.isDisposed) return

                val createdFiles = events.filterIsInstance<VFileCreateEvent>()
                    .mapNotNull { it.file }
                    .filter { file -> shouldConsider(file) }

                if (createdFiles.isEmpty()) return

                pendingFiles.addAll(createdFiles)
                scheduleDialog()
            }
        })
    }

    private fun shouldConsider(file: VirtualFile): Boolean {
        if (!EwpSourceFileExtensions.isSourceFile(file)) return false
        if (file.extension?.equals("ewp", ignoreCase = true) == true) return false

        val fileIndex = ProjectRootManager.getInstance(project).fileIndex
        return fileIndex.isInContent(file)
    }

    private fun scheduleDialog() {
        alarm.cancelAllRequests()
        alarm.addRequest({ showDialogIfNeeded() }, DEBOUNCE_MS)
    }

    private fun showDialogIfNeeded() {
        if (project.isDisposed) return

        val files = pendingFiles.toList()
        pendingFiles.clear()
        if (files.isEmpty()) return

        val indexService = project.service<EwpIndexService>()
        val ewpFiles = indexService.findEwpFiles()
        if (ewpFiles.isEmpty()) return

        val ewpProjects = ewpFiles.mapNotNull { ewpFile ->
            indexService.getProject(ewpFile)?.let { ewpFile to it }
        }.toMap()
        if (!EwpSiblingEwpSelector.needsDialog(files, ewpFiles, ewpProjects)) return

        val preselected = EwpSiblingEwpSelector.findEwpsWithSiblingFiles(files, ewpFiles, ewpProjects)
        AddToEwpDialog(project, files, ewpFiles, preselected).show()
    }

    override fun dispose() {
        alarm.cancelAllRequests()
        alarm.dispose()
    }

    companion object {
        private const val DEBOUNCE_MS = 400
    }
}
