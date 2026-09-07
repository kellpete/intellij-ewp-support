package com.github.kellpete.intellijewpsupport.parser

import com.github.kellpete.intellijewpsupport.ewp.EwpDocumentFormatter
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

object EwpSortFixer {

    fun fixSorting(ewpFile: VirtualFile, project: Project) {
        EwpDocumentFormatter.fixSorting(ewpFile, project)
    }
}
