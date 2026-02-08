package net.chikach.intellijjj

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManagerGate
import com.intellij.openapi.vcs.changes.ChangeProvider
import com.intellij.openapi.vcs.changes.ChangelistBuilder
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.changes.CurrentContentRevision
import com.intellij.openapi.vcs.changes.TextRevisionNumber
import com.intellij.openapi.vcs.changes.VcsDirtyScope
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsUtil
import net.chikach.intellijjj.jujutsu.Revset
import net.chikach.intellijjj.jujutsu.JujutsuDiffParser
import java.nio.file.Paths

class JujutsuChangeProvider(
    private val project: Project,
    private val vcs: JujutsuVcs
) : ChangeProvider {
    
    override fun getChanges(
        dirtyScope: VcsDirtyScope,
        builder: ChangelistBuilder,
        progress: ProgressIndicator,
        addGate: ChangeListManagerGate
    ) {
        val roots = ProjectLevelVcsManager.getInstance(project).getRootsUnderVcs(vcs)
        for (root in roots) {
            val output = vcs.commandExecutor.executeAndCheck(root, "diff", "--summary", "--color=never", "-r", "@")
            output.lineSequence()
                .map { it.trimEnd() }
                .filter { it.isNotEmpty() }
                .mapNotNull { JujutsuDiffParser.parseSummaryLine(it) }
                .mapNotNull { toChange(root, it) }
                .forEach { change ->
                    val matchesScope = listOfNotNull(change.afterRevision?.file, change.beforeRevision?.file)
                        .any { dirtyScope.belongsTo(it) }
                    if (matchesScope) {
                        builder.processChange(change, vcs.keyInstanceMethod)
                    }
                }
        }
    }

    override fun isModifiedDocumentTrackingRequired(): Boolean = true

    private fun toChange(root: VirtualFile, entry: JujutsuDiffParser.DiffSummaryEntry): Change? {
        val beforeRevision = entry.beforePath?.let { path ->
            val beforeFilePath = VcsUtil.getFilePath(Paths.get(root.path, path).toString(), false)
            JujutsuWorkingCopyBaseRevision(root, beforeFilePath, path, vcs, Revset.parentOf(Revset.WORKING_COPY))
        }
        val afterRevision = entry.afterPath?.let { path ->
            val afterFilePath = VcsUtil.getFilePath(Paths.get(root.path, path).toString(), false)
            CurrentContentRevision.create(afterFilePath)
        }
        if (beforeRevision == null && afterRevision == null) return null
        return Change(beforeRevision, afterRevision)
    }

    private class JujutsuWorkingCopyBaseRevision(
        private val root: VirtualFile,
        private val filePath: FilePath,
        private val relativePath: String,
        private val vcs: JujutsuVcs,
        private val revset: Revset
    ) : ContentRevision {
        override fun getContent(): String? {
            return try {
                vcs.commandExecutor.executeAndCheck(root, "file", "show", "-r", revset.stringify(), "--", relativePath)
            } catch (e: Exception) {
                Logger.getInstance(JujutsuWorkingCopyBaseRevision::class.java)
                    .warn("Failed to read base content for $relativePath at $revset", e)
                null
            }
        }

        override fun getFile(): FilePath = filePath

        override fun getRevisionNumber(): VcsRevisionNumber = TextRevisionNumber(revset.stringify())
    }
}
