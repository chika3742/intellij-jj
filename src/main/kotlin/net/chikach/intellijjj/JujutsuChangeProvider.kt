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
import com.intellij.openapi.options.advanced.AdvancedSettings
import net.chikach.intellijjj.jujutsu.Revset
import net.chikach.intellijjj.jujutsu.JujutsuDiffParser
import net.chikach.intellijjj.git.GitIgnoreScanner
import java.nio.file.Paths

/**
 * Populates IntelliJ local changes by reading `jj diff --summary` for each
 * Jujutsu root and mapping entries into [Change] instances.
 */
class JujutsuChangeProvider(
    private val project: Project,
    private val vcs: JujutsuVcs
) : ChangeProvider {
    private val gitIgnoreScanner = GitIgnoreScanner(project)
    
    override fun getChanges(
        dirtyScope: VcsDirtyScope,
        builder: ChangelistBuilder,
        progress: ProgressIndicator,
        addGate: ChangeListManagerGate
    ) {
        val roots = ProjectLevelVcsManager.getInstance(project).getRootsUnderVcs(vcs)
        for (root in roots) {
            val output = vcs.commandExecutor.diffCommand.getSummary(root, Revset.WORKING_COPY)
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
            processIgnoredFiles(root, dirtyScope, builder)
        }
    }

    override fun isModifiedDocumentTrackingRequired(): Boolean = true

    private fun processIgnoredFiles(
        root: VirtualFile,
        dirtyScope: VcsDirtyScope,
        builder: ChangelistBuilder
    ) {
        if (!AdvancedSettings.getBoolean("vcs.process.ignored")) return
        val ignored = gitIgnoreScanner.listIgnoredPaths(root)
        if (ignored.isEmpty()) return
        ignored.forEach { relativePath ->
            val fullPath = Paths.get(root.path, relativePath).toString()
            val isDirectory = fullPath.endsWith("/") || java.io.File(fullPath).isDirectory
            val filePath = VcsUtil.getFilePath(fullPath, isDirectory)
            if (dirtyScope.belongsTo(filePath)) {
                builder.processIgnoredFile(filePath)
            }
        }
    }

    private fun toChange(root: VirtualFile, entry: JujutsuDiffParser.DiffSummaryEntry): Change? {
        val beforeRevision = entry.beforePath?.let { path ->
            val beforeFilePath = VcsUtil.getFilePath(Paths.get(root.path, path).toString(), false)
            val wcCommit = vcs.commandExecutor.logCommand.getCommits(root, Revset.parentOf(Revset.WORKING_COPY)).first()
            JujutsuWorkingCopyBaseRevision(root, beforeFilePath, path, vcs, wcCommit.commitId)
        }
        val afterRevision = entry.afterPath?.let { path ->
            val afterFilePath = VcsUtil.getFilePath(Paths.get(root.path, path).toString(), false)
            CurrentContentRevision.create(afterFilePath)
        }
        if (beforeRevision == null && afterRevision == null) return null
        return Change(beforeRevision, afterRevision)
    }

    /**
     * Reads the content of the parent commit of the working copy for diffing.
     */
    private class JujutsuWorkingCopyBaseRevision(
        private val root: VirtualFile,
        private val filePath: FilePath,
        private val relativePath: String,
        private val vcs: JujutsuVcs,
        private val commitId: String
    ) : ContentRevision {
        override fun getContent(): String? {
            return try {
                vcs.commandExecutor.fileCommand.getContents(root, relativePath, Revset.commitId(commitId))
            } catch (e: Exception) {
                Logger.getInstance(JujutsuWorkingCopyBaseRevision::class.java)
                    .warn("Failed to read base content for $relativePath at $commitId", e)
                null
            }
        }

        override fun getFile(): FilePath = filePath

        override fun getRevisionNumber(): VcsRevisionNumber = TextRevisionNumber(commitId)
    }
}
