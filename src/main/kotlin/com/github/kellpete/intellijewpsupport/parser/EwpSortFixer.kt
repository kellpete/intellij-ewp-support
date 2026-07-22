package com.github.kellpete.intellijewpsupport.parser

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

object EwpSortFixer {

    fun fixSorting(ewpFile: VirtualFile, project: Project) {
        WriteCommandAction.runWriteCommandAction(project) {
            val fdm = FileDocumentManager.getInstance()
            val document = fdm.getDocument(ewpFile, project) ?: return@runWriteCommandAction

            val originalText = document.text
            val sortedText = EwpDomSortFixer.sortAndFormat(originalText)
            if (sortedText == originalText) {
                return@runWriteCommandAction
            }

            // Set fixed windows line endings
            // If not doing this, CLion seems to switch to Unix endings instead. The problem is not limited to the
            // plugin, so I think it is a CLion bug. Not sure what triggers this issue.
            ewpFile.detectedLineSeparator = "\r\n"

            document.setText(sortedText)
            fdm.saveDocument(document)
        }
    }
}
