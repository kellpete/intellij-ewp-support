package com.github.kellpete.intellijewpsupport.model

import com.intellij.openapi.vfs.VirtualFile

data class EwpProject(
    val ewpFile: VirtualFile,
    val groups: List<EwpGroup>,
    val sortIssues: List<EwpSortIssue>,
) {
    fun hasSortIssue(groupPath: List<String>): Boolean =
        sortIssues.any { it.groupPath == groupPath }

    val sortIssueCount: Int
        get() = sortIssues.map { it.groupPath }.distinct().size
}
