package com.github.kellpete.intellijewpsupport.toolWindow

import com.github.kellpete.intellijewpsupport.model.EwpFileEntry
import com.github.kellpete.intellijewpsupport.model.EwpGroup
import com.github.kellpete.intellijewpsupport.model.EwpProject
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

class EwpTreeTransferHandlerTest {

    private val mockProject: Project = mock(Project::class.java)
    private val mockEwpFile: VirtualFile = mock(VirtualFile::class.java)
    private val handler = EwpTreeTransferHandler(
        project = mockProject,
        getSelectedEwpFile = { mockEwpFile },
        onNodesMoved = { _, _ -> },
    )

    @Test
    fun filterTopLevelPathsKeepsOnlyAncestors() {
        val root = DefaultMutableTreeNode("root")
        val src = DefaultMutableTreeNode("src")
        val services = DefaultMutableTreeNode("services")
        val file = DefaultMutableTreeNode("main.cpp")
        root.add(src)
        src.add(services)
        src.add(file)

        val srcPath = TreePath(arrayOf(root, src))
        val servicesPath = TreePath(arrayOf(root, src, services))
        val filePath = TreePath(arrayOf(root, src, file))

        val result = handler.filterTopLevelPaths(arrayOf(srcPath, servicesPath, filePath))
        assertEquals(1, result.size)
        assertEquals(srcPath, result[0])
    }

    @Test
    fun filterTopLevelPathsKeepsIndependentSiblings() {
        val root = DefaultMutableTreeNode("root")
        val src = DefaultMutableTreeNode("src")
        val config = DefaultMutableTreeNode("config")
        root.add(src)
        root.add(config)

        val srcPath = TreePath(arrayOf(root, src))
        val configPath = TreePath(arrayOf(root, config))

        val result = handler.filterTopLevelPaths(arrayOf(srcPath, configPath))
        assertEquals(2, result.size)
        assertTrue(result.contains(srcPath))
        assertTrue(result.contains(configPath))
    }

    @Test
    fun resolveDropTargetForGroupNode() {
        val group = EwpGroup("src", emptyList(), emptyList())
        val node = EwpTreeNode.GroupNode(group, listOf("src"))
        val treeNode = DefaultMutableTreeNode(node)
        val path = TreePath(arrayOf(treeNode))

        val target = handler.resolveDropTarget(path, node)
        assertNotNull(target)
        assertEquals(listOf("src"), target?.targetGroupPath)
        assertFalse(target!!.isRoot)
    }

    @Test
    fun resolveDropTargetForRootNode() {
        val project = EwpProject(mockEwpFile, emptyList(), emptyList())
        val node = EwpTreeNode.Root(project)
        val treeNode = DefaultMutableTreeNode(node)
        val path = TreePath(arrayOf(treeNode))

        val target = handler.resolveDropTarget(path, node)
        assertNotNull(target)
        assertTrue(target!!.targetGroupPath.isEmpty())
        assertTrue(target.isRoot)
    }

    @Test
    fun resolveDropTargetForFileNodeResolvesToParentGroup() {
        val group = EwpGroup("src", emptyList(), emptyList())
        val groupNode = EwpTreeNode.GroupNode(group, listOf("src"))
        val groupTreeNode = DefaultMutableTreeNode(groupNode)

        val fileEntry = EwpFileEntry("main.cpp", "/proj/src/main.cpp")
        val fileNode = EwpTreeNode.FileNode(fileEntry, listOf("src"))
        val fileTreeNode = DefaultMutableTreeNode(fileNode)
        groupTreeNode.add(fileTreeNode)

        val path = TreePath(arrayOf(groupTreeNode, fileTreeNode))

        val target = handler.resolveDropTarget(path, fileNode)
        assertNotNull(target)
        assertEquals(listOf("src"), target?.targetGroupPath)
        assertFalse(target!!.isRoot)
    }

