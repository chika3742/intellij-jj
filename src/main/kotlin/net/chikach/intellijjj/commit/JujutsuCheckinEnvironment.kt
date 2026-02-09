package net.chikach.intellijjj.commit

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vcs.checkin.CheckinEnvironment
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.AppExecutorUtil
import net.chikach.intellijjj.JujutsuVcs
import net.chikach.intellijjj.repo.JujutsuRepositoryChangeListener

/**
 * Implements IntelliJ commit flow by delegating to `jj commit`.
 *
 * Commits are executed per VCS root. After successful commits, both dirty scope
 * and VCS log refresh events are triggered.
 */
class JujutsuCheckinEnvironment(
    private val project: Project,
    private val vcs: JujutsuVcs
) : CheckinEnvironment {
    private val log = Logger.getInstance(JujutsuCheckinEnvironment::class.java)

    override fun getHelpId(): String? = null

    override fun getCheckinOperationName(): String = "Commit"

    override fun commit(
        changes: MutableList<out Change>,
        commitMessage: String,
        commitContext: CommitContext,
        feedback: MutableSet<in String>
    ): MutableList<VcsException>? {
        val errors = mutableListOf<VcsException>()
        val committedRoots = mutableListOf<VirtualFile>()
        val rootsToChanges = groupChangesByRoot(changes)
        val roots = if (rootsToChanges.isNotEmpty()) {
            rootsToChanges.keys
        } else {
            ProjectLevelVcsManager.getInstance(project).getRootsUnderVcs(vcs).toSet()
        }

        for (root in roots) {
            val fileArgs = rootsToChanges[root]?.let { collectRelativePaths(root, it) }.orEmpty()
            val output = vcs.commandExecutor.commitCommand.commit(root, commitMessage, fileArgs)
            if (output.exitCode != 0) {
                val message = output.stderr.trim().ifEmpty { "jj commit failed" }
                errors.add(VcsException(message))
            } else {
                committedRoots.add(root)
                scheduleRootDirty(root)
            }
        }

        if (errors.isNotEmpty()) {
            log.warn("Commit failed with ${errors.size} error(s)")
            return errors
        }
        if (committedRoots.isNotEmpty()) {
            scheduleLogRefresh(roots)
        }
        return null
    }

    override fun scheduleMissingFileForDeletion(files: MutableList<out FilePath>): MutableList<VcsException>? = null

    override fun scheduleUnversionedFilesForAddition(files: MutableList<out VirtualFile>): MutableList<VcsException>? = null

    override fun isRefreshAfterCommitNeeded(): Boolean = true

    /**
     * Publishes repository change events so log UIs refresh after commit.
     */
    private fun scheduleLogRefresh(roots: Collection<VirtualFile>) {
        ApplicationManager.getApplication().invokeLater {
            roots.forEach { root ->
                project.messageBus.syncPublisher(JujutsuRepositoryChangeListener.TOPIC).repositoryChanged(root)
            }
        }
    }

    private fun scheduleRootDirty(root: VirtualFile) {
        if (project.isDisposed) return
        AppExecutorUtil.getAppExecutorService().execute {
            if (!project.isDisposed) {
                VcsDirtyScopeManager.getInstance(project).rootDirty(root)
            }
        }
    }

    /**
     * Groups selected changes by the VCS root that owns each path.
     */
    private fun groupChangesByRoot(changes: List<Change>): Map<VirtualFile, List<Change>> {
        val vcsManager = ProjectLevelVcsManager.getInstance(project)
        return changes.mapNotNull { change ->
            val filePath = change.afterRevision?.file ?: change.beforeRevision?.file
            val root = filePath?.let { vcsManager.getVcsRootFor(it) }
            if (filePath != null && root != null) {
                root to change
            } else {
                null
            }
        }.groupBy({ it.first }, { it.second })
    }

    /**
     * Converts change paths to root-relative arguments accepted by `jj commit --`.
     */
    private fun collectRelativePaths(root: VirtualFile, changes: List<Change>): List<String> {
        return changes.mapNotNull { change ->
            val filePath = change.afterRevision?.file ?: change.beforeRevision?.file ?: return@mapNotNull null
            val relative = FileUtil.getRelativePath(root.path, filePath.path, '/')
            relative?.takeIf { it.isNotBlank() }
        }.distinct()
    }
}
