package com.github.kellpete.intellijewpsupport.ewp.editor

import com.github.kellpete.intellijewpsupport.parser.EwpDomSortFixer
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.vfs.VirtualFile
import org.jdom.Document
import org.jdom.Element
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

object EwpFileWriter {

    data class FileAddition(
        val absolutePath: String,
        val groupPath: List<String>,
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

    fun addFilesToXml(xml: String, ewpDirectory: String, additions: List<FileAddition>): String {
        if (additions.isEmpty()) return xml

        val document: Document = JDOMUtil.loadDocument(ByteArrayInputStream(xml.toByteArray(StandardCharsets.UTF_8)))
        val root = document.rootElement
        require(root.name == "project") { "Root element must be <project>, was <${root.name}>" }

        val existingPaths = collectExistingRawPaths(root).toMutableSet()
        for (addition in additions) {
            val encodedPath = EwpPathEncoder.encode(ewpDirectory, addition.absolutePath) ?: continue
            if (existingPaths.contains(encodedPath)) continue

            val targetGroup = findOrCreateGroup(root, addition.groupPath)
            val fileElement = Element("file").addContent(Element("name").setText(encodedPath))
            targetGroup.addContent(fileElement)
            existingPaths += encodedPath
        }

        return EwpDomSortFixer.sortAndFormat(documentToString(document))
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

    private fun documentToString(document: Document): String {
        val outputter = org.jdom.output.XMLOutputter()
        return outputter.outputString(document)
    }
}
