package com.github.kellpete.intellijewpsupport.parser

import com.github.kellpete.intellijewpsupport.model.EwpFileEntry
import com.github.kellpete.intellijewpsupport.model.EwpGroup
import com.github.kellpete.intellijewpsupport.model.EwpMissingFileValidator
import com.github.kellpete.intellijewpsupport.model.EwpProject
import com.github.kellpete.intellijewpsupport.model.EwpSortIssue
import com.github.kellpete.intellijewpsupport.model.EwpSortValidator
import com.github.kellpete.intellijewpsupport.path.EwpPathResolver
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VfsUtilCore
import org.jdom.Element
import java.io.IOException

class EwpParser {

    fun parse(ewpFile: VirtualFile): EwpProject {
        val rootElement = try {
            JDOMUtil.load(loadText(ewpFile))
        } catch (e: Exception) {
            throw IOException("Failed to parse EWP file ${ewpFile.path}", e)
        }

        require(rootElement.name == "project") { "Root element must be <project>, was <${rootElement.name}>" }

        val sortIssues = mutableListOf<EwpSortIssue>()
        val groups = mutableListOf<EwpGroup>()
        val files = mutableListOf<EwpFileEntry>()
        var sawFile = false
        var groupsAfterFiles = false
        for (child in rootElement.children) {
            when (child.name) {
                "group" -> {
                    if (sawFile) groupsAfterFiles = true
                    groups += parseGroup(child, ewpFile, emptyList(), sortIssues)
                }
                "file" -> {
                    sawFile = true
                    parseFile(child, ewpFile)?.let { files += it }
                }
            }
        }

        sortIssues += EwpSortValidator.validateGroupOrder(groupsAfterFiles, emptyList())
        sortIssues += EwpSortValidator.validateContainer(groups, files, emptyList())
        sortIssues += EwpSortValidator.validateGroups(groups)
        val missingFiles = EwpMissingFileValidator.findMissingFiles(groups) +
            EwpMissingFileValidator.findMissingInFiles(files, emptyList())

        return EwpProject(
            ewpFile = ewpFile,
            groups = groups,
            sortIssues = sortIssues,
            missingFiles = missingFiles,
            files = files,
        )
    }

    private fun parseFile(element: Element, ewpFile: VirtualFile): EwpFileEntry? {
        val rawPath = element.getChildTextTrim("name") ?: return null
        return EwpFileEntry(
            rawPath = rawPath,
            resolvedPath = EwpPathResolver.resolve(ewpFile, rawPath),
        )
    }

    private fun loadText(ewpFile: VirtualFile): String {
        val document = FileDocumentManager.getInstance().getDocument(ewpFile)
        return document?.text ?: VfsUtilCore.loadText(ewpFile)
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
                    parseFile(child, ewpFile)?.let { childFiles += it }
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
