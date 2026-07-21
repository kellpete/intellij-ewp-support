package com.github.kellpete.intellijewpsupport.model

enum class EwpSortIssueType {
    GROUPS_AFTER_FILES,
    SUBGROUPS_NOT_SORTED,
    FILES_NOT_SORTED,
}

data class EwpSortIssue(
    val groupPath: List<String>,
    val type: EwpSortIssueType,
)
