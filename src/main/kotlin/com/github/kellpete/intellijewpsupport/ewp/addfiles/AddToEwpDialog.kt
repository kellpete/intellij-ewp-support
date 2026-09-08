package com.github.kellpete.intellijewpsupport.ewp.addfiles

import com.github.kellpete.intellijewpsupport.MyBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JPanel

class AddToEwpDialog(
    private val project: Project,
    private val newFiles: List<VirtualFile>,
    private val ewpFiles: List<VirtualFile>,
    preselectedEwps: Set<VirtualFile>,
) : DialogWrapper(project) {

    private val ewpCheckboxes = ewpFiles.map { ewpFile ->
        ewpFile to JBCheckBox(ewpFile.name, preselectedEwps.contains(ewpFile))
    }

    init {
        title = MyBundle["addToEwp.dialog.title"]
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        val constraints = GridBagConstraints().apply {
            gridx = 0
            gridy = 0
            anchor = GridBagConstraints.WEST
            insets = JBUI.insets(2, 0)
            weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
        }

        panel.add(JBLabel(MyBundle["addToEwp.dialog.filesLabel"]), constraints)
        constraints.gridy++

        val fileSummary = newFiles.joinToString(", ") { it.name }
        panel.add(JBLabel(fileSummary), constraints)
        constraints.gridy++

        panel.add(JBLabel(MyBundle["addToEwp.dialog.ewpLabel"]), constraints.apply {
            insets = JBUI.insets(8, 0, 2, 0)
        })
        constraints.gridy++

        for ((_, checkbox) in ewpCheckboxes) {
            panel.add(checkbox, constraints)
            constraints.gridy++
        }

        val wrapper = JPanel(BorderLayout())
        wrapper.add(panel, BorderLayout.NORTH)
        return wrapper
    }

    override fun doOKAction() {
        val selectedEwps = ewpCheckboxes.filter { (_, checkbox) -> checkbox.isSelected }.map { (ewpFile, _) -> ewpFile }
        EwpAddFilesService.addFilesToEwps(project, newFiles, selectedEwps)
        super.doOKAction()
    }
}
