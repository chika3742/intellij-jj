package net.chikach.intellijjj.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.actions.commit.CommonCheckinProjectAction
import net.chikach.intellijjj.JujutsuVcs

/**
 * Shows the standard IntelliJ commit action only when Jujutsu VCS is active.
 */
class JjCommitAction : DumbAwareAction("Commit") {
    private val delegate = CommonCheckinProjectAction()

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        if (!ProjectLevelVcsManager.getInstance(project).checkVcsIsActive(JujutsuVcs.VCS_NAME)) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        delegate.update(e)
    }

    override fun actionPerformed(e: AnActionEvent) {
        delegate.actionPerformed(e)
    }
}
