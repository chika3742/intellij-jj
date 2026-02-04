package net.chikach.intellijjj.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import net.chikach.intellijjj.JujutsuVcs

class JujutsuCreateBookmarkAction : JujutsuAction() {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val vcs = JujutsuVcs.getInstance(project) ?: return
        
        val name = Messages.showInputDialog(
            project,
            "Enter bookmark name:",
            "Create Bookmark",
            Messages.getQuestionIcon()
        )
        
        if (name.isNullOrBlank()) return
        
        try {
            val root = getProjectRoot(project)
            val result = vcs.commandExecutor.createBookmark(root, name)
            showInfo(project, "Bookmark Created", result)
        } catch (ex: Exception) {
            handleException(project, ex)
        }
    }
}
