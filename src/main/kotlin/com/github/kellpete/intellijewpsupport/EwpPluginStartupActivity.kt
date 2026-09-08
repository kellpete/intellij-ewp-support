package com.github.kellpete.intellijewpsupport

import com.github.kellpete.intellijewpsupport.ewp.addfiles.EwpNewSourceFileListener
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class EwpPluginStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        project.service<EwpNewSourceFileListener>()
    }
}
