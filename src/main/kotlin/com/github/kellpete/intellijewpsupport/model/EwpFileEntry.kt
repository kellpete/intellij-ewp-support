package com.github.kellpete.intellijewpsupport.model

data class EwpFileEntry(
    val rawPath: String,
    val resolvedPath: String?,
) {
    val displayName: String
        get() {
            val path = rawPath.replace('\\', '/')
            val lastSlash = path.lastIndexOf('/')
            return if (lastSlash >= 0) path.substring(lastSlash + 1) else path
        }
}
