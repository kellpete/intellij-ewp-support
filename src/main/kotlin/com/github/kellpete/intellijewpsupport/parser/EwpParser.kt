package com.github.kellpete.intellijewpsupport.parser

import com.github.kellpete.intellijewpsupport.model.EwpFileEntry
import com.github.kellpete.intellijewpsupport.model.EwpGroup
import com.github.kellpete.intellijewpsupport.model.EwpProject
import com.github.kellpete.intellijewpsupport.model.EwpSortIssue
import com.github.kellpete.intellijewpsupport.model.EwpSortValidator
import com.github.kellpete.intellijewpsupport.path.EwpPathResolver
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VfsUtilCore
import org.jdom.Element
import java.io.IOException

class EwpParser {

    fun parse(ewpFile: VirtualFile): EwpProject {
        val rootElement = try {
            JDOMUtil.load(VfsUtilCore.loadText(ewpFile))
        } catch (e: Exception) {
            throw IOException("Failed to parse EWP file ${ewpFile.path}", e)
        }

        require(rootElement.name == "project") { "Root element must be <project>, was <${rootElement.name}>" }

        val sortIssues = mutableListOf<EwpSortIssue>()
        val groups = rootElement.getChildren("group").map { parseGroup(it, ewpFile, emptyList(), sortIssues) }

        sortIssues += EwpSortValidator.validateGroups(groups)

        return EwpProject(
            ewpFile = ewpFile,
            groups = groups,
            sortIssues = sortIssues,
        )
    }

    private fun parseGroup(
        element: Element,
        ewpFile: VirtualFile,
        parentPath: List<String>,
        sortIssues: MutableList<EwpSortIssue>,
    ): EwpGroup {
        val name = element.getChildTextTrim("name") ?: ""
        val groupPath = parentPath + name

        val childGroups = mutableListOf<EwpGroup>()
        val childFiles = mutableListOf<EwpFileEntry>()
        var sawFile = false
        var groupsAfterFiles = false

        for (child in element.children) {
            when (child.name) {
                "group" -> {
                    if (sawFile) groupsAfterFiles = true
                    childGroups += parseGroup(child, ewpFile, groupPath, sortIssues)
                }
                "file" -> {
                    sawFile = true
                    val rawPath = child.getChildTextTrim("name") ?: continue
                    childFiles += EwpFileEntry(
                        rawPath = rawPath,
                        resolvedPath = EwpPathResolver.resolve(ewpFile, rawPath),
                    )
                }
            }
        }

        sortIssues += EwpSortValidator.validateGroupOrder(groupsAfterFiles, groupPath)

        return EwpGroup(
            name = name,
            groups = childGroups,
            files = childFiles,
        )
    }
}
