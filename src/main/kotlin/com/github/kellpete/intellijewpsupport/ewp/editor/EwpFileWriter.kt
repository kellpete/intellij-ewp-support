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

    fun removeFiles(ewpFile: VirtualFile, project: Project, absolutePaths: Collection<String>) {
        if (absolutePaths.isEmpty()) return

        WriteCommandAction.runWriteCommandAction(project) {
            val document = FileDocumentManager.getInstance().getDocument(ewpFile) ?: return@runWriteCommandAction
            val ewpDirectory = ewpFile.parent.path
            val updatedText = removeFilesToXml(document.text, ewpDirectory, absolutePaths)
            if (updatedText != document.text) {
                document.setText(updatedText)
                FileDocumentManager.getInstance().saveDocument(document)
            }
        }
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

    fun removeFilesToXml(xml: String, ewpDirectory: String, absolutePaths: Collection<String>): String {
        if (absolutePaths.isEmpty()) return xml

        val root = JDOMUtil.load(xml)
        require(root.name == "project") { "Root element must be <project>, was <${root.name}>" }

        val normalizedTargets = absolutePaths.map { normalizePath(it) }.toSet()
        if (!removeMatchingFiles(root, ewpDirectory, normalizedTargets)) {
            return xml
        }

        pruneEmptyGroups(root)
        return EwpDomSortFixer.sortAndFormat(root)
    }

    fun moveFilesToXml(xml: String, ewpDirectory: String, moves: List<FileMove>): String {
        if (moves.isEmpty()) return xml

        val root = JDOMUtil.load(xml)
        require(root.name == "project") { "Root element must be <project>, was <${root.name}>" }

        val existingPaths = collectExistingRawPaths(root).toMutableSet()
        var changed = false

        for (move in moves) {
            val oldPath = normalizePath(move.oldAbsolutePath)
            if (!removeMatchingFiles(root, ewpDirectory, setOf(oldPath))) {
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
    ): Boolean {
        var removed = false
        val children = container.children.toList()
        for (child in children) {
            when (child.name) {
                "file" -> {
                    val rawPath = child.getChildTextTrim("name") ?: continue
                    val resolved = EwpPathResolver.resolve(ewpDirectory, rawPath)?.let { normalizePath(it) }
                    if (resolved != null && normalizedTargets.contains(resolved)) {
                        container.removeContent(child)
                        removed = true
                    }
                }
                "group" -> {
                    if (removeMatchingFiles(child, ewpDirectory, normalizedTargets)) {
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

    private fun findOrCreateGroup(root: Element, groupPath: List<String>): Element {
        var current = root
        for (name in groupPath) {
            current = findChildGroup(current, name) ?: createChildGroup(current, name)
        }
        return current
    }

    private fun findChildGroup(parent: Element, name: String): Element? =
        parent.children
            .filter { it.name == "group" }
            .firstOrNull { it.getChildTextTrim("name") == name }

    private fun createChildGroup(parent: Element, name: String): Element {
        val group = Element("group")
        group.addContent(Element("name").setText(name))
        parent.addContent(group)
        return group
    }

    private fun normalizePath(path: String): String =
        FileUtil.toSystemIndependentName(path.trimEnd('/'))
}
