package com.github.kellpete.intellijewpsupport.ewp

import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import javax.swing.Icon

class EwpFileType : LanguageFileType(EwpLanguage) {

    override fun getName(): String = "EWP"

    override fun getDescription(): String = "EWARM project"

    override fun getDefaultExtension(): String = "ewp"

    override fun getIcon(): Icon? = null

    companion object {
        @JvmField
        val INSTANCE = EwpFileType()

        fun isEwp(file: VirtualFile?): Boolean = file?.extension?.equals("ewp", ignoreCase = true) == true

        fun isEwp(file: PsiFile): Boolean = isEwp(file.virtualFile)
    }
}
