package net.chika3742.intellijjj.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import net.chika3742.intellijjj.JujutsuVcs

class JujutsuSquashAction : JujutsuAction() {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val vcs = JujutsuVcs.getInstance(project) ?: return
        
        val confirm = Messages.showYesNoDialog(
            project,
            "This will squash the current change into its parent. Continue?",
            "Squash Change",
            Messages.getQuestionIcon()
        )
        
        if (confirm != Messages.YES) return
        
        try {
            val root = getProjectRoot(project)
            val result = vcs.commandExecutor.squashChange(root)
            showInfo(project, "Change Squashed", result)
        } catch (ex: Exception) {
            handleException(project, ex)
        }
    }
}
