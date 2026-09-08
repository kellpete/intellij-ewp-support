package com.github.kellpete.intellijewpsupport.ewp.addfiles

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EwpSourceFileExtensionsTest {

    @Test
    fun recognizesCppSourceExtensions() {
        assertTrue(EwpSourceFileExtensions.isSourceFilePath("main.cpp"))
        assertTrue(EwpSourceFileExtensions.isSourceFilePath("header.hpp"))
        assertTrue(EwpSourceFileExtensions.isSourceFilePath("legacy.C"))
    }

    @Test
    fun rejectsNonSourceExtensions() {
        assertFalse(EwpSourceFileExtensions.isSourceFilePath("readme.md"))
        assertFalse(EwpSourceFileExtensions.isSourceFilePath("CMakeLists.txt"))
    }
}
