package com.github.kellpete.intellijewpsupport.toolWindow

import com.github.kellpete.intellijewpsupport.ewp.editor.EwpFileWriter
import com.github.kellpete.intellijewpsupport.model.EwpFileEntry
import com.github.kellpete.intellijewpsupport.model.EwpGroup
import com.github.kellpete.intellijewpsupport.model.EwpProject
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import javax.swing.JComponent
import javax.swing.JTree
import javax.swing.SwingUtilities
import javax.swing.TransferHandler
import javax.swing.tree.TreePath

data class DraggedFile(
    val entry: EwpFileEntry,
    val sourceGroupPath: List<String>,
)

data class DraggedGroup(
    val group: EwpGroup,
    val sourceGroupPath: List<String>,
)

data class EwpDragData(
    val ewpFile: VirtualFile,
    val files: List<DraggedFile>,
    val groups: List<DraggedGroup>,
)

class EwpTreeTransferable(private val data: EwpDragData) : Transferable {

    companion object {
        val DATA_FLAVOR: DataFlavor = DataFlavor(
            "${DataFlavor.javaJVMLocalObjectMimeType};class=\"${EwpDragData::class.java.name}\"",
            "EWP Drag Nodes",
            EwpDragData::class.java.classLoader,
        )
    }

    override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DATA_FLAVOR)

    override fun isDataFlavorSupported(flavor: DataFlavor?): Boolean {
        if (flavor == null) return false
        return flavor == DATA_FLAVOR ||
                (flavor.isMimeTypeEqual(DataFlavor.javaJVMLocalObjectMimeType) &&
                        flavor.representationClass == EwpDragData::class.java)
    }

    override fun getTransferData(flavor: DataFlavor?): Any {
        if (!isDataFlavorSupported(flavor)) {
            throw UnsupportedFlavorException(flavor)
        }
        return data
    }
}

