package com.github.kellpete.intellijewpsupport.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EwpSortValidatorTest {

    @Test
    fun detectsUnsortedSubgroups() {
        val groups = listOf(
            EwpGroup(
                name = "root",
                groups = listOf(
                    EwpGroup("zebra", emptyList(), emptyList()),
                    EwpGroup("alpha", emptyList(), emptyList()),
                ),
                files = emptyList(),
            ),
        )

        val issues = EwpSortValidator.validateGroups(groups)

        assertEquals(1, issues.size)
        assertEquals(EwpSortIssueType.SUBGROUPS_NOT_SORTED, issues.single().type)
        assertEquals(listOf("root"), issues.single().groupPath)
    }

    @Test
    fun detectsUnsortedFiles() {
        val groups = listOf(
            EwpGroup(
                name = "root",
                groups = emptyList(),
                files = listOf(
                    EwpFileEntry("z.cpp", "/z.cpp"),
                    EwpFileEntry("a.cpp", "/a.cpp"),
                ),
            ),
        )

        val issues = EwpSortValidator.validateGroups(groups)

        assertEquals(1, issues.size)
        assertEquals(EwpSortIssueType.FILES_NOT_SORTED, issues.single().type)
    }

    @Test
    fun acceptsCorrectlySortedGroup() {
        val groups = listOf(
            EwpGroup(
                name = "root",
                groups = listOf(
                    EwpGroup("alpha", emptyList(), emptyList()),
                    EwpGroup("beta", emptyList(), emptyList()),
                ),
                files = listOf(
                    EwpFileEntry("a.cpp", "/a.cpp"),
                    EwpFileEntry("b.cpp", "/b.cpp"),
                ),
            ),
        )

        val issues = EwpSortValidator.validateGroups(groups)

        assertTrue(issues.isEmpty())
    }

    @Test
    fun detectsGroupsAfterFilesIssue() {
        val issues = EwpSortValidator.validateGroupOrder(groupsAfterFiles = true, groupPath = listOf("mixed"))

        assertEquals(1, issues.size)
        assertEquals(EwpSortIssueType.GROUPS_AFTER_FILES, issues.single().type)
        assertEquals(listOf("mixed"), issues.single().groupPath)
    }
}
