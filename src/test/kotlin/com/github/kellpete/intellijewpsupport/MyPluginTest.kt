package com.github.kellpete.intellijewpsupport

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.PsiErrorElementUtil
import com.intellij.util.ThrowableRunnable
import org.jdom.Element

@TestDataPath("\$CONTENT_ROOT/src/test/testData")
class MyPluginTest : BasePlatformTestCase() {

    // CLion 2026.2 bundles a Rider/Radler backend (RD socket-wire protocol, workspace
    // model sync, patch engine, etc.) that keeps running background/async tasks even for
    // simple light platform tests. Those tasks occasionally log errors (AlreadyDisposedException
    // after the light test project is temporarily disposed between tests, "patch.engine.backend
    // .freeze.timeout" warnings, temp-filesystem-to-nio-path mapping failures, ...) on
    // unrelated background threads. None of this originates from this plugin's code, but
    // TestLoggerFactory turns any such logged error into a test failure, causing flakiness.
    // Suppress only errors that don't touch this plugin's own package.
    override fun runBare(testRunnable: ThrowableRunnable<Throwable>) {
        LoggedErrorProcessor.executeWith<Throwable>(object : LoggedErrorProcessor() {
            override fun processError(
                category: String,
                message: String,
                details: Array<String>,
                t: Throwable?
            ): MutableSet<Action> {
                return if (isUnrelatedPlatformBackendNoise(t)) Action.NONE
                else super.processError(category, message, details, t)
            }
        }) {
            super.runBare(testRunnable)
        }
    }

    private fun isUnrelatedPlatformBackendNoise(t: Throwable?): Boolean {
        if (t == null) return false
        val chain = generateSequence(t) { it.cause }.toList()
        val touchesThisPlugin = chain.any { ex ->
            ex.stackTrace.any { frame -> frame.className.startsWith(PLUGIN_PACKAGE) }
        }
        if (touchesThisPlugin) return false

        return chain.any { ex ->
            ex.stackTrace.any { frame ->
                NOISY_BACKEND_PACKAGE_PREFIXES.any { prefix -> frame.className.startsWith(prefix) }
            }
        }
    }

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

    fun testXMLFile() {
        val psiFile = myFixture.configureByText(XmlFileType.INSTANCE, "<foo>bar</foo>")
        val xmlFile = assertInstanceOf(psiFile, XmlFile::class.java)

        assertFalse(PsiErrorElementUtil.hasErrors(project, xmlFile.virtualFile))

        assertNotNull(xmlFile.rootTag)

        xmlFile.rootTag?.let {
            assertEquals("foo", it.name)
            assertEquals("bar", it.value.text)
        }
    }

    fun testRename() {
        myFixture.testRename("foo.xml", "foo_after.xml", "a2")
    }

    override fun getTestDataPath() = "src/test/testData/rename"

    private companion object {
        const val PLUGIN_PACKAGE = "com.github.kellpete.intellijewpsupport"
        val NOISY_BACKEND_PACKAGE_PREFIXES = listOf(
            "com.jetbrains.rd",
            "com.jetbrains.rider",
            "org.jetbrains.plugins.clion",
            "com.intellij.clion",
        )
    }
}
