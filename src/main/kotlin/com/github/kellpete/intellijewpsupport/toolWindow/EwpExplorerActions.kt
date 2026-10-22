package com.github.kellpete.intellijewpsupport.toolWindow

import com.github.kellpete.intellijewpsupport.MyBundle
import com.intellij.icons.AllIcons
import com.intellij.ide.CommonActionsManager
import com.intellij.ide.DefaultTreeExpander
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.InputValidatorEx
import com.intellij.openapi.ui.Messages
import com.intellij.ui.PopupHandler
import javax.swing.JComponent
import javax.swing.JTree
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath

internal object EwpExplorerActions {

    private const val POPUP_PLACE = "EwpExplorerPopup"

    fun createTitleActions(panel: EwpExplorerPanel, tree: JTree, contextComponent: JComponent): List<AnAction> {
        val expander = DefaultTreeExpander { tree }
        val common = CommonActionsManager.getInstance()
        return listOf(
            ExpandSelectedAction(tree, contextComponent),
            common.createCollapseAllHeaderAction(expander, contextComponent),
        )
    }

    fun installPopupMenu(panel: EwpExplorerPanel, tree: JTree): RemoveFilesAction {
        val removeFilesAction = RemoveFilesAction(panel, tree)
        val group = DefaultActionGroup().apply {
            add(NewGroupAction(panel))
            add(AddFilesAction(panel))
            add(removeFilesAction)
        }
        PopupHandler.installPopupMenu(tree, group, POPUP_PLACE)
        return removeFilesAction
    }
}

private class ExpandSelectedAction(
    private val tree: JTree,
    contextComponent: JComponent,
) : DumbAwareAction(
    MyBundle.messagePointer("ewpExplorer.expandSelected"),
    AllIcons.Actions.Expandall,
) {
    init {
        ActionManager.getInstance().getAction("ExpandRecursively")?.shortcutSet?.let { shortcuts ->
            registerCustomShortcutSet(shortcuts, contextComponent)
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val paths = tree.selectionPaths ?: return
        for (path in paths) {
            expandRecursively(path)
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = tree.selectionCount > 0
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    private fun expandRecursively(path: TreePath) {
        tree.expandPath(path)
        val node = path.lastPathComponent as? TreeNode ?: return
        for (i in 0 until node.childCount) {
            expandRecursively(path.pathByAddingChild(node.getChildAt(i)))
        }
    }
}

private class NewGroupAction(
    private val panel: EwpExplorerPanel,
) : DumbAwareAction(
    MyBundle.messagePointer("ewpExplorer.newGroup"),
    AllIcons.Actions.NewFolder,
) {
    override fun actionPerformed(e: AnActionEvent) {
        panel.createNewGroup()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = panel.canCreateNewGroup()
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}

private class AddFilesAction(
    private val panel: EwpExplorerPanel,
) : DumbAwareAction(
    MyBundle.messagePointer("ewpExplorer.addFiles"),
    AllIcons.Actions.AddFile,
) {
    override fun actionPerformed(e: AnActionEvent) {
        panel.addFilesToSelectedGroup()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = panel.canAddFilesToSelectedGroup()
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}

internal class RemoveFilesAction(
    private val panel: EwpExplorerPanel,
    tree: JTree,
) : DumbAwareAction(
    MyBundle.messagePointer("ewpExplorer.removeFiles"),
    AllIcons.Actions.GC,
) {
    init {
        registerCustomShortcutSet(CommonShortcuts.getDelete(), tree)
    }

    override fun actionPerformed(e: AnActionEvent) {
        panel.removeSelectedFromEwp()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = panel.canRemoveSelectedFromEwp()
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}

internal object EwpNewGroupDialog {
    fun askGroupName(parentComponent: JComponent, existingSiblingNames: Set<String>): String? {
        val name = Messages.showInputDialog(
            parentComponent,
            MyBundle["ewpExplorer.newGroup.prompt"],
            MyBundle["ewpExplorer.newGroup.title"],
            AllIcons.Actions.NewFolder,
            "",
            object : InputValidatorEx {
                override fun checkInput(inputString: String?): Boolean =
                    getErrorText(inputString) == null

                override fun canClose(inputString: String?): Boolean =
                    checkInput(inputString)

                override fun getErrorText(inputString: String?): String? {
                    val trimmed = inputString?.trim().orEmpty()
                    return when {
                        trimmed.isEmpty() -> MyBundle["ewpExplorer.newGroup.emptyName"]
                        existingSiblingNames.contains(trimmed) -> MyBundle["ewpExplorer.newGroup.duplicateName"]
                        else -> null
                    }
                }
            },
        )
        return name?.trim()?.takeIf { it.isNotEmpty() }
    }
}
