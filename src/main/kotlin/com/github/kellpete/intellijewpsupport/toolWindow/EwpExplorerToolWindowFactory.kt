package com.github.kellpete.intellijewpsupport.toolWindow

import com.github.kellpete.intellijewpsupport.MyBundle
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class EwpExplorerToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = EwpExplorerPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project): Boolean = true

    override fun init(toolWindow: ToolWindow) {
        toolWindow.stripeTitle = MyBundle["ewpExplorer.toolWindowTitle"]
        toolWindow.setIcon(AllIcons.Nodes.Module)
    }
}
