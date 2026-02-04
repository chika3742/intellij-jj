package net.chika3742.intellijjj.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import net.chika3742.intellijjj.JujutsuVcs
import javax.swing.JComponent
import javax.swing.JTextArea

class JujutsuLogAction : JujutsuAction() {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val vcs = JujutsuVcs.getInstance(project) ?: return
        
        try {
            val root = getProjectRoot(project)
            val logOutput = vcs.commandExecutor.getLog(root)
            
            val dialog = LogDialog(project, logOutput)
            dialog.show()
        } catch (ex: Exception) {
            handleException(project, ex)
        }
    }
    
    private class LogDialog(
        project: com.intellij.openapi.project.Project,
        private val logContent: String
    ) : DialogWrapper(project) {
        
        init {
            title = "Jujutsu Change Log"
            init()
        }
        
        override fun createCenterPanel(): JComponent {
            val textArea = JTextArea(logContent).apply {
                isEditable = false
                rows = 30
                columns = 80
            }
            return JBScrollPane(textArea)
        }
    }
}
