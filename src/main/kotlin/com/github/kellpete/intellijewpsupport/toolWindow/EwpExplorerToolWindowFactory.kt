package com.github.kellpete.intellijewpsupport.toolWindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class EwpExplorerToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = EwpExplorerPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, null, false)
        content.preferredFocusableComponent = panel.preferredFocusComponent()
        toolWindow.contentManager.addContent(content)
        toolWindow.setTitleActions(panel.createTitleActions())
    }

    override fun shouldBeAvailable(project: Project) = true
}
