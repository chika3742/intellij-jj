package net.chikach.intellijjj.commit

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vcs.changes.ui.CommitMessageProvider
import com.intellij.vcsUtil.VcsUtil
import net.chikach.intellijjj.JujutsuVcs

class JujutsuCommitMessageProvider : CommitMessageProvider {
    private val log = Logger.getInstance(JujutsuCommitMessageProvider::class.java)

    override fun getCommitMessage(forChangelist: LocalChangeList, project: Project): String? {
        val vcs = JujutsuVcs.getInstance(project) ?: return null
        val vcsManager = ProjectLevelVcsManager.getInstance(project)
        if (!vcsManager.checkVcsIsActive(JujutsuVcs.VCS_NAME)) return null

        val change = forChangelist.changes.firstOrNull { isJujutsuChange(vcsManager, it) } ?: return null
        val filePath = change.afterRevision?.file ?: change.beforeRevision?.file ?: return null
        val root = VcsUtil.getVcsRootFor(project, filePath) ?: return null

        return try {
            val output = vcs.commandExecutor.executeAndCheck(root, "log", "--no-graph", "--color=never", "-r", "@", "-T", "description")
            val message = output.trimEnd()
            message.ifBlank { null }
        } catch (e: Exception) {
            log.warn("Failed to read current change description", e)
            null
        }
    }

    private fun isJujutsuChange(vcsManager: ProjectLevelVcsManager, change: Change): Boolean {
        val filePath = change.afterRevision?.file ?: change.beforeRevision?.file ?: return false
        val vcs = vcsManager.getVcsFor(filePath) ?: return false
        return vcs.name == JujutsuVcs.VCS_NAME
    }
}
