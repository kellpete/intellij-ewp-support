package com.github.kellpete.intellijewpsupport.ewp

import com.github.kellpete.intellijewpsupport.parser.EwpDomSortFixer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile

object EwpDocumentFormatter {

    fun applySortAndFormat(document: Document, virtualFile: VirtualFile): Boolean {
        val sortedText = EwpDomSortFixer.sortAndFormat(document.text)
        if (sortedText == document.text) {
            return false
        }

        // Set fixed windows line endings
        // If not doing this, CLion seems to switch to Unix endings instead. The problem is not limited to the
        // plugin, so I think it is a CLion bug. Not sure what triggers this issue.
        ApplicationManager.getApplication().runWriteAction {
            virtualFile.detectedLineSeparator = "\r\n"
            document.setText(sortedText)
        }
        return true
    }

    fun applySortAndFormat(psiFile: PsiFile): Boolean {
        if (!EwpFileType.isEwp(psiFile)) {
            return false
        }
        val virtualFile = psiFile.virtualFile ?: return false
        val document = PsiDocumentManager.getInstance(psiFile.project).getDocument(psiFile) ?: return false
        return applySortAndFormat(document, virtualFile)
    }

    fun fixSorting(ewpFile: VirtualFile, project: Project) {
        WriteCommandAction.runWriteCommandAction(project) {
            val fdm = FileDocumentManager.getInstance()
            val document = fdm.getDocument(ewpFile) ?: return@runWriteCommandAction
            if (!applySortAndFormat(document, ewpFile)) {
                return@runWriteCommandAction
            }
            fdm.saveDocument(document)
        }
    }
}
