package com.github.kellpete.intellijewpsupport.parser

internal object EwpTextSortFixer {

    fun sortGroupsInText(text: String): String {
        val projectStart = findProjectElementStart(text) ?: return text
        val project = readElement(text, projectStart) ?: return text
        val sorted = rebuildSortedProject(text, project)
        return if (sorted == text) text else sorted
    }

    private fun rebuildSortedProject(text: String, project: ElementMatch): String {
        val children = findDirectChildSpans(text, project)
        val groupChildren = children.filter { it.tagName == "group" }
        if (groupChildren.isEmpty()) {
            return text
        }

        val sortedGroups = groupChildren.sortedWith(groupSpanOrder(text))
        if (!needsProjectRebuild(text, children, sortedGroups)) {
            return text
        }

        return rebuildProjectContent(text, project, children, sortedGroups)
    }

    private fun needsProjectRebuild(
        text: String,
        children: List<ChildSpan>,
        sortedGroups: List<ChildSpan>,
    ): Boolean {
        val originalGroups = children.filter { it.tagName == "group" }
        if (originalGroups.map { it.elementStart } != sortedGroups.map { it.elementStart }) {
            return true
        }
        return originalGroups.any { span ->
            val element = readElement(text, span.elementStart) ?: return@any false
            rebuildSortedGroup(text, element) != text.substring(element.start, element.end)
        }
    }

    private fun rebuildProjectContent(
        text: String,
        project: ElementMatch,
        children: List<ChildSpan>,
        sortedGroups: List<ChildSpan>,
    ): String {
        return buildString {
            append(text, 0, project.start)
            append(text, project.start, project.contentStart)
            var groupIndex = 0
            for (child in children) {
                if (child.tagName == "group") {
                    append(rebuildSortedGroupSpan(text, sortedGroups[groupIndex++]))
                } else {
                    append(text, child.leadingStart, child.elementEnd)
                }
            }
            append(trailingBeforeClose(text, children, project.contentEnd))
            append(text, project.contentEnd, project.end)
            append(text, project.end, text.length)
        }
    }

    private fun rebuildSortedGroupSpan(text: String, span: ChildSpan): String {
        val element = readElement(text, span.elementStart)
            ?: return text.substring(span.leadingStart, span.elementEnd)
        val sortedElement = rebuildSortedGroup(text, element)
        return buildString {
            append(text, span.leadingStart, span.elementStart)
            append(sortedElement)
        }
    }

    private fun rebuildSortedGroup(text: String, group: ElementMatch): String {
        val children = findDirectChildSpans(text, group)
        val firstReorderableIndex = children.indexOfFirst { it.tagName == "group" || it.tagName == "file" }
        if (firstReorderableIndex == -1) {
            return text.substring(group.start, group.end)
        }

        val prefixChildren = children.subList(0, firstReorderableIndex)
        val reorderableChildren = children
            .drop(firstReorderableIndex)
            .takeWhile { it.tagName == "group" || it.tagName == "file" }
        val suffixChildren = children.drop(firstReorderableIndex + reorderableChildren.size)

        val sortedGroups = reorderableChildren
            .filter { it.tagName == "group" }
            .sortedWith(groupSpanOrder(text))
        val sortedFiles = reorderableChildren
            .filter { it.tagName == "file" }
            .sortedWith(fileSpanOrder(text))

        val originalGroups = reorderableChildren.filter { it.tagName == "group" }
        val originalFiles = reorderableChildren.filter { it.tagName == "file" }
        val groupsUnchanged = originalGroups.map { it.elementStart } == sortedGroups.map { it.elementStart }
        val filesUnchanged = originalFiles.map { it.elementStart } == sortedFiles.map { it.elementStart }
        val nestedUnchanged = sortedGroups.all { span ->
            val element = readElement(text, span.elementStart) ?: return@all true
            rebuildSortedGroup(text, element) == text.substring(element.start, element.end)
        }

        if (groupsUnchanged && filesUnchanged && nestedUnchanged) {
            return text.substring(group.start, group.end)
        }

        return buildString {
            append(text, group.start, group.contentStart)
            prefixChildren.forEach { append(text, it.leadingStart, it.elementEnd) }
            sortedGroups.forEach { append(rebuildSortedGroupSpan(text, it)) }
            sortedFiles.forEach { append(text, it.leadingStart, it.elementEnd) }
            suffixChildren.forEach { append(text, it.leadingStart, it.elementEnd) }
            append(trailingBeforeClose(text, children, group.contentEnd))
            append(text, group.contentEnd, group.end)
        }
    }

