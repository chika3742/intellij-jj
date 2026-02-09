package net.chikach.intellijjj.rollback

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vcs.rollback.DefaultRollbackEnvironment
import com.intellij.openapi.vcs.rollback.RollbackProgressListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.vcsUtil.VcsUtil
import net.chikach.intellijjj.JujutsuVcs
import net.chikach.intellijjj.repo.JujutsuRepositoryChangeListener

/**
 * Implements IntelliJ rollback by delegating to `jj restore`.
 */
class JujutsuRollbackEnvironment(
    private val project: Project,
    private val vcs: JujutsuVcs,
) : DefaultRollbackEnvironment() {
    private val log = Logger.getInstance(JujutsuRollbackEnvironment::class.java)
    private val vcsManager = ProjectLevelVcsManager.getInstance(project)
    private val dirtyScopeManager = VcsDirtyScopeManager.getInstance(project)

    override fun rollbackChanges(
        changes: List<Change>,
        exceptions: MutableList<VcsException>,
        listener: RollbackProgressListener,
    ) {
        val files = changes.mapNotNull { change ->
            // Deleted changes have no afterRevision, so fall back to beforeRevision.
            change.afterRevision?.file ?: change.beforeRevision?.file
        }
        rollbackItems(files, exceptions, listener)
    }

    override fun rollbackMissingFileDeletion(
        files: List<FilePath>,
        exceptions: MutableList<in VcsException>,
        listener: RollbackProgressListener,
    ) {
        rollbackItems(files, exceptions, listener)
    }

    override fun rollbackModifiedWithoutCheckout(
        files: List<VirtualFile>,
        exceptions: MutableList<in VcsException>,
        listener: RollbackProgressListener,
    ) {
        rollbackItems(files.map { file -> VcsUtil.getFilePath(file) }, exceptions, listener)
    }

    private fun rollbackItems(
        files: List<FilePath>,
        exceptions: MutableList<in VcsException>,
        listener: RollbackProgressListener,
    ) {
        val roots = files.groupBy { vcsManager.getVcsRootFor(it) }
        roots.forEach { (root, files) ->
            listener.checkCanceled()
            if (root == null) {
                // Notify the listener that the files have been consumed and skip processing
                listener.accept(files)
                return@forEach
            }

            val uniquePaths = files.mapNotNull { toRelativePath(root, it.path).let { relativePath ->
                if (relativePath == null) {
                    // Notify the listener that the file has been consumed and remove from processing
                    listener.accept(it)
                    null
                } else relativePath
            } }.distinct()
            if (uniquePaths.isEmpty()) return@forEach
            try {
                vcs.commandExecutor.restoreCommand.restore(root, uniquePaths)
                markRootChanged(root)
            } catch (e: VcsException) {
                log.warn("Failed to rollback ${uniquePaths.size} path(s) in ${root.path}", e)
                exceptions.add(e)
            }
        }
    }

    private fun markRootChanged(root: VirtualFile) {
        if (!project.isDisposed) {
            AppExecutorUtil.getAppExecutorService().execute {
                if (!project.isDisposed) {
                    dirtyScopeManager.rootDirty(root)
                }
            }
        }
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                project.messageBus.syncPublisher(JujutsuRepositoryChangeListener.TOPIC).repositoryChanged(root)
            }
        }
    }

    private fun toRelativePath(root: VirtualFile, path: String): String? {
        val rootPath = FileUtil.toSystemIndependentName(root.path)
        val filePath = FileUtil.toSystemIndependentName(path)
        return FileUtil.getRelativePath(rootPath, filePath, '/')
            ?.takeIf { it.isNotBlank() }
    }
}
