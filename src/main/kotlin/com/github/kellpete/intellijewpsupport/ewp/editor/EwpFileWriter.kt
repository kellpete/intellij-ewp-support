package com.github.kellpete.intellijewpsupport.ewp.editor

import com.github.kellpete.intellijewpsupport.parser.EwpDomSortFixer
import com.github.kellpete.intellijewpsupport.path.EwpPathResolver
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import org.jdom.Element

object EwpFileWriter {

    data class FileAddition(
        val absolutePath: String,
        val groupPath: List<String>,
    )

    data class FileMove(
        val oldAbsolutePath: String,
        val newAbsolutePath: String,
        val newGroupPath: List<String>,
    )

    data class TreeFileMove(
        val rawPath: String,
        val sourceGroupPath: List<String>,
        val targetGroupPath: List<String>,
    )

    data class TreeGroupMove(
        val sourceGroupPath: List<String>,
        val targetParentGroupPath: List<String>,
    )

    fun addFiles(ewpFile: VirtualFile, project: Project, additions: List<FileAddition>) {
        if (additions.isEmpty()) return

        WriteCommandAction.runWriteCommandAction(project) {
            val document = FileDocumentManager.getInstance().getDocument(ewpFile) ?: return@runWriteCommandAction
            val ewpDirectory = ewpFile.parent.path
            val updatedText = addFilesToXml(document.text, ewpDirectory, additions)
            if (updatedText != document.text) {
                document.setText(updatedText)
                FileDocumentManager.getInstance().saveDocument(document)
            }
        }
    }

    @JvmOverloads
    fun removeFiles(
        ewpFile: VirtualFile,
        project: Project,
        absolutePaths: Collection<String>,
        rawPaths: Collection<String> = emptyList(),
    ): Boolean = removeFromEwp(ewpFile, project, absolutePaths, rawPaths)

    @JvmOverloads
    fun removeFromEwp(
        ewpFile: VirtualFile,
        project: Project,
        absolutePaths: Collection<String> = emptyList(),
        rawPaths: Collection<String> = emptyList(),
        groupPaths: Collection<List<String>> = emptyList(),
    ): Boolean {
        if (absolutePaths.isEmpty() && rawPaths.isEmpty() && groupPaths.isEmpty()) return false

        val changed = booleanArrayOf(false)
        WriteCommandAction.runWriteCommandAction(project) {
            val document = FileDocumentManager.getInstance().getDocument(ewpFile) ?: return@runWriteCommandAction
            val ewpDirectory = ewpFile.parent.path
            val updatedText = removeFromEwpToXml(document.text, ewpDirectory, absolutePaths, rawPaths, groupPaths)
            if (updatedText != document.text) {
                document.setText(updatedText)
                FileDocumentManager.getInstance().saveDocument(document)
                changed[0] = true
            }
        }
        return changed[0]
    }

    fun moveFiles(ewpFile: VirtualFile, project: Project, moves: List<FileMove>) {
        if (moves.isEmpty()) return

        WriteCommandAction.runWriteCommandAction(project) {
            val document = FileDocumentManager.getInstance().getDocument(ewpFile) ?: return@runWriteCommandAction
            val ewpDirectory = ewpFile.parent.path
            val updatedText = moveFilesToXml(document.text, ewpDirectory, moves)
            if (updatedText != document.text) {
                document.setText(updatedText)
                FileDocumentManager.getInstance().saveDocument(document)
            }
        }
    }

    /**
     * Creates an empty group at [groupPath] (parents are created if missing).
     * Returns false if the group already exists or the write could not be applied.
     */
    fun createGroup(ewpFile: VirtualFile, project: Project, groupPath: List<String>): Boolean {
        if (groupPath.isEmpty()) return false

        val changed = booleanArrayOf(false)
        WriteCommandAction.runWriteCommandAction(project) {
            val document = FileDocumentManager.getInstance().getDocument(ewpFile) ?: return@runWriteCommandAction
            val updatedText = createGroupInXml(document.text, groupPath)
            if (updatedText != document.text) {
                document.setText(updatedText)
                FileDocumentManager.getInstance().saveDocument(document)
                changed[0] = true
            }
        }
        return changed[0]
    }

