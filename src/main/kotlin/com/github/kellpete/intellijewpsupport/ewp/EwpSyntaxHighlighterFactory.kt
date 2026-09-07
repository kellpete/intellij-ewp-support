package com.github.kellpete.intellijewpsupport.ewp

import com.intellij.lang.xml.XMLLanguage
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class EwpSyntaxHighlighterFactory : SyntaxHighlighterFactory() {

    override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?): SyntaxHighlighter {
        return SyntaxHighlighterFactory.getSyntaxHighlighter(XMLLanguage.INSTANCE, project, virtualFile)
    }
}