    private fun trailingBeforeClose(text: String, children: List<ChildSpan>, contentEnd: Int): String {
        if (children.isEmpty()) return ""
        return text.substring(children.last().elementEnd, contentEnd)
    }

    private fun groupSpanOrder(text: String): Comparator<ChildSpan> =
        compareBy(String.CASE_INSENSITIVE_ORDER) { span ->
            val element = readElement(text, span.elementStart)
            if (element == null) "" else extractSortKey(text, element)
        }

    private fun fileSpanOrder(text: String): Comparator<ChildSpan> =
        compareBy(String.CASE_INSENSITIVE_ORDER) { span ->
            val element = readElement(text, span.elementStart)
            if (element == null) "" else extractFileSortKey(text, element)
        }

    private fun extractSortKey(text: String, element: ElementMatch): String {
        val nameSpan = findDirectChildSpans(text, element).firstOrNull { it.tagName == "name" } ?: return ""
        val nameElement = readElement(text, nameSpan.elementStart) ?: return ""
        return text.substring(nameElement.contentStart, nameElement.contentEnd).trim()
    }

    private fun extractFileSortKey(text: String, element: ElementMatch): String {
        return filenameComponent(extractSortKey(text, element))
    }

    private fun filenameComponent(path: String): String {
        val normalized = path.replace('\\', '/')
        val lastSlash = normalized.lastIndexOf('/')
        return if (lastSlash >= 0) normalized.substring(lastSlash + 1) else normalized
    }

    private fun findProjectElementStart(text: String): Int? {
        var pos = 0
        while (pos < text.length) {
            pos = skipLeadingMarkup(text, pos)
            if (pos >= text.length) return null

            if (text.startsWith("<project", pos)) {
                return pos
            }

            val tagName = readTagNameAt(text, pos) ?: return null
            if (tagName != "project") {
                return null
            }
            return pos
        }
        return null
    }

    private fun skipLeadingMarkup(text: String, pos: Int): Int {
        var index = skipWhitespaceAndBom(text, pos, text.length)
        while (index < text.length) {
            when {
                text.startsWith("<?", index) -> {
                    val end = text.indexOf("?>", index)
                    if (end == -1) return text.length
                    index = end + 2
                }
                text.startsWith("<!--", index) -> {
                    val end = text.indexOf("-->", index)
                    if (end == -1) return text.length
                    index = end + 3
                }
                text.startsWith("<!", index) -> {
                    val end = text.indexOf('>', index)
                    if (end == -1) return text.length
                    index = end + 1
                }
                else -> return index
            }
            index = skipWhitespaceAndBom(text, index, text.length)
        }
        return index
    }

    private fun findDirectChildSpans(text: String, container: ElementMatch): List<ChildSpan> {
        val children = mutableListOf<ChildSpan>()
        var pos = container.contentStart

        while (pos < container.contentEnd) {
            val leadingStart = pos
            val elementStart = findNextElementStart(text, pos, container.contentEnd)
            if (elementStart == -1) break

            val element = readElement(text, elementStart) ?: break
            children += ChildSpan(
                leadingStart = leadingStart,
                elementStart = elementStart,
                elementEnd = element.end,
                tagName = element.tagName,
            )
            pos = element.end
        }

        return children
    }