    fun moveNodes(
        ewpFile: VirtualFile,
        project: Project,
        fileMoves: List<TreeFileMove> = emptyList(),
        groupMoves: List<TreeGroupMove> = emptyList(),
    ): Boolean {
        if (fileMoves.isEmpty() && groupMoves.isEmpty()) return false

        val changed = booleanArrayOf(false)
        WriteCommandAction.runWriteCommandAction(project) {
            val document = FileDocumentManager.getInstance().getDocument(ewpFile) ?: return@runWriteCommandAction
            val updatedText = moveNodesInXml(document.text, fileMoves, groupMoves)
            if (updatedText != document.text) {
                document.setText(updatedText)
                FileDocumentManager.getInstance().saveDocument(document)
                changed[0] = true
            }
        }
        return changed[0]
    }

    fun moveNodesInXml(
        xml: String,
        fileMoves: List<TreeFileMove> = emptyList(),
        groupMoves: List<TreeGroupMove> = emptyList(),
    ): String {
        if (fileMoves.isEmpty() && groupMoves.isEmpty()) return xml

        val root = JDOMUtil.load(xml)
        require(root.name == "project") { "Root element must be <project>, was <${root.name}>" }

        var changed = false
        for (move in fileMoves) {
            if (moveFileInXml(root, move.rawPath, move.sourceGroupPath, move.targetGroupPath)) {
                changed = true
            }
        }
        for (move in groupMoves) {
            if (moveGroupInXml(root, move.sourceGroupPath, move.targetParentGroupPath)) {
                changed = true
            }
        }

        if (!changed) return xml
        return EwpDomSortFixer.sortAndFormat(root)
    }

    fun moveFileInXml(
        root: Element,
        rawPath: String,
        sourceGroupPath: List<String>,
        targetGroupPath: List<String>,
    ): Boolean {
        if (sourceGroupPath == targetGroupPath) return false
        if (targetGroupPath.isEmpty()) return false

        val sourceGroup = findGroup(root, sourceGroupPath) ?: return false
        val targetGroup = findOrCreateGroup(root, targetGroupPath)

        val normalizedRaw = normalizeRawPath(rawPath)
        val fileElement = sourceGroup.children
            .filter { it.name == "file" }
            .firstOrNull { normalizeRawPath(it.getChildTextTrim("name").orEmpty()) == normalizedRaw }
            ?: return false

        val alreadyInTarget = targetGroup.children
            .filter { it.name == "file" }
            .any { normalizeRawPath(it.getChildTextTrim("name").orEmpty()) == normalizedRaw }
        if (alreadyInTarget) return false

        sourceGroup.removeContent(fileElement)
        addContentToContainer(targetGroup, fileElement)
        return true
    }

    fun moveGroupInXml(
        root: Element,
        sourceGroupPath: List<String>,
        targetParentGroupPath: List<String>,
    ): Boolean {
        if (sourceGroupPath.isEmpty()) return false
        if (isDescendantOrSelf(sourceGroupPath, targetParentGroupPath)) return false
        if (sourceGroupPath.dropLast(1) == targetParentGroupPath) return false

        val sourceParentPath = sourceGroupPath.dropLast(1)
        val groupName = sourceGroupPath.last()

        val sourceParent = if (sourceParentPath.isEmpty()) root else findGroup(root, sourceParentPath) ?: return false
        val groupElement = findChildGroup(sourceParent, groupName) ?: return false

        val targetParent = if (targetParentGroupPath.isEmpty()) root else findOrCreateGroup(root, targetParentGroupPath)
        if (findChildGroup(targetParent, groupName) != null) return false

        sourceParent.removeContent(groupElement)
        addContentToContainer(targetParent, groupElement)
        return true
    }

    fun createGroupInXml(xml: String, groupPath: List<String>): String {
        if (groupPath.isEmpty()) return xml

        val root = JDOMUtil.load(xml)
        require(root.name == "project") { "Root element must be <project>, was <${root.name}>" }

        val parentPath = groupPath.dropLast(1)
        val name = groupPath.last()
        val parent = findOrCreateGroup(root, parentPath)
        if (findChildGroup(parent, name) != null) return xml

        createChildGroup(parent, name)
        return EwpDomSortFixer.sortAndFormat(root)
    }

