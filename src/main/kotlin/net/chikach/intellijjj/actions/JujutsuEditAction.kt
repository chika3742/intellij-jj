package net.chika3742.intellijjj.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import net.chika3742.intellijjj.JujutsuVcs

class JujutsuEditAction : JujutsuAction() {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val vcs = JujutsuVcs.getInstance(project) ?: return
        
        val changeId = Messages.showInputDialog(
            project,
            "Enter the change ID to edit:",
            "Edit Change",
            Messages.getQuestionIcon()
        )
        
        if (changeId.isNullOrBlank()) return
        
        try {
            val root = getProjectRoot(project)
            val result = vcs.commandExecutor.editChange(root, changeId)
            showInfo(project, "Change Edited", result)
        } catch (ex: Exception) {
            handleException(project, ex)
        }
    }
}