class EwpTreeTransferHandler(
    private val project: Project,
    private val getSelectedEwpFile: () -> VirtualFile?,
    private val onNodesMoved: (targetGroupPath: List<String>, movedGroupNames: List<String>) -> Unit,
) : TransferHandler() {

    companion object {
        @Volatile
        var activeDragData: EwpDragData? = null
            internal set
    }

    override fun getSourceActions(c: JComponent): Int = MOVE

    override fun createTransferable(c: JComponent): Transferable? {
        val tree = c as? JTree ?: return null
        val selectedEwp = getSelectedEwpFile() ?: return null
        val selectionPaths = tree.selectionPaths?.takeIf { it.isNotEmpty() }
            ?: tree.leadSelectionPath?.let { arrayOf(it) }
            ?: return null

        val topPaths = filterTopLevelPaths(selectionPaths)
        val files = mutableListOf<DraggedFile>()
        val groups = mutableListOf<DraggedGroup>()

        for (path in topPaths) {
            when (val node = EwpTreeStructure.extractNode(path.lastPathComponent)) {
                is EwpTreeNode.Root -> continue
                is EwpTreeNode.GroupNode -> {
                    groups += DraggedGroup(node.group, node.groupPath)
                }

                is EwpTreeNode.FileNode -> {
                    files += DraggedFile(node.entry, node.groupPath)
                }

                null -> continue
            }
        }

        if (files.isEmpty() && groups.isEmpty()) {
            return null
        }

        val dragData = EwpDragData(selectedEwp, files, groups)
        activeDragData = dragData
        return EwpTreeTransferable(dragData)
    }

    override fun canImport(support: TransferSupport): Boolean {
        if (!support.isDrop) return false
        if (activeDragData == null && !support.isDataFlavorSupported(EwpTreeTransferable.DATA_FLAVOR)) return false

        val currentEwp = getSelectedEwpFile() ?: return false
        val dropLocation = support.dropLocation as? JTree.DropLocation ?: return false
        val targetPath = dropLocation.path ?: return false
        val targetNode = EwpTreeStructure.extractNode(targetPath.lastPathComponent) ?: return false

        val data = activeDragData ?: extractDragData(support) ?: return false

        if (data.ewpFile != currentEwp) return false

        val target = resolveDropTarget(targetPath, targetNode) ?: return false
        val valid = isValidDrop(data, target)
        support.setShowDropLocation(valid)
        (support.component as? JTree)?.repaint()
        return valid
    }

    override fun importData(support: TransferSupport): Boolean {
        if (!canImport(support)) return false

        val currentEwp = getSelectedEwpFile() ?: return false
        val dropLocation = support.dropLocation as? JTree.DropLocation ?: return false
        val targetPath = dropLocation.path ?: return false
        val targetNode = EwpTreeStructure.extractNode(targetPath.lastPathComponent) ?: return false

        val data = activeDragData ?: extractDragData(support) ?: return false
        activeDragData = null

        val target = resolveDropTarget(targetPath, targetNode) ?: return false

        val fileMoves = data.files.map { file ->
            EwpFileWriter.TreeFileMove(
                rawPath = file.entry.rawPath,
                sourceGroupPath = file.sourceGroupPath,
                targetGroupPath = target.targetGroupPath,
            )
        }

        val groupMoves = data.groups.map { group ->
            EwpFileWriter.TreeGroupMove(
                sourceGroupPath = group.sourceGroupPath,
                targetParentGroupPath = target.targetGroupPath,
            )
        }

        val success = EwpFileWriter.moveNodes(
            ewpFile = currentEwp,
            project = project,
            fileMoves = fileMoves,
            groupMoves = groupMoves,
        )

        if (success) {
            val movedGroupNames = data.groups.map { it.group.name }
            SwingUtilities.invokeLater {
                onNodesMoved(target.targetGroupPath, movedGroupNames)
            }
            return true
        }

        return false
    }

    override fun exportDone(source: JComponent, data: Transferable?, action: Int) {
        activeDragData = null
        (source as? JTree)?.repaint()
    }

    data class DropTargetInfo(
        val targetGroupPath: List<String>,
        val targetGroup: EwpGroup?,
        val rootProject: EwpProject?,
        val isRoot: Boolean,
    )

    fun resolveDropTarget(targetPath: TreePath, targetNode: EwpTreeNode): DropTargetInfo? {
        return when (targetNode) {
            is EwpTreeNode.GroupNode -> DropTargetInfo(
                targetGroupPath = targetNode.groupPath,
                targetGroup = targetNode.group,
                rootProject = null,
                isRoot = false,
            )

            is EwpTreeNode.Root -> DropTargetInfo(
                targetGroupPath = emptyList(),
                targetGroup = null,
                rootProject = targetNode.project,
                isRoot = true,
            )

            is EwpTreeNode.FileNode -> {
                val parentPath = targetPath.parentPath ?: return null
                val parentNode = EwpTreeStructure.extractNode(parentPath.lastPathComponent) ?: return null
                when (parentNode) {
                    is EwpTreeNode.GroupNode -> DropTargetInfo(
                        targetGroupPath = parentNode.groupPath,
                        targetGroup = parentNode.group,
                        rootProject = null,
                        isRoot = false,
                    )

                    is EwpTreeNode.Root -> DropTargetInfo(
                        targetGroupPath = emptyList(),
                        targetGroup = null,
                        rootProject = parentNode.project,
                        isRoot = true,
                    )

                    is EwpTreeNode.FileNode -> null
                }
            }
        }
    }

    fun isValidDrop(data: EwpDragData, target: DropTargetInfo): Boolean {
        if (data.files.isEmpty() && data.groups.isEmpty()) return false

        if (data.files.isNotEmpty()) {
            val targetFiles = if (target.isRoot) {
                target.rootProject?.files ?: return false
            } else {
                target.targetGroup?.files ?: return false
            }

            val draggedRawPaths = data.files.map { normalizePath(it.entry.rawPath) }
            if (draggedRawPaths.distinct().size != draggedRawPaths.size) return false

            for (file in data.files) {
                if (file.sourceGroupPath == target.targetGroupPath) return false
                val normalizedRaw = normalizePath(file.entry.rawPath)
                val alreadyExists = targetFiles.any {
                    normalizePath(it.rawPath) == normalizedRaw
                }
                if (alreadyExists) return false
            }
        }

        if (data.groups.isNotEmpty()) {
            val existingChildGroupNames = if (target.isRoot) {
                target.rootProject?.groups?.map { it.name } ?: emptyList()
            } else {
                target.targetGroup?.groups?.map { it.name } ?: emptyList()
            }

            val draggedGroupNames = data.groups.map { it.group.name.lowercase() }
            if (draggedGroupNames.distinct().size != draggedGroupNames.size) return false

            for (group in data.groups) {
                if (EwpFileWriter.isDescendantOrSelf(group.sourceGroupPath, target.targetGroupPath)) return false
                val currentParent = group.sourceGroupPath.dropLast(1)
                if (currentParent == target.targetGroupPath) return false
                if (existingChildGroupNames.any { it.equals(group.group.name, ignoreCase = true) }) return false
            }
        }

        return true
    }

    fun filterTopLevelPaths(paths: Array<TreePath>): List<TreePath> {
        val pathList = paths.toList()
        return pathList.filter { candidate ->
            pathList.none { other -> other != candidate && other.isDescendant(candidate) }
        }
    }

    private fun extractDragData(support: TransferSupport): EwpDragData? {
        return try {
            if (support.isDataFlavorSupported(EwpTreeTransferable.DATA_FLAVOR)) {
                support.transferable.getTransferData(EwpTreeTransferable.DATA_FLAVOR) as? EwpDragData
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun normalizePath(path: String): String =
        path.trim().replace('\\', '/')
}
