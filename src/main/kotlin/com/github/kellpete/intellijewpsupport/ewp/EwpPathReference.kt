package com.github.kellpete.intellijewpsupport.ewp

import com.github.kellpete.intellijewpsupport.path.EwpPathResolver
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase

class EwpPathReference(
    file: EwpPsiFile,
    range: TextRange,
    private val rawPath: String,
) : PsiReferenceBase<EwpPsiFile>(file, range, /* soft = */ true) {

    override fun resolve(): PsiElement? {
        val ewpVirtualFile = element.virtualFile ?: return null
        val ewpDirectory = ewpVirtualFile.parent ?: return null

        val targetFile = resolveViaRelativePath(ewpDirectory)
            ?: resolveViaAbsolutePath(ewpVirtualFile)
            ?: return null

        return PsiManager.getInstance(element.project).findFile(targetFile)
    }

    private fun resolveViaRelativePath(ewpDirectory: VirtualFile): VirtualFile? =
        ewpDirectory.findFileByRelativePath(relativePathFromProjDir())

    private fun resolveViaAbsolutePath(ewpVirtualFile: VirtualFile): VirtualFile? =
        EwpPathResolver.resolve(ewpVirtualFile, rawPath)?.let { absolutePath ->
            LocalFileSystem.getInstance().findFileByPath(absolutePath)
                ?: LocalFileSystem.getInstance().refreshAndFindFileByPath(absolutePath)
        }

    private fun relativePathFromProjDir(): String {
        val withoutPlaceholder = if (rawPath.startsWith(PROJ_DIR)) {
            rawPath.removePrefix(PROJ_DIR)
        } else {
            rawPath
        }
        return withoutPlaceholder.replace('\\', '/').trimStart('/')
    }

    override fun getVariants(): Array<Any> = emptyArray()

    companion object {
        private const val PROJ_DIR = "\$PROJ_DIR\$"
    }
}

object EwpPathReferences {

    private const val NAME_OPEN = "<name>"
    private const val NAME_CLOSE = "</name>"
    private const val PROJ_DIR = "\$PROJ_DIR\$"

    fun collect(file: EwpPsiFile): Array<PsiReference> {
        val text = file.text
        val references = mutableListOf<PsiReference>()
        var searchFrom = 0

        while (true) {
            val markerStart = text.indexOf(PROJ_DIR, searchFrom)
            if (markerStart < 0) break

            val nameOpen = text.lastIndexOf(NAME_OPEN, markerStart)
            val nameClose = text.indexOf(NAME_CLOSE, markerStart)
            if (nameOpen < 0 || nameClose < 0 || nameOpen > markerStart) {
                searchFrom = markerStart + PROJ_DIR.length
                continue
            }

            val contentStart = nameOpen + NAME_OPEN.length
            var pathStart = contentStart
            while (pathStart < nameClose && text[pathStart].isWhitespace()) {
                pathStart++
            }
            var pathEnd = nameClose
            while (pathEnd > pathStart && text[pathEnd - 1].isWhitespace()) {
                pathEnd--
            }

            if (pathStart < pathEnd && text.startsWith(PROJ_DIR, pathStart)) {
                val rawPath = text.substring(pathStart, pathEnd)
                references += EwpPathReference(file, TextRange(pathStart, pathEnd), rawPath)
            }

            searchFrom = nameClose + NAME_CLOSE.length
        }

        return references.toTypedArray()
    }
}
