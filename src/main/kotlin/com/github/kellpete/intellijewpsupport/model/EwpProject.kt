package com.github.kellpete.intellijewpsupport.model

import com.intellij.openapi.vfs.VirtualFile

data class EwpProject(
    val ewpFile: VirtualFile,
    val groups: List<EwpGroup>,
    val sortIssues: List<EwpSortIssue>,
    val missingFiles: List<EwpMissingFileIssue> = emptyList(),
) {
    val sortIssueCount: Int
        get() = sortIssues.map { it.groupPath }.distinct().size

    val missingFileCount: Int
        get() = missingFiles.size
}
