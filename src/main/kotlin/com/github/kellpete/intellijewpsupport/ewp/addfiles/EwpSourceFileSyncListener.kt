package com.github.kellpete.intellijewpsupport.ewp.addfiles

import com.github.kellpete.intellijewpsupport.service.EwpIndexService
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.util.Alarm
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps `.ewp` files in sync with IDE-driven source file lifecycle events.
 *
 * - **Create:** offers the Add-to-EWP dialog (same UX as VCS "Add Files").
 * - **Delete / move / rename:** automatically updates every EWP that already lists the file.
 *
 * External/refresh VFS events (e.g. git checkout) are ignored.
 */
@Service(Service.Level.PROJECT)
class EwpSourceFileSyncListener(private val project: Project) : Disposable {

    private val lock = Any()
    private val pendingCreates = ConcurrentHashMap.newKeySet<VirtualFile>()
    private val pendingDeletes = ConcurrentHashMap.newKeySet<String>()
    private val pendingMoves = ConcurrentHashMap<String, String>()
    private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, project)

    init {
        project.messageBus.connect(this).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {

            override fun after(events: List<VFileEvent>) {
                if (project.isDisposed) return

                var schedule = false

                for (event in events) {
                    when (event) {
                        is VFileDeleteEvent -> {
                            if (collectDelete(event)) {
                                schedule = true
                            }
                        }

                        is VFileCreateEvent -> {
                            if (!shouldConsiderCreate(event)) continue
                            val file = event.file ?: continue
                            pendingCreates.add(file)
                            schedule = true
                        }

                        is VFileMoveEvent -> {
                            if (!shouldConsiderMove(event)) continue
                            val file = event.file
                            val oldPath = normalizePath(event.oldParent.path + "/" + file.name)
                            val newPath = normalizePath(file.path)
                            synchronized(lock) {
                                pendingDeletes.remove(oldPath)
                                pendingDeletes.remove(newPath)
                                pendingMoves[oldPath] = newPath
                            }
                            schedule = true
                        }

                        is VFilePropertyChangeEvent -> {
                            if (!shouldConsiderRename(event)) continue
                            val file = event.file
                            val oldName = event.oldValue as? String ?: continue
                            val parentPath = file.parent?.path ?: continue
                            val oldPath = normalizePath("$parentPath/$oldName")
                            val newPath = normalizePath(file.path)
                            synchronized(lock) {
                                pendingDeletes.remove(oldPath)
                                pendingDeletes.remove(newPath)
                                pendingMoves[oldPath] = newPath
                            }
                            schedule = true
                        }
                    }
                }

                if (pendingDeletes.isNotEmpty()) {
                    schedule = true
                }

                if (schedule) {
                    scheduleFlush()
                }
            }
        })
    }

    private fun collectDelete(event: VFileDeleteEvent): Boolean {
        if (!shouldConsiderDelete(event)) return false
        val path = normalizePath(event.path)
        pendingDeletes.add(path)
        return true
    }

    private fun shouldConsiderDelete(event: VFileDeleteEvent): Boolean {
        if (event.isFromRefresh) return false
        val file = event.file
        if (file.isDirectory) {
            return isInContentRoot(event.path) || (file.isValid && isInContent(file))
        }
        if (!EwpSourceFileExtensions.isSourceFilePath(event.path) &&
            !(file.isValid && EwpSourceFileExtensions.isSourceFile(file))
        ) {
            return false
        }
        return isInContentRoot(event.path) || (file.isValid && isInContent(file))
    }

    private fun shouldConsiderCreate(event: VFileCreateEvent): Boolean {
        // Same distinction as VCS "when files are created": ignore external/refresh creates
        // (git checkout, sync from disk, etc.).
        if (event.isFromRefresh) return false
        val file = event.file ?: return false
        if (!EwpSourceFileExtensions.isSourceFile(file)) return false
        return isInContent(file)
    }

    private fun shouldConsiderMove(event: VFileMoveEvent): Boolean {
        if (event.isFromRefresh) return false
        val file = event.file
        if (!EwpSourceFileExtensions.isSourceFile(file)) return false
        return isInContent(file) || isInContent(event.oldParent)
    }

    private fun shouldConsiderRename(event: VFilePropertyChangeEvent): Boolean {
        if (event.isFromRefresh) return false
        if (event.propertyName != VirtualFile.PROP_NAME) return false
        val file = event.file
        if (file.isDirectory) return false
        val oldName = event.oldValue as? String ?: return false
        val wasSource = EwpSourceFileExtensions.isSourceFilePath(oldName)
        val isSource = EwpSourceFileExtensions.isSourceFile(file)
        if (!wasSource && !isSource) return false
        return isInContent(file)
    }

    private fun isInContent(file: VirtualFile): Boolean {
        val fileIndex = ProjectRootManager.getInstance(project).fileIndex
        return fileIndex.isInContent(file)
    }

    private fun isInContentRoot(path: String): Boolean {
        val normalized = normalizePath(path)
        return ProjectRootManager.getInstance(project).contentRoots.any { root ->
            val rootPath = normalizePath(root.path)
            FileUtil.pathsEqual(normalized, rootPath) ||
                    FileUtil.startsWith(normalized, rootPath)
        }
    }

    private fun scheduleFlush() {
        alarm.cancelAllRequests()
        alarm.addRequest({ flushPending() }, DEBOUNCE_MS)
    }

    private fun flushPending() {
        if (project.isDisposed) return

        val (deletes, moves, creates) = synchronized(lock) {
            val drainedDeletes = pendingDeletes.toList()
            pendingDeletes.clear()
            val drainedMoves = pendingMoves.entries.map { (oldPath, newPath) ->
                EwpSyncFilesService.PathMove(oldPath, newPath)
            }
            pendingMoves.clear()
            val deleteSet = drainedDeletes.toSet()
            val drainedCreates = pendingCreates.filter { file ->
                file.isValid && normalizePath(file.path) !in deleteSet
            }
            pendingCreates.clear()
            Triple(drainedDeletes, drainedMoves, drainedCreates)
        }

        if (deletes.isNotEmpty()) {
            EwpSyncFilesService.removePathsFromAllEwps(project, deletes)
        }

        if (moves.isNotEmpty()) {
            EwpSyncFilesService.movePathsInAllEwps(project, moves)
        }

        if (creates.isNotEmpty()) {
            showCreateDialogIfNeeded(creates)
        }
    }

    private fun showCreateDialogIfNeeded(files: List<VirtualFile>) {
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

    private fun normalizePath(path: String): String =
        FileUtil.toSystemIndependentName(path.trimEnd('/'))

    companion object {
        private const val DEBOUNCE_MS = 400
    }
}
