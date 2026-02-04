package net.chika3742.intellijjj.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.VcsException
import net.chika3742.intellijjj.JujutsuVcs

abstract class JujutsuAction : AnAction() {
    
    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabled = project != null && JujutsuVcs.getInstance(project) != null
    }
    
    protected fun getProjectRoot(project: Project) = project.baseDir
    
    protected fun showError(project: Project, title: String, message: String) {
        Messages.showErrorDialog(project, message, title)
    }
    
    protected fun showInfo(project: Project, title: String, message: String) {
        Messages.showInfoMessage(project, message, title)
    }
    
    protected fun handleException(project: Project, e: Exception) {
        val message = when (e) {
            is VcsException -> e.message ?: "Unknown VCS error"
            else -> e.message ?: "Unknown error"
        }
        showError(project, "Jujutsu Error", message)
    }
}
