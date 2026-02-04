package net.chikach.intellijjj.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import net.chikach.intellijjj.JujutsuVcs

class JujutsuSplitAction : JujutsuAction() {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val vcs = JujutsuVcs.getInstance(project) ?: return
        
        val confirm = Messages.showYesNoDialog(
            project,
            "This will split the current change. Continue?",
            "Split Change",
            Messages.getQuestionIcon()
        )
        
        if (confirm != Messages.YES) return
        
        try {
            val root = getProjectRoot(project)
            val result = vcs.commandExecutor.splitChange(root)
            showInfo(project, "Change Split", result)
        } catch (ex: Exception) {
            handleException(project, ex)
        }
    }
}