    private fun findNextElementStart(text: String, pos: Int, end: Int): Int {
        var index = pos
        while (index < end) {
            index = skipWhitespaceAndBom(text, index, end)
            if (index >= end) return -1

            if (text.startsWith("<!--", index)) {
                val commentEnd = text.indexOf("-->", index)
                if (commentEnd == -1) return -1
                index = commentEnd + 3
                continue
            }

            if (text.startsWith("<!", index)) {
                val declEnd = text.indexOf('>', index)
                if (declEnd == -1) return -1
                index = declEnd + 1
                continue
            }

            if (text.startsWith("</", index)) return -1
            if (text[index] == '<') return index
            return -1
        }
        return -1
    }

    private fun readElement(text: String, start: Int): ElementMatch? {
        if (start >= text.length || text[start] != '<') return null

        val openTagEnd = text.indexOf('>', start)
        if (openTagEnd == -1) return null

        val openTag = text.substring(start, openTagEnd + 1)
        if (openTag.startsWith("<?") || openTag.startsWith("<!")) return null

        val tagName = extractTagName(openTag) ?: return null
        if (isSelfClosingTag(openTag)) {
            return ElementMatch(tagName, start, openTagEnd + 1, openTagEnd + 1, openTagEnd + 1)
        }

        val contentStart = openTagEnd + 1
        var depth = 1
        var pos = contentStart

        while (pos < text.length && depth > 0) {
            val tagStart = text.indexOf('<', pos)
            if (tagStart == -1) return null

            when {
                text.startsWith("<!--", tagStart) -> {
                    val commentEnd = text.indexOf("-->", tagStart)
                    if (commentEnd == -1) return null
                    pos = commentEnd + 3
                }
                text.startsWith("<![CDATA[", tagStart) -> {
                    val cdataEnd = text.indexOf("]]>", tagStart)
                    if (cdataEnd == -1) return null
                    pos = cdataEnd + 3
                }
                text.startsWith("<!", tagStart) -> {
                    val declEnd = text.indexOf('>', tagStart)
                    if (declEnd == -1) return null
                    pos = declEnd + 1
                }
                text.startsWith("</", tagStart) -> {
                    depth--
                    val closeEnd = text.indexOf('>', tagStart)
                    if (closeEnd == -1) return null
                    if (depth == 0) {
                        return ElementMatch(tagName, start, closeEnd + 1, contentStart, tagStart)
                    }
                    pos = closeEnd + 1
                }
                else -> {
                    val innerTagEnd = text.indexOf('>', tagStart)
                    if (innerTagEnd == -1) return null
                    val innerTag = text.substring(tagStart, innerTagEnd + 1)
                    if (!isSelfClosingTag(innerTag) && !innerTag.startsWith("<?")) {
                        depth++
                    }
                    pos = innerTagEnd + 1
                }
            }
        }

        return null
    }

    private fun readTagNameAt(text: String, start: Int): String? {
        if (start >= text.length || text[start] != '<') return null
        val tagEnd = text.indexOf('>', start)
        if (tagEnd == -1) return null
        return extractTagName(text.substring(start, tagEnd + 1))
    }

    private fun extractTagName(tag: String): String? {
        val trimmed = tag.trim().removePrefix("<").removePrefix("/")
        if (trimmed.isEmpty()) return null

        val nameEnd = trimmed.indexOfFirst { it.isWhitespace() || it == '>' || it == '/' }
        val name = if (nameEnd == -1) trimmed else trimmed.substring(0, nameEnd)
        return name.takeIf { it.isNotEmpty() }
    }

    private fun isSelfClosingTag(tag: String): Boolean {
        val trimmed = tag.trimEnd()
        return trimmed.endsWith("/>")
    }

    private fun skipWhitespaceAndBom(text: String, pos: Int, end: Int): Int {
        var index = pos
        while (index < end) {
            if (text[index] == '\uFEFF') {
                index++
                continue
            }
            if (!text[index].isWhitespace()) {
                break
            }
            index++
        }
        return index
    }

    private data class ElementMatch(
        val tagName: String,
        val start: Int,
        val end: Int,
        val contentStart: Int,
        val contentEnd: Int,
    )

    private data class ChildSpan(
        val leadingStart: Int,
        val elementStart: Int,
        val elementEnd: Int,
        val tagName: String,
    )
}
