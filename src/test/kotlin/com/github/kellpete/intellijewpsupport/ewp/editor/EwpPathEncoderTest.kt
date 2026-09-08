package com.github.kellpete.intellijewpsupport.ewp.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EwpPathEncoderTest {

    @Test
    fun encodesRelativePathWithProjDirPlaceholder() {
        val encoded = EwpPathEncoder.encode("/project", "/src/main.cpp")

        assertEquals("\$PROJ_DIR\$\\..\\src\\main.cpp", encoded)
    }

    @Test
    fun encodesNestedRelativePath() {
        val encoded = EwpPathEncoder.encode(
            "/project/firmware",
            "/project/config/ra6m5/option_setting_memory_init.h",
        )

        assertEquals(
            "\$PROJ_DIR\$\\..\\config\\ra6m5\\option_setting_memory_init.h",
            encoded,
        )
    }

    @Test
    fun encodesFileInsideEwpDirectory() {
        val encoded = EwpPathEncoder.encode("/project/firmware", "/project/firmware/config.h")

        assertEquals("\$PROJ_DIR\$\\config.h", encoded)
    }

    @Test
    fun returnsNullForBlankPath() {
        assertNull(EwpPathEncoder.encode("/project", "   "))
    }
}
