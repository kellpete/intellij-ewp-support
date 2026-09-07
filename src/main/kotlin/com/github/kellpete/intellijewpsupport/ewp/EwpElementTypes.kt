package com.github.kellpete.intellijewpsupport.ewp

import com.intellij.psi.tree.IFileElementType

object EwpElementTypes {
    @JvmField
    val FILE: IFileElementType = IFileElementType("EWP_FILE", EwpLanguage)
}
