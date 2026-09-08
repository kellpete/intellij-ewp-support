package com.github.kellpete.intellijewpsupport.parser

import com.intellij.openapi.util.JDOMUtil
import org.jdom.Document
import org.jdom.Element
import org.jdom.output.Format
import org.jdom.output.XMLOutputter
import java.io.ByteArrayInputStream
import java.io.StringWriter
import java.nio.charset.StandardCharsets

object EwpDomSortFixer {

    fun sortAndFormat(text: String): String {
        val document = JDOMUtil.loadDocument(ByteArrayInputStream(text.toByteArray(StandardCharsets.UTF_8)))
        val root = document.rootElement
        require(root.name == "project") { "Root element must be <project>, was <${root.name}>" }
        sortContainerChildren(root)
        return formatDocument(document)
    }

    private fun sortContainerChildren(container: Element) {
        val childElements = container.children.toList()
        val prefix = childElements.takeWhile { it.name != "group" && it.name != "file" }
        val reorderable = childElements
            .drop(prefix.size)
            .takeWhile { it.name == "group" || it.name == "file" }
        val suffix = childElements.drop(prefix.size + reorderable.size)

        val sortedGroups = reorderable
            .filter { it.name == "group" }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.getChildTextTrim("name").orEmpty() })
        val sortedFiles = reorderable
            .filter { it.name == "file" }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { fileSortKey(it) })

        sortedGroups.forEach { sortContainerChildren(it) }

        container.removeContent()
        (prefix + sortedGroups + sortedFiles + suffix).forEach { container.addContent(it) }
    }

    private fun fileSortKey(fileElement: Element): String {
        val rawPath = fileElement.getChildTextTrim("name") ?: return ""
        val path = rawPath.replace('\\', '/')
        val lastSlash = path.lastIndexOf('/')
        return if (lastSlash >= 0) path.substring(lastSlash + 1) else path
    }

    private fun formatDocument(document: Document): String {
        val format = Format.getPrettyFormat()
            .setIndent("    ")
            .setExpandEmptyElements(true)
            .setEncoding("UTF-8")
            .setLineSeparator("\n")
        val outputter = XMLOutputter(format)

        val writer = StringWriter()
        outputter.output(document, writer)
        val result = writer.toString()
        val normalized = collapseEmptyDataTags(if (result.endsWith("\n")) result else "$result\n")
        val trimmed = normalized.trimEnd('\n') + '\n'
        return trimmed
    }

    private fun collapseEmptyDataTags(text: String): String {
        return text
            .replace(DATA_EXPANDED) { match ->
                val attributes = match.groupValues[1].trim()
                if (attributes.isEmpty()) "<data />" else "<data $attributes />"
            }
    }

    private val DATA_EXPANDED = Regex("<data(\\s[^>]*)?>\\s*</data>")
}
