package com.github.kellpete.intellijewpsupport.ewp.addfiles

import com.github.kellpete.intellijewpsupport.MyBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants

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
        val panel = JPanel(BorderLayout(0, JBUI.scale(8)))

        val header = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            alignmentX = JComponent.LEFT_ALIGNMENT
            add(JBLabel(MyBundle["addToEwp.dialog.filesLabel"]).apply {
                alignmentX = JComponent.LEFT_ALIGNMENT
            })
            add(Box.createVerticalStrut(JBUI.scale(2)))
            add(JBLabel(newFiles.joinToString(", ") { it.name }).apply {
                alignmentX = JComponent.LEFT_ALIGNMENT
            })
            add(Box.createVerticalStrut(JBUI.scale(8)))
            add(JBLabel(MyBundle["addToEwp.dialog.ewpLabel"]).apply {
                alignmentX = JComponent.LEFT_ALIGNMENT
            })
        }
        panel.add(header, BorderLayout.NORTH)

        val checkboxPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(2, 0)
            for ((_, checkbox) in ewpCheckboxes) {
                checkbox.alignmentX = JComponent.LEFT_ALIGNMENT
                checkbox.maximumSize = Dimension(Int.MAX_VALUE, checkbox.preferredSize.height)
                add(checkbox)
            }
        }
        // Keep rows at natural height when the viewport is taller than the list.
        val listWrapper = JPanel(BorderLayout()).apply {
            add(checkboxPanel, BorderLayout.NORTH)
        }

        val rowHeight = ewpCheckboxes.firstOrNull()?.second?.preferredSize?.height
            ?: JBUI.scale(22)
        val visibleRows = minOf(10, maxOf(ewpCheckboxes.size, 1))
        val scrollPane = JBScrollPane(
            listWrapper,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER,
        ).apply {
            border = JBUI.Borders.empty()
            preferredSize = Dimension(
                JBUI.scale(360),
                rowHeight * visibleRows + JBUI.scale(4),
            )
        }
        panel.add(scrollPane, BorderLayout.CENTER)

        return panel
    }

    override fun doOKAction() {
        val selectedEwps = ewpCheckboxes.filter { (_, checkbox) -> checkbox.isSelected }.map { (ewpFile, _) -> ewpFile }
        EwpAddFilesService.addFilesToEwps(project, newFiles, selectedEwps)
        super.doOKAction()
    }
}
