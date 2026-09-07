package com.github.kellpete.intellijewpsupport.ewp

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider

class EwpPsiFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, EwpLanguage) {

    override fun getFileType(): FileType = EwpFileType.INSTANCE
}
