package com.github.kellpete.intellijewpsupport.service

import com.github.kellpete.intellijewpsupport.ewp.addfiles.EwpProjectChangeListener
import com.github.kellpete.intellijewpsupport.model.EwpProject
import com.github.kellpete.intellijewpsupport.parser.EwpParser
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class EwpIndexService(private val project: Project) : Disposable {

    private val parser = EwpParser()
    private val cache = ConcurrentHashMap<VirtualFile, EwpProject>()

    init {
        project.messageBus.connect(this).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                var ewpChanged = false
                for (event in events) {
                    val file = event.file ?: continue
                    if (file.extension?.equals("ewp", ignoreCase = true) != true) continue
                    invalidate(file)
                    ewpChanged = true
                }
                if (ewpChanged) {
                    project.messageBus.syncPublisher(EwpProjectChangeListener.TOPIC).onEwpProjectsChanged()
                }
            }
        })
    }

    fun findEwpFiles(): List<VirtualFile> =
        FilenameIndex.getAllFilesByExt(project, "ewp", GlobalSearchScope.projectScope(project))
            .sortedBy { it.path }

    fun getProject(ewpFile: VirtualFile): EwpProject? {
        return try {
            cache.getOrPut(ewpFile) { parser.parse(ewpFile) }
        } catch (_: Exception) {
            null
        }
    }

    fun invalidate(ewpFile: VirtualFile) {
        cache.remove(ewpFile)
    }

    fun invalidateAll() {
        cache.clear()
    }

    override fun dispose() = Unit
}
