package net.chikach.intellijjj.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.actions.VcsContextFactory
import net.chikach.intellijjj.JujutsuVcs

abstract class JujutsuAction : AnAction() {
    
    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabled = project != null && JujutsuVcs.getInstance(project) != null
    }
    
    protected fun getProjectRoot(project: Project): FilePath {
        // Use guessProjectDir instead of deprecated baseDir
        val projectDir = project.guessProjectDir() 
            ?: throw IllegalStateException("Cannot determine project directory")
        // Use createFilePathOn which directly accepts VirtualFile
        return VcsContextFactory.getInstance().createFilePathOn(projectDir)
    }
    
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
