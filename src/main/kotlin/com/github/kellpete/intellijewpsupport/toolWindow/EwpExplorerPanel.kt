package com.github.kellpete.intellijewpsupport.toolWindow

import com.github.kellpete.intellijewpsupport.MyBundle
import com.github.kellpete.intellijewpsupport.ewp.addfiles.EwpProjectChangeListener
import com.github.kellpete.intellijewpsupport.ewp.editor.EwpFileWriter
import com.github.kellpete.intellijewpsupport.model.EwpProject
import com.github.kellpete.intellijewpsupport.parser.EwpSortFixer
import com.github.kellpete.intellijewpsupport.service.EwpIndexService
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.service
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.SimpleTree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.util.ui.tree.TreeUtil
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.DefaultComboBoxModel
import javax.swing.DefaultListCellRenderer
import javax.swing.DropMode
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

class EwpExplorerPanel(private val project: Project) : BorderLayoutPanel() {

    private val indexService = project.service<EwpIndexService>()
    private val ewpComboBox = JComboBox<VirtualFile>()
    private val tree = SimpleTree()
    private val statusLabel = JBLabel("", JBLabel.LEFT)
    private val sortWarningLabel = JBLabel("", AllIcons.General.Warning, JBLabel.LEFT)
    private val sortWarningPanel = createSortWarningPanel()
    private val treeContainer = BorderLayoutPanel()

