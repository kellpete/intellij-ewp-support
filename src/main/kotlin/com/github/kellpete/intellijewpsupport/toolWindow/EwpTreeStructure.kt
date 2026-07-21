package com.github.kellpete.intellijewpsupport.toolWindow

import com.github.kellpete.intellijewpsupport.model.EwpFileEntry
import com.github.kellpete.intellijewpsupport.model.EwpGroup
import com.github.kellpete.intellijewpsupport.model.EwpProject
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

sealed interface EwpTreeNode {
    val displayName: String

    data class Root(val project: EwpProject) : EwpTreeNode {
        override val displayName: String = project.ewpFile.name
    }

    data class GroupNode(
        val group: EwpGroup,
        val groupPath: List<String>,
        val hasSortIssue: Boolean,
    ) : EwpTreeNode {
        override val displayName: String = group.name
    }

    data class FileNode(val entry: EwpFileEntry) : EwpTreeNode {
        override val displayName: String = entry.displayName
    }
}

object EwpTreeStructure {

    fun buildTreeModel(ewpProject: EwpProject): DefaultTreeModel {
        val root = buildNode(EwpTreeNode.Root(ewpProject), ewpProject)
        return DefaultTreeModel(root)
    }

    private fun buildNode(node: EwpTreeNode, ewpProject: EwpProject): DefaultMutableTreeNode {
        val treeNode = DefaultMutableTreeNode(node)
        getChildren(node, ewpProject).forEach { child ->
            treeNode.add(buildNode(child, ewpProject))
        }
        return treeNode
    }

    fun getChildren(node: EwpTreeNode, ewpProject: EwpProject): List<EwpTreeNode> {
        return when (node) {
            is EwpTreeNode.Root -> ewpProject.groups.map { group ->
                toGroupNode(ewpProject, group, listOf(group.name))
            }

            is EwpTreeNode.GroupNode -> {
                val childGroups = node.group.groups.map { group ->
                    toGroupNode(ewpProject, group, node.groupPath + group.name)
                }
                val childFiles = node.group.files.map { EwpTreeNode.FileNode(it) }
                childGroups + childFiles
            }

            is EwpTreeNode.FileNode -> emptyList()
        }
    }

    private fun toGroupNode(project: EwpProject, group: EwpGroup, groupPath: List<String>): EwpTreeNode.GroupNode {
        return EwpTreeNode.GroupNode(
            group = group,
            groupPath = groupPath,
            hasSortIssue = project.hasSortIssue(groupPath),
        )
    }
}
