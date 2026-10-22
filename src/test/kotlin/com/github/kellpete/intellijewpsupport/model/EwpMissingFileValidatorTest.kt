package com.github.kellpete.intellijewpsupport.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class EwpMissingFileValidatorTest {

    @Test
    fun detectsMissingResolvedPath() {
        val groups = listOf(
            EwpGroup(
                name = "src",
                groups = emptyList(),
                files = listOf(
                    EwpFileEntry("missing.cpp", "/definitely/does/not/exist/missing.cpp"),
                ),
            ),
        )

        val issues = EwpMissingFileValidator.findMissingFiles(groups)

        assertEquals(1, issues.size)
        assertEquals(listOf("src"), issues.single().groupPath)
        assertEquals("missing.cpp", issues.single().rawPath)
    }

    @Test
    fun detectsUnresolvablePathAsMissing() {
        val groups = listOf(
            EwpGroup(
                name = "src",
                groups = emptyList(),
                files = listOf(EwpFileEntry("broken", null)),
            ),
        )

        val issues = EwpMissingFileValidator.findMissingFiles(groups)

        assertEquals(1, issues.size)
        assertEquals(null, issues.single().resolvedPath)
    }

    @Test
    fun acceptsExistingFile() {
        val tempFile = Files.createTempFile("ewp-missing-test", ".cpp")
        try {
            val groups = listOf(
                EwpGroup(
                    name = "src",
                    groups = emptyList(),
                    files = listOf(
                        EwpFileEntry(tempFile.fileName.toString(), tempFile.toString()),
                    ),
                ),
            )

            val issues = EwpMissingFileValidator.findMissingFiles(groups)

            assertTrue(issues.isEmpty())
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }

    @Test
    fun findsMissingFilesInNestedGroups() {
        val groups = listOf(
            EwpGroup(
                name = "src",
                groups = listOf(
                    EwpGroup(
                        name = "nested",
                        groups = emptyList(),
                        files = listOf(
                            EwpFileEntry("gone.cpp", "/no/such/gone.cpp"),
                        ),
                    ),
                ),
                files = emptyList(),
            ),
        )

        val issues = EwpMissingFileValidator.findMissingFiles(groups)

        assertEquals(listOf("src", "nested"), issues.single().groupPath)
    }
}
