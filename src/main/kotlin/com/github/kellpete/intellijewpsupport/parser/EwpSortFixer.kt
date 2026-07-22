package com.github.kellpete.intellijewpsupport.parser

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VfsUtilCore

object EwpSortFixer {

    fun fixSorting(ewpFile: VirtualFile, project: Project) {
        WriteCommandAction.runWriteCommandAction(project) {
            val document = FileDocumentManager.getInstance().getDocument(ewpFile)
            val originalText = document?.text ?: VfsUtilCore.loadText(ewpFile)
            val sortedText = EwpDomSortFixer.sortAndFormat(originalText)
            if (sortedText == originalText) {
                return@runWriteCommandAction
            }

            if (document != null) {
                document.setText(sortedText)
            } else {
                VfsUtilCore.saveText(ewpFile, sortedText)
            }
        }
    }
}
