package com.github.kellpete.intellijewpsupport.ewp.formatter

import com.github.kellpete.intellijewpsupport.ewp.EwpDocumentFormatter
import com.github.kellpete.intellijewpsupport.ewp.EwpFileType
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor

class EwpPostFormatProcessor : PostFormatProcessor {

    override fun processElement(source: PsiElement, settings: CodeStyleSettings): PsiElement {
        val file = source.containingFile
        if (!EwpFileType.isEwp(file)) {
            return source
        }
        EwpDocumentFormatter.applySortAndFormat(file)
        return source
    }

    override fun processText(source: PsiFile, rangeToReformat: TextRange, settings: CodeStyleSettings): TextRange {
        if (!EwpFileType.isEwp(source)) {
            return rangeToReformat
        }
        EwpDocumentFormatter.applySortAndFormat(source)
        return TextRange(0, source.textLength)
    }
}