    init {
        border = JBUI.Borders.empty(4)

        ewpComboBox.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean,
            ) = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus).apply {
                text = (value as? VirtualFile)?.name ?: MyBundle["ewpExplorer.noEwpSelected"]
            }
        }

        val toolbar = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.emptyBottom(4)
            add(ewpComboBox, BorderLayout.CENTER)
            add(JButton(AllIcons.Actions.Refresh).apply {
                toolTipText = MyBundle["ewpExplorer.refresh"]
                addActionListener {
                    indexService.invalidateAll()
                    reloadEwpFiles()
                }
            }, BorderLayout.EAST)
        }

        tree.setRootVisible(true)
        tree.cellRenderer = EwpTreeCellRenderer()
        tree.dragEnabled = true
        tree.dropMode = DropMode.ON
        tree.transferHandler = EwpTreeTransferHandler(
            project = project,
            getSelectedEwpFile = { ewpComboBox.selectedItem as? VirtualFile },
            onNodesMoved = { targetGroupPath, movedGroupNames ->
                val selectedEwp = ewpComboBox.selectedItem as? VirtualFile ?: return@EwpTreeTransferHandler
                indexService.invalidate(selectedEwp)
                val selectPath = if (targetGroupPath.isEmpty() && movedGroupNames.isNotEmpty()) {
                    listOf(movedGroupNames.first())
                } else if (movedGroupNames.size == 1 && targetGroupPath.isNotEmpty()) {
                    targetGroupPath + movedGroupNames.first()
                } else {
                    targetGroupPath.ifEmpty { null }
                }
                reloadTree(selectGroupPath = selectPath)
            },
        )
        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    handleDoubleClick(e)
                }
            }
        })

        ewpComboBox.addActionListener { reloadTree() }

        project.messageBus.connect().subscribe(EwpProjectChangeListener.TOPIC, object : EwpProjectChangeListener {
            override fun onEwpProjectsChanged() {
                SwingUtilities.invokeLater { reloadTree() }
            }
        })

        treeContainer.add(sortWarningPanel, BorderLayout.NORTH)
        treeContainer.add(JBScrollPane(tree), BorderLayout.CENTER)

        add(toolbar, BorderLayout.NORTH)
        add(treeContainer, BorderLayout.CENTER)
        add(statusLabel, BorderLayout.SOUTH)

        reloadEwpFiles()
    }

    fun createTitleActions(): List<AnAction> =
        EwpExplorerActions.createTitleActions(this, tree, this)

    fun preferredFocusComponent() = tree

    fun canCreateNewGroup(): Boolean = ewpComboBox.selectedItem is VirtualFile

    fun createNewGroup() {
        val selectedEwp = ewpComboBox.selectedItem as? VirtualFile ?: return
        val parentPath = resolveNewGroupParentPath()
        val siblingNames = resolveSiblingGroupNames(parentPath)
        val name = EwpNewGroupDialog.askGroupName(this, siblingNames) ?: return
        val groupPath = parentPath + name

        if (!EwpFileWriter.createGroup(selectedEwp, project, groupPath)) {
            return
        }

        indexService.invalidate(selectedEwp)
        reloadTree(selectGroupPath = groupPath)
    }

    private fun createSortWarningPanel(): JPanel {
        val fixButton = JButton(MyBundle["ewpExplorer.fixSorting"]).apply {
            addActionListener { fixSortingForSelectedEwp() }
        }

        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.emptyBottom(4)
            isVisible = false
            add(sortWarningLabel, BorderLayout.CENTER)
            add(fixButton, BorderLayout.EAST)
        }
    }

    fun reloadEwpFiles() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater { reloadEwpFiles() }
            return
        }

        val previousSelection = ewpComboBox.selectedItem as? VirtualFile
        ReadAction.nonBlocking<List<VirtualFile>> {
            indexService.findEwpFiles()
        }
            .inSmartMode(project)
            .finishOnUiThread(ModalityState.defaultModalityState()) { ewpFiles ->
                applyEwpFiles(ewpFiles, previousSelection)
            }
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    private fun applyEwpFiles(ewpFiles: List<VirtualFile>, previousSelection: VirtualFile?) {
        ewpComboBox.model = DefaultComboBoxModel(ewpFiles.toTypedArray())
        ewpComboBox.isEnabled = ewpFiles.isNotEmpty()

        if (ewpFiles.isEmpty()) {
            showEmptyState()
            return
        }

        val selection = previousSelection?.takeIf { previous -> ewpFiles.any { it.path == previous.path } } ?: ewpFiles.first()
        ewpComboBox.selectedItem = selection
        reloadTree()
    }

    private fun reloadTree(selectGroupPath: List<String>? = null) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater { reloadTree(selectGroupPath) }
            return
        }

        val selectedEwp = ewpComboBox.selectedItem as? VirtualFile
        if (selectedEwp == null) {
            showEmptyState()
            return
        }

        ReadAction.nonBlocking<EwpProject?> {
            indexService.getProject(selectedEwp)
        }
            .inSmartMode(project)
            .finishOnUiThread(ModalityState.defaultModalityState()) { ewpProject ->
                applyTreeProject(selectedEwp, ewpProject, selectGroupPath)
            }
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    private fun applyTreeProject(
        selectedEwp: VirtualFile,
        ewpProject: EwpProject?,
        selectGroupPath: List<String>? = null,
    ) {
        if (ewpProject == null) {
            clearTree()
            hideSortWarning()
            statusLabel.text = MyBundle["ewpExplorer.parseError", selectedEwp.name]
            return
        }

        statusLabel.text = ""
        setTreeProject(ewpProject, selectGroupPath)
        updateSortWarning(ewpProject)
    }

    private fun setTreeProject(ewpProject: EwpProject, selectGroupPath: List<String>? = null) {
        tree.model = EwpTreeStructure.buildTreeModel(ewpProject)
        tree.setRootVisible(true)
        TreeUtil.expandAll(tree)
        if (selectGroupPath != null) {
            selectGroup(selectGroupPath)
        }
    }

    private fun resolveNewGroupParentPath(): List<String> {
        val selected = tree.selectionPath?.lastPathComponent
        return when (val node = extractTreeNode(selected)) {
            is EwpTreeNode.GroupNode -> node.groupPath
            is EwpTreeNode.FileNode -> {
                val parent = tree.selectionPath?.parentPath?.lastPathComponent
                when (val parentNode = extractTreeNode(parent)) {
                    is EwpTreeNode.GroupNode -> parentNode.groupPath
                    else -> emptyList()
                }
            }
            else -> emptyList()
        }
    }

    private fun resolveSiblingGroupNames(parentPath: List<String>): Set<String> {
        val root = tree.model.root ?: return emptySet()
        val parentNode = findGroupNode(root, parentPath) ?: return emptySet()
        return (0 until parentNode.childCount).mapNotNull { index ->
            val child = parentNode.getChildAt(index) as? DefaultMutableTreeNode ?: return@mapNotNull null
            (child.userObject as? EwpTreeNode.GroupNode)?.group?.name
        }.toSet()
    }

    private fun selectGroup(groupPath: List<String>) {
        val root = tree.model.root ?: return
        val node = findGroupNode(root, groupPath) ?: return
        val path = TreePath(node.path)
        tree.selectionPath = path
        TreeUtil.showRowCentered(tree, tree.getRowForPath(path), false)
    }

    private fun findGroupNode(root: Any, groupPath: List<String>): DefaultMutableTreeNode? {
        var current = root as? DefaultMutableTreeNode ?: return null
        for (name in groupPath) {
            current = (0 until current.childCount)
                .mapNotNull { current.getChildAt(it) as? DefaultMutableTreeNode }
                .firstOrNull { (it.userObject as? EwpTreeNode.GroupNode)?.group?.name == name }
                ?: return null
        }
        return current
    }

    private fun clearTree() {
        tree.model = DefaultTreeModel(DefaultMutableTreeNode())
    }

    private fun showEmptyState() {
        clearTree()
        hideSortWarning()
        statusLabel.text = MyBundle["ewpExplorer.emptyState"]
    }

    private fun updateSortWarning(ewpProject: EwpProject) {
        val issueCount = ewpProject.sortIssueCount
        if (issueCount <= 0) {
            hideSortWarning()
            return
        }

        sortWarningLabel.text = MyBundle["ewpExplorer.sortWarning", issueCount]
        sortWarningPanel.isVisible = true
        treeContainer.revalidate()
        treeContainer.repaint()
    }

    private fun hideSortWarning() {
        sortWarningPanel.isVisible = false
        treeContainer.revalidate()
        treeContainer.repaint()
    }

    private fun fixSortingForSelectedEwp() {
        val selectedEwp = ewpComboBox.selectedItem as? VirtualFile ?: return
        EwpSortFixer.fixSorting(selectedEwp, project)
        indexService.invalidate(selectedEwp)
        reloadTree()
    }

    private fun handleDoubleClick(e: MouseEvent) {
        val path = tree.getPathForLocation(e.x, e.y) ?: return
        val node = extractTreeNode(path.lastPathComponent) as? EwpTreeNode.FileNode ?: return
        navigateToFile(node)
    }

    private fun navigateToFile(node: EwpTreeNode.FileNode) {
        val resolvedPath = node.entry.resolvedPath ?: return
        val virtualFile = LocalFileSystem.getInstance().findFileByPath(resolvedPath) ?: return
        OpenFileDescriptor(project, virtualFile).navigate(true)
    }

    private fun extractTreeNode(pathComponent: Any?): EwpTreeNode? =
        EwpTreeStructure.extractNode(pathComponent)
}