    @Test
    fun isValidDropAcceptsFileMovedToDifferentGroup() {
        val targetGroup = EwpGroup("config", emptyList(), emptyList())
        val dropTarget = EwpTreeTransferHandler.DropTargetInfo(
            targetGroupPath = listOf("config"),
            targetGroup = targetGroup,
            rootProject = null,
            isRoot = false,
        )

        val file = DraggedFile(
            entry = EwpFileEntry("main.cpp", "/proj/src/main.cpp"),
            sourceGroupPath = listOf("src"),
        )
        val data = EwpDragData(mockEwpFile, listOf(file), emptyList())

        assertTrue(handler.isValidDrop(data, dropTarget))
    }

    @Test
    fun isValidDropRejectsFileMovedToSameGroup() {
        val targetGroup = EwpGroup("src", emptyList(), emptyList())
        val dropTarget = EwpTreeTransferHandler.DropTargetInfo(
            targetGroupPath = listOf("src"),
            targetGroup = targetGroup,
            rootProject = null,
            isRoot = false,
        )

        val file = DraggedFile(
            entry = EwpFileEntry("main.cpp", "/proj/src/main.cpp"),
            sourceGroupPath = listOf("src"),
        )
        val data = EwpDragData(mockEwpFile, listOf(file), emptyList())

        assertFalse(handler.isValidDrop(data, dropTarget))
    }

    @Test
    fun isValidDropRejectsFileMovedToRoot() {
        val dropTarget = EwpTreeTransferHandler.DropTargetInfo(
            targetGroupPath = emptyList(),
            targetGroup = null,
            rootProject = EwpProject(mockEwpFile, emptyList(), emptyList()),
            isRoot = true,
        )

        val file = DraggedFile(
            entry = EwpFileEntry("main.cpp", "/proj/src/main.cpp"),
            sourceGroupPath = listOf("src"),
        )
        val data = EwpDragData(mockEwpFile, listOf(file), emptyList())

        assertFalse(handler.isValidDrop(data, dropTarget))
    }

    @Test
    fun isValidDropRejectsDuplicateFileInTargetGroup() {
        val existingFile = EwpFileEntry("\$PROJ_DIR$\\main.cpp", "/proj/main.cpp")
        val targetGroup = EwpGroup("config", emptyList(), listOf(existingFile))
        val dropTarget = EwpTreeTransferHandler.DropTargetInfo(
            targetGroupPath = listOf("config"),
            targetGroup = targetGroup,
            rootProject = null,
            isRoot = false,
        )

        val file = DraggedFile(
            entry = EwpFileEntry("\$PROJ_DIR$\\main.cpp", "/proj/main.cpp"),
            sourceGroupPath = listOf("src"),
        )
        val data = EwpDragData(mockEwpFile, listOf(file), emptyList())

        assertFalse(handler.isValidDrop(data, dropTarget))
    }

    @Test
    fun isValidDropAcceptsGroupMovedToAnotherGroup() {
        val targetGroup = EwpGroup("config", emptyList(), emptyList())
        val dropTarget = EwpTreeTransferHandler.DropTargetInfo(
            targetGroupPath = listOf("config"),
            targetGroup = targetGroup,
            rootProject = null,
            isRoot = false,
        )

        val group = DraggedGroup(
            group = EwpGroup("parameter", emptyList(), emptyList()),
            sourceGroupPath = listOf("src", "services", "parameter"),
        )
        val data = EwpDragData(mockEwpFile, emptyList(), listOf(group))

        assertTrue(handler.isValidDrop(data, dropTarget))
    }

