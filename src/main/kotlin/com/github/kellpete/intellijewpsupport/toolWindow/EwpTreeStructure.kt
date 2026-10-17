package com.github.kellpete.intellijewpsupport.toolWindow

import com.github.kellpete.intellijewpsupport.model.EwpFileEntry
import com.github.kellpete.intellijewpsupport.model.EwpGroup
import com.github.kellpete.intellijewpsupport.model.EwpProject
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

sealed interface EwpTreeNode {
    val displayName: String

    // TreeSpeedSearch matches DefaultMutableTreeNode.toString() (userObject.toString()),
    // while the cell renderer highlights displayName — keep them identical.
    data class Root(val project: EwpProject) : EwpTreeNode {
        override val displayName: String = project.ewpFile.name
        override fun toString(): String = displayName
    }

    data class GroupNode(
        val group: EwpGroup,
        val groupPath: List<String>,
    ) : EwpTreeNode {
        override val displayName: String = group.name
        override fun toString(): String = displayName
    }

    data class FileNode(val entry: EwpFileEntry) : EwpTreeNode {
        override val displayName: String = entry.displayName
        override fun toString(): String = displayName
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
                EwpTreeNode.GroupNode(group, listOf(group.name))
            }

            is EwpTreeNode.GroupNode -> {
                val childGroups = node.group.groups.map { group ->
                    EwpTreeNode.GroupNode(group, node.groupPath + group.name)
                }
                val childFiles = node.group.files.map { EwpTreeNode.FileNode(it) }
                childGroups + childFiles
            }

            is EwpTreeNode.FileNode -> emptyList()
        }
    }

}
