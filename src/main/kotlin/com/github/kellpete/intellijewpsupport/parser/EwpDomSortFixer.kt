package com.github.kellpete.intellijewpsupport.parser

import com.intellij.openapi.util.JDOMUtil
import org.jdom.Document
import org.jdom.Element

object EwpDomSortFixer {

    fun sortAndFormat(text: String): String = sortAndFormat(JDOMUtil.load(text))

    fun sortAndFormat(root: Element): String {
        require(root.name == "project") { "Root element must be <project>, was <${root.name}>" }
        sortContainerChildren(root)
        return formatRoot(root)
    }

    private fun sortContainerChildren(container: Element) {
        val childElements = container.children.toList()
        val isReorderable = { e: Element -> e.name == "group" || e.name == "file" }
        val reorderable = childElements.filter(isReorderable)
        val others = childElements.filterNot(isReorderable)
        val isGroup = container.name == "group"
        val prefix: List<Element>
        val suffix: List<Element>
        if (isGroup) {
            // Inside a group: name, subgroups, files, then configurations and anything else.
            prefix = others.filter { it.name == "name" }
            suffix = others.filter { it.name != "name" }
        } else {
            val lastConfiguration = others.indexOfLast { it.name == "configuration" }
            val firstReorderable = childElements.indexOfFirst(isReorderable)
            // Groups and files go after the last configuration; otherwise stay where they were.
            val anchor = when {
                lastConfiguration >= 0 -> lastConfiguration + 1
                firstReorderable >= 0 -> childElements.take(firstReorderable).count { !isReorderable(it) }
                else -> others.size
            }
            prefix = others.take(anchor)
            suffix = others.drop(anchor)
        }

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

    private fun formatRoot(root: Element): String {
        val document = root.document ?: Document(root)
        return postProcess(JDOMUtil.writeDocument(document, "\n"))
    }

    private fun postProcess(xml: String): String {
        return xml
            .let(::expandSelfClosingTags)
            .let(::collapseEmptyDataTags)
            .let(::expandIndentToFourSpaces)
            .trimEnd('\n') + '\n'
    }

    private fun expandSelfClosingTags(xml: String): String =
        xml.replace(SELF_CLOSING) { match ->
            val name = match.groupValues[1]
            val attributes = match.groupValues[2].trim()
            if (attributes.isEmpty()) "<$name></$name>" else "<$name $attributes></$name>"
        }

    private fun collapseEmptyDataTags(text: String): String =
        text.replace(DATA_EXPANDED) { match ->
            val attributes = match.groupValues[1].trim()
            if (attributes.isEmpty()) "<data />" else "<data $attributes />"
        }

    private fun expandIndentToFourSpaces(xml: String): String =
        xml.lineSequence().joinToString("\n") { line ->
            val spaces = line.takeWhile { it == ' ' }.length
            if (spaces == 0) {
                line
            } else {
                val depth = spaces / PLATFORM_INDENT_WIDTH
                "    ".repeat(depth) + line.substring(spaces)
            }
        }

    private const val PLATFORM_INDENT_WIDTH = 2
    private val SELF_CLOSING = Regex("""<([A-Za-z_][\w.-]*)([^>]*)/>""")
    private val DATA_EXPANDED = Regex("<data(\\s[^>]*)?>\\s*</data>")
}
