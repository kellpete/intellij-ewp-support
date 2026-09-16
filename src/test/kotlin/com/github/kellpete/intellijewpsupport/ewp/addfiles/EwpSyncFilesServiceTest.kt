package com.github.kellpete.intellijewpsupport.ewp.addfiles

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jdom.Element

class EwpSyncFilesServiceTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        snapshotCodeInsightSettings()
    }

    private fun snapshotCodeInsightSettings() {
        val current = CodeInsightSettings.getInstance()
        val snapshot = CodeInsightSettings()
        val element = Element("temp")
        current.writeExternal(element)
        snapshot.loadState(element)
        setDefaultCodeInsightSettings(snapshot)
    }

    fun testRemoveDeletesListedPathFromEwp() {
        myFixture.addFileToProject("src/main.cpp", "int main() { return 0; }\n")
        myFixture.addFileToProject("src/other.cpp", "void other() {}\n")
        val ewp = myFixture.addFileToProject(
            "firmware/project.ewp",
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
                <group>
                    <name>src</name>
                    <file>
                        <name>${'$'}PROJ_DIR${'$'}\..\src\main.cpp</name>
                    </file>
                    <file>
                        <name>${'$'}PROJ_DIR${'$'}\..\src\other.cpp</name>
                    </file>
                </group>
            </project>
            """.trimIndent(),
        ).virtualFile

        val mainPath = FileUtil.toSystemIndependentName(
            myFixture.tempDirFixture.getFile("src/main.cpp")!!.path,
        )

        EwpSyncFilesService.removePathsFromAllEwps(project, listOf(mainPath))

        val text = String(ewp.contentsToByteArray())
        assertFalse(text.contains("main.cpp"))
        assertTrue(text.contains("other.cpp"))
    }

    fun testRemoveDeletesCppAndHppTogether() {
        myFixture.addFileToProject("src/Foo.cpp", "void foo() {}\n")
        myFixture.addFileToProject("src/Foo.hpp", "void foo();\n")
        val ewp = myFixture.addFileToProject(
            "firmware/project.ewp",
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
                <group>
                    <name>src</name>
                    <file>
                        <name>${'$'}PROJ_DIR${'$'}\..\src\Foo.cpp</name>
                    </file>
                    <file>
                        <name>${'$'}PROJ_DIR${'$'}\..\src\Foo.hpp</name>
                    </file>
                </group>
            </project>
            """.trimIndent(),
        ).virtualFile

        val cppPath = FileUtil.toSystemIndependentName(
            myFixture.tempDirFixture.getFile("src/Foo.cpp")!!.path,
        )
        val hppPath = FileUtil.toSystemIndependentName(
            myFixture.tempDirFixture.getFile("src/Foo.hpp")!!.path,
        )

        EwpSyncFilesService.removePathsFromAllEwps(project, listOf(cppPath, hppPath))

        val text = String(ewp.contentsToByteArray())
        assertFalse(text.contains("Foo.cpp"))
        assertFalse(text.contains("Foo.hpp"))
    }

    fun testMoveRemapsToNewGroup() {
        myFixture.addFileToProject("src/main.cpp", "int main() { return 0; }\n")
        val ewp = myFixture.addFileToProject(
            "firmware/project.ewp",
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
                <group>
                    <name>src</name>
                    <file>
                        <name>${'$'}PROJ_DIR${'$'}\..\src\main.cpp</name>
                    </file>
                </group>
            </project>
            """.trimIndent(),
        ).virtualFile

        val oldPath = FileUtil.toSystemIndependentName(
            myFixture.tempDirFixture.getFile("src/main.cpp")!!.path,
        )
        val moved = myFixture.addFileToProject("src/tools/main.cpp", "int main() { return 0; }\n")
        val newPath = FileUtil.toSystemIndependentName(moved.virtualFile.path)

        EwpSyncFilesService.movePathsInAllEwps(
            project,
            listOf(EwpSyncFilesService.PathMove(oldPath, newPath)),
        )

        val text = String(ewp.contentsToByteArray())
        assertTrue(text.contains("tools"))
        assertTrue(text.contains("main.cpp"))
        assertFalse(text.contains("${'$'}PROJ_DIR${'$'}\\..\\src\\main.cpp"))
    }
}