    fun addFilesToXml(xml: String, ewpDirectory: String, additions: List<FileAddition>): String {
        if (additions.isEmpty()) return xml

        val root = JDOMUtil.load(xml)
        require(root.name == "project") { "Root element must be <project>, was <${root.name}>" }

        val existingPaths = collectExistingRawPaths(root).toMutableSet()
        var changed = false
        for (addition in additions) {
            val encodedPath = EwpPathEncoder.encode(ewpDirectory, addition.absolutePath) ?: continue
            if (existingPaths.contains(encodedPath)) continue

            val targetGroup = findOrCreateGroup(root, addition.groupPath)
            val fileElement = Element("file").addContent(Element("name").setText(encodedPath))
            targetGroup.addContent(fileElement)
            existingPaths += encodedPath
            changed = true
        }

        if (!changed) return xml
        return EwpDomSortFixer.sortAndFormat(root)
    }

    @JvmOverloads
    fun removeFilesToXml(
        xml: String,
        ewpDirectory: String,
        absolutePaths: Collection<String>,
        rawPaths: Collection<String> = emptyList(),
    ): String = removeFromEwpToXml(xml, ewpDirectory, absolutePaths, rawPaths, emptyList())

    @JvmOverloads
    fun removeFromEwpToXml(
        xml: String,
        ewpDirectory: String,
        absolutePaths: Collection<String> = emptyList(),
        rawPaths: Collection<String> = emptyList(),
        groupPaths: Collection<List<String>> = emptyList(),
    ): String {
        if (absolutePaths.isEmpty() && rawPaths.isEmpty() && groupPaths.isEmpty()) return xml

        val root = JDOMUtil.load(xml)
        require(root.name == "project") { "Root element must be <project>, was <${root.name}>" }

        var changed = false
        for (groupPath in filterRedundantGroupPaths(groupPaths)) {
            if (removeGroupAtPath(root, groupPath)) {
                changed = true
            }
        }

        val normalizedTargets = absolutePaths.map { normalizePath(it) }.toSet()
        val normalizedRawTargets = rawPaths.map { normalizeRawPath(it) }.toSet()
        if (normalizedTargets.isNotEmpty() || normalizedRawTargets.isNotEmpty()) {
            if (removeMatchingFiles(root, ewpDirectory, normalizedTargets, normalizedRawTargets)) {
                changed = true
            }
        }

        if (!changed) return xml

        pruneEmptyGroups(root)
        return EwpDomSortFixer.sortAndFormat(root)
    }

    fun filterRedundantGroupPaths(groupPaths: Collection<List<String>>): List<List<String>> {
        val paths = groupPaths.filter { it.isNotEmpty() }
        return paths.filter { candidate ->
            paths.none { other ->
                other != candidate && other.size < candidate.size && isDescendantOrSelf(other, candidate)
            }
        }
    }

    private fun removeGroupAtPath(root: Element, groupPath: List<String>): Boolean {
        if (groupPath.isEmpty()) return false
        val parentPath = groupPath.dropLast(1)
        val groupName = groupPath.last()
        val parent = if (parentPath.isEmpty()) root else findGroup(root, parentPath) ?: return false
        val groupElement = findChildGroup(parent, groupName) ?: return false
        parent.removeContent(groupElement)
        return true
    }

    fun moveFilesToXml(xml: String, ewpDirectory: String, moves: List<FileMove>): String {
        if (moves.isEmpty()) return xml

        val root = JDOMUtil.load(xml)
        require(root.name == "project") { "Root element must be <project>, was <${root.name}>" }

        val existingPaths = collectExistingRawPaths(root).toMutableSet()
        var changed = false

        for (move in moves) {
            val oldPath = normalizePath(move.oldAbsolutePath)
            if (!removeMatchingFiles(root, ewpDirectory, setOf(oldPath), emptySet())) {
                continue
            }
            changed = true
            existingPaths.clear()
            existingPaths.addAll(collectExistingRawPaths(root))

            val encodedPath = EwpPathEncoder.encode(ewpDirectory, move.newAbsolutePath) ?: continue
            if (existingPaths.contains(encodedPath)) continue

            val targetGroup = findOrCreateGroup(root, move.newGroupPath)
            val fileElement = Element("file").addContent(Element("name").setText(encodedPath))
            targetGroup.addContent(fileElement)
            existingPaths += encodedPath
        }

        if (!changed) return xml
        pruneEmptyGroups(root)
        return EwpDomSortFixer.sortAndFormat(root)
    }