    @Test
    fun isValidDropAcceptsGroupMovedToRoot() {
        val rootProject = EwpProject(
            ewpFile = mockEwpFile,
            groups = listOf(EwpGroup("src", emptyList(), emptyList())),
            sortIssues = emptyList(),
        )
        val dropTarget = EwpTreeTransferHandler.DropTargetInfo(
            targetGroupPath = emptyList(),
            targetGroup = null,
            rootProject = rootProject,
            isRoot = true,
        )

        val group = DraggedGroup(
            group = EwpGroup("services", emptyList(), emptyList()),
            sourceGroupPath = listOf("src", "services"),
        )
        val data = EwpDragData(mockEwpFile, emptyList(), listOf(group))

        assertTrue(handler.isValidDrop(data, dropTarget))
    }

    @Test
    fun isValidDropRejectsGroupMovedToSelfOrDescendant() {
        val targetGroup = EwpGroup("parameter", emptyList(), emptyList())
        val selfTarget = EwpTreeTransferHandler.DropTargetInfo(
            targetGroupPath = listOf("src", "services"),
            targetGroup = targetGroup,
            rootProject = null,
            isRoot = false,
        )
        val descendantTarget = EwpTreeTransferHandler.DropTargetInfo(
            targetGroupPath = listOf("src", "services", "parameter"),
            targetGroup = targetGroup,
            rootProject = null,
            isRoot = false,
        )

        val group = DraggedGroup(
            group = EwpGroup("services", emptyList(), emptyList()),
            sourceGroupPath = listOf("src", "services"),
        )
        val data = EwpDragData(mockEwpFile, emptyList(), listOf(group))

        assertFalse(handler.isValidDrop(data, selfTarget))
        assertFalse(handler.isValidDrop(data, descendantTarget))
    }

    @Test
    fun isValidDropRejectsGroupMovedToSameParent() {
        val targetGroup = EwpGroup("src", emptyList(), emptyList())
        val sameParentTarget = EwpTreeTransferHandler.DropTargetInfo(
            targetGroupPath = listOf("src"),
            targetGroup = targetGroup,
            rootProject = null,
            isRoot = false,
        )

        val group = DraggedGroup(
            group = EwpGroup("services", emptyList(), emptyList()),
            sourceGroupPath = listOf("src", "services"),
        )
        val data = EwpDragData(mockEwpFile, emptyList(), listOf(group))

        assertFalse(handler.isValidDrop(data, sameParentTarget))
    }

    @Test
    fun isValidDropRejectsDuplicateGroupNameInTarget() {
        val targetGroup = EwpGroup(
            name = "config",
            groups = listOf(EwpGroup("tools", emptyList(), emptyList())),
            files = emptyList(),
        )
        val dropTarget = EwpTreeTransferHandler.DropTargetInfo(
            targetGroupPath = listOf("config"),
            targetGroup = targetGroup,
            rootProject = null,
            isRoot = false,
        )

        val group = DraggedGroup(
            group = EwpGroup("tools", emptyList(), emptyList()),
            sourceGroupPath = listOf("src", "tools"),
        )
        val data = EwpDragData(mockEwpFile, emptyList(), listOf(group))

        assertFalse(handler.isValidDrop(data, dropTarget))
    }

    @Test
    fun transferableUsesJvmLocalObjectFlavor() {
        val data = EwpDragData(mockEwpFile, emptyList(), emptyList())
        val transferable = EwpTreeTransferable(data)

        assertTrue(transferable.isDataFlavorSupported(EwpTreeTransferable.DATA_FLAVOR))
        assertEquals(java.awt.datatransfer.DataFlavor.javaJVMLocalObjectMimeType, EwpTreeTransferable.DATA_FLAVOR.mimeType.substringBefore(';'))
        assertEquals(data, transferable.getTransferData(EwpTreeTransferable.DATA_FLAVOR))
    }

    @Test
    fun activeDragDataTracksCurrentDragLifecycle() {
        val data = EwpDragData(mockEwpFile, emptyList(), emptyList())
        EwpTreeTransferHandler.activeDragData = data
        assertEquals(data, EwpTreeTransferHandler.activeDragData)

        EwpTreeTransferHandler.activeDragData = null
        assertNull(EwpTreeTransferHandler.activeDragData)
    }
}
