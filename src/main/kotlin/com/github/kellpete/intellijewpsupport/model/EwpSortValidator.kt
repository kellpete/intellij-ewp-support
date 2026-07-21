package com.github.kellpete.intellijewpsupport.model

object EwpSortValidator {

    fun validateGroups(groups: List<EwpGroup>, parentPath: List<String> = emptyList()): List<EwpSortIssue> {
        val issues = mutableListOf<EwpSortIssue>()
        for (group in groups) {
            val path = parentPath + group.name
            issues.addAll(validateGroup(group, path))
        }
        return issues
    }

    fun validateGroupOrder(groupsAfterFiles: Boolean, groupPath: List<String>): List<EwpSortIssue> {
        if (!groupsAfterFiles) return emptyList()
        return listOf(EwpSortIssue(groupPath, EwpSortIssueType.GROUPS_AFTER_FILES))
    }

    private fun validateGroup(group: EwpGroup, path: List<String>): List<EwpSortIssue> {
        val issues = mutableListOf<EwpSortIssue>()

        if (!isAlphabeticallySorted(group.groups.map { it.name })) {
            issues += EwpSortIssue(path, EwpSortIssueType.SUBGROUPS_NOT_SORTED)
        }
        if (!isAlphabeticallySorted(group.files.map { it.rawPath })) {
            issues += EwpSortIssue(path, EwpSortIssueType.FILES_NOT_SORTED)
        }

        issues += validateGroups(group.groups, path)
        return issues
    }

    private fun isAlphabeticallySorted(names: List<String>): Boolean {
        if (names.size <= 1) return true
        val sorted = names.sortedWith(String.CASE_INSENSITIVE_ORDER)
        return names == sorted
    }
}
