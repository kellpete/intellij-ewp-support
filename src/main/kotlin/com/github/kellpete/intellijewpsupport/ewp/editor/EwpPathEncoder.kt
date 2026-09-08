package com.github.kellpete.intellijewpsupport.ewp.editor

import com.intellij.openapi.util.io.FileUtil
import java.nio.file.InvalidPathException
import java.nio.file.Paths

object EwpPathEncoder {

    private const val PROJ_DIR_PLACEHOLDER = "\$PROJ_DIR\$"

    fun encode(ewpDirectory: String, absolutePath: String): String? {
        if (absolutePath.isBlank()) return null

        return try {
            val absolute = Paths.get(FileUtil.toSystemIndependentName(absolutePath)).normalize()
            var current = Paths.get(FileUtil.toSystemIndependentName(ewpDirectory)).normalize()
            var prefix = PROJ_DIR_PLACEHOLDER

            repeat(10) {
                try {
                    val relative = current.relativize(absolute)
                    if (!relative.isAbsolute) {
                        val encoded = relative.toString().replace('/', '\\')
                        return "$prefix\\$encoded"
                    }
                } catch (_: IllegalArgumentException) {
                }

                val parent = current.parent ?: return null
                current = parent
                prefix = "$prefix\\.."
            }
            null
        } catch (_: InvalidPathException) {
            null
        }
    }
}
