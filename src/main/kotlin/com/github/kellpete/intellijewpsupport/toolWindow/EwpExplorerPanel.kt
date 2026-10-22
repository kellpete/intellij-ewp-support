package com.github.kellpete.intellijewpsupport.toolWindow

import com.github.kellpete.intellijewpsupport.MyBundle
import com.github.kellpete.intellijewpsupport.ewp.addfiles.EwpProjectChangeListener
import com.github.kellpete.intellijewpsupport.ewp.editor.EwpFileWriter
import com.github.kellpete.intellijewpsupport.ewp.editor.EwpGroupMapper
import com.github.kellpete.intellijewpsupport.model.EwpGroup
import com.github.kellpete.intellijewpsupport.model.EwpProject
import com.github.kellpete.intellijewpsupport.parser.EwpSortFixer
import com.github.kellpete.intellijewpsupport.service.EwpIndexService
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.SimpleTree
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.util.ui.tree.TreeUtil
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.DefaultComboBoxModel
import javax.swing.DefaultListCellRenderer
import javax.swing.DropMode
import javax.swing.BoxLayout
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
    private val missingFilesWarningLabel = JBLabel("", AllIcons.General.Warning, JBLabel.LEFT)
    private val missingFilesWarningPanel = createMissingFilesWarningPanel()
    private val warningsPanel = createWarningsPanel()
    private val treeContainer = BorderLayoutPanel()
    @Suppress("unused")
    private val removeFilesAction: RemoveFilesAction

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

        val toolbarRowHeight = ewpComboBox.preferredSize.height
        ewpComboBox.maximumSize = Dimension(Int.MAX_VALUE, toolbarRowHeight)
        val refreshButton = JButton(AllIcons.Actions.Refresh).apply {
            toolTipText = MyBundle["ewpExplorer.refresh"]
            margin = JBUI.emptyInsets()
            isFocusable = false
            preferredSize = JBUI.size(toolbarRowHeight, toolbarRowHeight)
            addActionListener {
                indexService.invalidateAll()
                reloadEwpFiles()
            }
        }

        val toolbar = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            border = JBUI.Borders.emptyBottom(4)
            add(ewpComboBox)
            add(refreshButton)
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
        removeFilesAction = EwpExplorerActions.installPopupMenu(this, tree)

        ewpComboBox.addActionListener { reloadTree() }

        project.messageBus.connect().subscribe(EwpProjectChangeListener.TOPIC, object : EwpProjectChangeListener {
            override fun onEwpProjectsChanged() {
                SwingUtilities.invokeLater { reloadTree() }
            }
        })

        treeContainer.add(warningsPanel, BorderLayout.NORTH)
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

    fun canAddFilesToSelectedGroup(): Boolean =
        ewpComboBox.selectedItem is VirtualFile && resolveTargetGroupPath() != null

    fun canRemoveSelectedFromEwp(): Boolean =
        ewpComboBox.selectedItem is VirtualFile &&
            (selectedFileNodes().isNotEmpty() || selectedGroupNodes().isNotEmpty())

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

    fun addFilesToSelectedGroup() {
        val selectedEwp = ewpComboBox.selectedItem as? VirtualFile ?: return
        val groupPath = resolveTargetGroupPath() ?: return
        val ewpProject = currentEwpProject() ?: return
        val toSelect = resolveSuggestedDirectory(ewpProject, groupPath)

        val descriptor = FileChooserDescriptorFactory.createMultipleFilesNoJarsDescriptor().apply {
            title = MyBundle["ewpExplorer.addFiles.title"]
            description = MyBundle["ewpExplorer.addFiles.description"]
        }

        FileChooser.chooseFiles(descriptor, project, this, toSelect) { chosen ->
            val files = chosen.filter { it.isValid && !it.isDirectory }
            if (files.isEmpty()) return@chooseFiles

            val directories = files.mapNotNull { it.parent?.path }.map { FileUtil.toSystemIndependentName(it) }.toSet()
            if (directories.size > 1) {
                Messages.showErrorDialog(
                    this,
                    MyBundle["ewpExplorer.addFiles.sameDirectory"],
                    MyBundle["ewpExplorer.addFiles.title"],
                )
                return@chooseFiles
            }

            val additions = files.map { file ->
                EwpFileWriter.FileAddition(
                    absolutePath = file.path,
                    groupPath = groupPath,
                )
            }
            EwpFileWriter.addFiles(selectedEwp, project, additions)
            indexService.invalidate(selectedEwp)
            reloadTree(selectGroupPath = groupPath)
        }
    }

    fun removeSelectedFromEwp() {
        val selectedEwp = ewpComboBox.selectedItem as? VirtualFile ?: return
        val groupNodes = selectedGroupNodes()
        val groupPaths = EwpFileWriter.filterRedundantGroupPaths(groupNodes.map { it.groupPath })
        val fileNodes = selectedFileNodes().filter { node ->
            groupPaths.none { EwpFileWriter.isDescendantOrSelf(it, node.groupPath) }
        }
        if (fileNodes.isEmpty() && groupPaths.isEmpty()) return

        val message = buildRemoveFromEwpConfirmMessage(fileNodes, groupPaths)
        val title = MyBundle["ewpExplorer.removeFiles.title"]
        if (Messages.showYesNoDialog(this, message, title, Messages.getQuestionIcon()) != Messages.YES) {
            return
        }

        val absolutePaths = mutableListOf<String>()
        val rawPaths = mutableListOf<String>()
        for (node in fileNodes) {
            val resolved = node.entry.resolvedPath
            if (resolved != null) {
                absolutePaths += resolved
            } else {
                rawPaths += node.entry.rawPath
            }
        }

        val selectGroupPath = when {
            groupPaths.isNotEmpty() -> groupPaths.first().dropLast(1).takeIf { it.isNotEmpty() }
            fileNodes.isNotEmpty() -> fileNodes.first().groupPath.takeIf { it.isNotEmpty() }
            else -> null
        }
        if (!EwpFileWriter.removeFromEwp(selectedEwp, project, absolutePaths, rawPaths, groupPaths)) {
            return
        }

        indexService.invalidate(selectedEwp)
        reloadTree(selectGroupPath = selectGroupPath)
    }

    private fun buildRemoveFromEwpConfirmMessage(
        fileNodes: List<EwpTreeNode.FileNode>,
        groupPaths: List<List<String>>,
    ): String {
        val fileCount = fileNodes.size
        val groupCount = groupPaths.size
        return when {
            fileCount == 0 && groupCount == 1 ->
                MyBundle["ewpExplorer.removeFiles.confirmSingleGroup", groupPaths.first().last()]
            fileCount == 0 && groupCount > 1 ->
                MyBundle["ewpExplorer.removeFiles.confirmMultipleGroups", groupCount]
            fileCount == 1 && groupCount == 0 ->
                MyBundle["ewpExplorer.removeFiles.confirmSingle", fileNodes.first().displayName]
            fileCount > 1 && groupCount == 0 ->
                MyBundle["ewpExplorer.removeFiles.confirmMultiple", fileCount]
            else ->
                MyBundle["ewpExplorer.removeFiles.confirmMixed", fileCount, groupCount]
        }
    }

    private fun selectedFileNodes(): List<EwpTreeNode.FileNode> =
        tree.selectionPaths
            ?.mapNotNull { path -> extractTreeNode(path.lastPathComponent) as? EwpTreeNode.FileNode }
            ?: emptyList()

    private fun selectedGroupNodes(): List<EwpTreeNode.GroupNode> =
        tree.selectionPaths
            ?.mapNotNull { path -> extractTreeNode(path.lastPathComponent) as? EwpTreeNode.GroupNode }
            ?: emptyList()

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

    private fun createMissingFilesWarningPanel(): JPanel {
        val fixButton = JButton(MyBundle["ewpExplorer.removeMissingFiles"]).apply {
            addActionListener { removeMissingFilesForSelectedEwp() }
        }

        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.emptyBottom(4)
            isVisible = false
            add(missingFilesWarningLabel, BorderLayout.CENTER)
            add(fixButton, BorderLayout.EAST)
        }
    }

    private fun createWarningsPanel(): JPanel =
        BorderLayoutPanel().apply {
            isVisible = false
            addToTop(sortWarningPanel)
            addToBottom(missingFilesWarningPanel)
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
            hideWarnings()
            statusLabel.text = MyBundle["ewpExplorer.parseError", selectedEwp.name]
            return
        }

        statusLabel.text = ""
        setTreeProject(ewpProject, selectGroupPath)
        updateWarnings(ewpProject)
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
            is EwpTreeNode.FileNode -> node.groupPath
            else -> emptyList()
        }
    }

    /** Target group for Add Files: selected group, or parent group of a selected file. */
    private fun resolveTargetGroupPath(): List<String>? {
        val selected = tree.selectionPath?.lastPathComponent
        return when (val node = extractTreeNode(selected)) {
            is EwpTreeNode.GroupNode -> node.groupPath
            is EwpTreeNode.FileNode -> node.groupPath.takeIf { it.isNotEmpty() }
            else -> null
        }
    }

    private fun currentEwpProject(): EwpProject? =
        (extractTreeNode(tree.model.root) as? EwpTreeNode.Root)?.project

    private fun resolveSuggestedDirectory(ewpProject: EwpProject, groupPath: List<String>): VirtualFile? {
        val projectRoot = resolveProjectRoot(ewpProject) ?: return ewpProject.ewpFile.parent
        val suggestedPath = EwpGroupMapper(ewpProject, projectRoot).mapGroupToDirectory(groupPath)
        return findExistingDirectory(suggestedPath) ?: findExistingDirectory(projectRoot) ?: ewpProject.ewpFile.parent
    }

    private fun resolveProjectRoot(ewpProject: EwpProject): String? {
        val contentRoots = ProjectRootManager.getInstance(project).contentRoots.map {
            FileUtil.toSystemIndependentName(it.path)
        }
        if (contentRoots.isEmpty()) {
            return ewpProject.ewpFile.parent?.path?.let { FileUtil.toSystemIndependentName(it) }
        }

        val fileDirs = collectResolvedFileDirectories(ewpProject.groups)
        val matching = contentRoots.filter { root ->
            fileDirs.any { dir -> FileUtil.startsWith(dir, root) } ||
                FileUtil.startsWith(FileUtil.toSystemIndependentName(ewpProject.ewpFile.path), root)
        }
        return matching.minByOrNull { it.length }
            ?: contentRoots.minByOrNull { it.length }
    }

    private fun collectResolvedFileDirectories(groups: List<EwpGroup>): List<String> {
        val result = mutableListOf<String>()
        for (group in groups) {
            for (file in group.files) {
                val path = file.resolvedPath ?: continue
                val normalized = FileUtil.toSystemIndependentName(path)
                val lastSlash = normalized.lastIndexOf('/')
                if (lastSlash > 0) {
                    result += normalized.substring(0, lastSlash)
                }
            }
            result += collectResolvedFileDirectories(group.groups)
        }
        return result
    }

    private fun findExistingDirectory(path: String): VirtualFile? {
        var current: String? = FileUtil.toSystemIndependentName(path)
        val lfs = LocalFileSystem.getInstance()
        while (current != null) {
            val file = lfs.findFileByPath(current)
            if (file != null && file.isDirectory) return file
            val lastSlash = current.lastIndexOf('/')
            if (lastSlash <= 0) break
            current = current.substring(0, lastSlash)
        }
        return null
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
        hideWarnings()
        statusLabel.text = MyBundle["ewpExplorer.emptyState"]
    }

    private fun updateWarnings(ewpProject: EwpProject) {
        val sortIssueCount = ewpProject.sortIssueCount
        if (sortIssueCount > 0) {
            sortWarningLabel.text = MyBundle["ewpExplorer.sortWarning", sortIssueCount]
            sortWarningPanel.isVisible = true
        } else {
            sortWarningPanel.isVisible = false
        }

        val missingFileCount = ewpProject.missingFileCount
        if (missingFileCount > 0) {
            missingFilesWarningLabel.text = MyBundle["ewpExplorer.missingFilesWarning", missingFileCount]
            missingFilesWarningPanel.isVisible = true
        } else {
            missingFilesWarningPanel.isVisible = false
        }

        warningsPanel.isVisible = sortWarningPanel.isVisible || missingFilesWarningPanel.isVisible
        treeContainer.revalidate()
        treeContainer.repaint()
    }

    private fun hideWarnings() {
        sortWarningPanel.isVisible = false
        missingFilesWarningPanel.isVisible = false
        warningsPanel.isVisible = false
        treeContainer.revalidate()
        treeContainer.repaint()
    }

    private fun fixSortingForSelectedEwp() {
        val selectedEwp = ewpComboBox.selectedItem as? VirtualFile ?: return
        EwpSortFixer.fixSorting(selectedEwp, project)
        indexService.invalidate(selectedEwp)
        reloadTree()
    }

    private fun removeMissingFilesForSelectedEwp() {
        val selectedEwp = ewpComboBox.selectedItem as? VirtualFile ?: return
        val ewpProject = currentEwpProject() ?: return
        val rawPaths = ewpProject.missingFiles.map { it.rawPath }
        if (rawPaths.isEmpty()) return

        EwpFileWriter.removeFiles(selectedEwp, project, absolutePaths = emptyList(), rawPaths = rawPaths)
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
