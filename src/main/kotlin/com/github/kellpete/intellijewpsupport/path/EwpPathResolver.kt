package com.github.kellpete.intellijewpsupport.path

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.InvalidPathException
import java.nio.file.Paths

object EwpPathResolver {

    private const val PROJ_DIR_PLACEHOLDER = "\$PROJ_DIR\$"

    fun resolve(ewpFile: VirtualFile, rawPath: String): String? =
        resolve(ewpFile.parent.path, rawPath)

    fun resolve(ewpDirectory: String, rawPath: String): String? {
        if (rawPath.isBlank()) return null

        return try {
            val expanded = rawPath.replace(PROJ_DIR_PLACEHOLDER, ewpDirectory.replace('\\', '/'))
            val normalized = Paths.get(expanded.replace('\\', '/')).normalize().toString().replace('\\', '/')
            FileUtil.toSystemIndependentName(normalized)
        } catch (_: InvalidPathException) {
            null
        } catch (_: RuntimeException) {
            null
        }
    }
}