class EwpTreeCellRenderer : ColoredTreeCellRenderer() {

    override fun customizeCellRenderer(
        tree: javax.swing.JTree,
        value: Any?,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean,
    ) {
        val node = extractTreeNode(value) ?: run {
            append(value?.toString() ?: "", SimpleTextAttributes.REGULAR_ATTRIBUTES)
            return
        }

        val dropLocation = tree.dropLocation
        val isDropTarget = dropLocation != null &&
            dropLocation.childIndex == -1 &&
            tree.getRowForPath(dropLocation.path) == row

        if (isDropTarget) {
            setPaintFocusBorder(true)
        }

        append(node.displayName, SimpleTextAttributes.REGULAR_ATTRIBUTES)

        when (node) {
            is EwpTreeNode.Root -> {
                icon = AllIcons.Nodes.Module
                toolTipText = node.project.ewpFile.path
            }
            is EwpTreeNode.GroupNode -> {
                icon = AllIcons.Nodes.Folder
                toolTipText = node.displayName
            }
            is EwpTreeNode.FileNode -> {
                val resolvedPath = node.entry.resolvedPath
                icon = if (resolvedPath != null) {
                    com.intellij.openapi.fileTypes.FileTypeManager.getInstance()
                        .getFileTypeByFileName(resolvedPath).icon
                } else {
                    AllIcons.General.Warning
                }
                toolTipText = resolvedPath ?: node.entry.rawPath
            }
        }
    }

    private fun extractTreeNode(value: Any?): EwpTreeNode? =
        EwpTreeStructure.extractNode(value)
}
