package net.chikach.intellijjj.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import net.chikach.intellijjj.JujutsuVcs

class JujutsuNewAction : JujutsuAction() {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val vcs = JujutsuVcs.getInstance(project) ?: return
        
        val message = Messages.showInputDialog(
            project,
            "Enter description for the new change (optional):",
            "Create New Change",
            Messages.getQuestionIcon()
        )
        
        // User cancelled
        if (message == null) return
        
        try {
            val root = getProjectRoot(project)
            val result = if (message.isBlank()) {
                vcs.commandExecutor.newChange(root)
            } else {
                vcs.commandExecutor.newChange(root, message)
            }
            showInfo(project, "New Change Created", result)
        } catch (ex: Exception) {
            handleException(project, ex)
        }
    }
}
