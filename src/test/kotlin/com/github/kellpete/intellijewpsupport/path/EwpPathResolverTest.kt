package com.github.kellpete.intellijewpsupport.path

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EwpPathResolverTest {

    @Test
    fun resolvesProjDirPlaceholder() {
        val resolved = EwpPathResolver.resolve("/project", "\$PROJ_DIR\$\\..\\src\\main.cpp")

        assertEquals("/src/main.cpp", resolved)
    }

    @Test
    fun resolvesNestedRelativePath() {
        val resolved = EwpPathResolver.resolve(
            "/project/firmware",
            "\$PROJ_DIR\$\\..\\config\\ra6m5\\option_setting_memory_init.h",
        )

        assertEquals("/project/config/ra6m5/option_setting_memory_init.h", resolved)
    }

    @Test
    fun normalizesBackslashes() {
        val resolved = EwpPathResolver.resolve("/project", "\$PROJ_DIR\$\\src\\main.cpp")

        assertEquals("/project/src/main.cpp", resolved)
    }

    @Test
    fun returnsNullForBlankPath() {
        assertNull(EwpPathResolver.resolve("/project", "   "))
    }
}
