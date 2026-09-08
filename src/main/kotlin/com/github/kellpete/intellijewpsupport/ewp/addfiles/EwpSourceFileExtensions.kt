package com.github.kellpete.intellijewpsupport.ewp.addfiles

import com.intellij.openapi.vfs.VirtualFile

object EwpSourceFileExtensions {

    private val EXTENSIONS = setOf(
        "c", "cpp", "cc", "cxx", "h", "hpp", "hh", "hxx", "C",
    )

    fun isSourceFile(file: VirtualFile): Boolean {
        val extension = file.extension ?: return false
        return EXTENSIONS.contains(extension)
    }

    fun isSourceFilePath(path: String): Boolean {
        val extension = path.substringAfterLast('.', "").substringBefore('?')
        return EXTENSIONS.contains(extension)
    }
}