    private fun removeMatchingFiles(
        container: Element,
        ewpDirectory: String,
        normalizedTargets: Set<String>,
        normalizedRawTargets: Set<String> = emptySet(),
    ): Boolean {
        var removed = false
        val children = container.children.toList()
        for (child in children) {
            when (child.name) {
                "file" -> {
                    val rawPath = child.getChildTextTrim("name") ?: continue
                    val normalizedRaw = normalizeRawPath(rawPath)
                    val resolved = EwpPathResolver.resolve(ewpDirectory, rawPath)?.let { normalizePath(it) }
                    val matchesAbsolute = resolved != null && normalizedTargets.any { FileUtil.pathsEqual(it, resolved) }
                    val matchesRaw = normalizedRawTargets.any { FileUtil.pathsEqual(it, normalizedRaw) }
                    if (matchesAbsolute || matchesRaw) {
                        container.removeContent(child)
                        removed = true
                    }
                }
                "group" -> {
                    if (removeMatchingFiles(child, ewpDirectory, normalizedTargets, normalizedRawTargets)) {
                        removed = true
                    }
                }
            }
        }
        return removed
    }

    private fun pruneEmptyGroups(container: Element) {
        for (group in container.children.filter { it.name == "group" }.toList()) {
            pruneEmptyGroups(group)
            val hasContent = group.children.any { it.name == "file" || it.name == "group" }
            if (!hasContent) {
                container.removeContent(group)
            }
        }
    }

    private fun collectExistingRawPaths(root: Element): Set<String> {
        val paths = mutableSetOf<String>()
        collectFilePaths(root, paths)
        return paths
    }

    private fun collectFilePaths(container: Element, paths: MutableSet<String>) {
        for (child in container.children) {
            when (child.name) {
                "file" -> child.getChildTextTrim("name")?.let { paths += it }
                "group" -> collectFilePaths(child, paths)
            }
        }
    }

    fun findOrCreateGroup(root: Element, groupPath: List<String>): Element {
        var current = root
        for (name in groupPath) {
            current = findChildGroup(current, name) ?: createChildGroup(current, name)
        }
        return current
    }

    fun findGroup(root: Element, groupPath: List<String>): Element? {
        var current: Element = root
        for (name in groupPath) {
            current = findChildGroup(current, name) ?: return null
        }
        return current
    }

    fun findChildGroup(parent: Element, name: String): Element? =
        parent.children
            .filter { it.name == "group" }
            .firstOrNull { it.getChildTextTrim("name") == name }

    fun isDescendantOrSelf(ancestor: List<String>, candidate: List<String>): Boolean {
        if (candidate.size < ancestor.size) return false
        return candidate.take(ancestor.size) == ancestor
    }

    private fun normalizeRawPath(path: String): String =
        path.trim().replace('\\', '/')

    private fun addContentToContainer(container: Element, element: Element) {
        val childElements = container.children.toList()
        val lastGroupOrFile = childElements.indexOfLast { it.name == "group" || it.name == "file" }
        val insertIndex = if (lastGroupOrFile >= 0) {
            lastGroupOrFile + 1
        } else {
            childElements.takeWhile { it.name != "group" && it.name != "file" && it.name != "projectSettings" }.size
        }
        if (insertIndex in 0..childElements.size) {
            container.addContent(insertIndex, element)
        } else {
            container.addContent(element)
        }
    }

    private fun createChildGroup(parent: Element, name: String): Element {
        val group = Element("group")
        group.addContent(Element("name").setText(name))
        addContentToContainer(parent, group)
        return group
    }

    private fun normalizePath(path: String): String =
        FileUtil.toSystemIndependentName(path.trimEnd('/'))
}
