package net.chikach.intellijjj.vcs

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeProvider
import com.intellij.openapi.vcs.changes.ChangelistBuilder
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.changes.CurrentContentRevision
import com.intellij.openapi.vcs.changes.TextRevisionNumber
import com.intellij.openapi.vcs.changes.VcsDirtyScope
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsUtil
import net.chikach.intellijjj.JujutsuVcs
import java.nio.file.Paths

class JujutsuChangeProvider(
    private val project: Project,
    private val vcs: JujutsuVcs
) : ChangeProvider {
    
    override fun getChanges(
        dirtyScope: VcsDirtyScope,
        builder: ChangelistBuilder,
        progress: com.intellij.openapi.progress.ProgressIndicator,
        addGate: com.intellij.openapi.vcs.changes.ChangeListManagerGate
    ) {
        val roots = ProjectLevelVcsManager.getInstance(project).getRootsUnderVcs(vcs)
        for (root in roots) {
            val output = vcs.commandExecutor.executeAndCheck(root, "diff", "--summary", "--color=never", "-r", "@")
            output.lineSequence()
                .map { it.trimEnd() }
                .filter { it.isNotEmpty() }
                .mapNotNull { parseSummaryLine(it) }
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

    private fun parseSummaryLine(line: String): DiffSummaryEntry? {
        if (line.length < 3 || line[1] != ' ') return null
        val status = line[0]
        val pathPart = line.substring(2)
        return when (status) {
            'A' -> DiffSummaryEntry(status, null, pathPart)
            'D' -> DiffSummaryEntry(status, pathPart, null)
            'M' -> DiffSummaryEntry(status, pathPart, pathPart)
            'R' -> {
                val (beforePath, afterPath) = parseRenamePath(pathPart)
                DiffSummaryEntry(status, beforePath, afterPath)
            }
            'C' -> {
                val (_, afterPath) = parseRenamePath(pathPart)
                DiffSummaryEntry(status, null, afterPath)
            }
            else -> DiffSummaryEntry(status, pathPart, pathPart)
        }
    }

    private fun parseRenamePath(pathPart: String): Pair<String, String> {
        val openBrace = pathPart.indexOf('{')
        val closeBrace = pathPart.indexOf('}', startIndex = openBrace + 1)
        if (openBrace == -1 || closeBrace == -1) {
            return pathPart to pathPart
        }
        val prefix = pathPart.substring(0, openBrace)
        val suffix = pathPart.substring(closeBrace + 1)
        val inside = pathPart.substring(openBrace + 1, closeBrace)
        val arrowIndex = inside.indexOf(" => ")
        val (before, after) = if (arrowIndex != -1) {
            inside.substring(0, arrowIndex) to inside.substring(arrowIndex + 4)
        } else {
            val fallbackIndex = inside.indexOf("=>")
            if (fallbackIndex != -1) {
                inside.substring(0, fallbackIndex).trim() to inside.substring(fallbackIndex + 2).trim()
            } else {
                inside to inside
            }
        }
        return (prefix + before + suffix) to (prefix + after + suffix)
    }

    private fun toChange(root: VirtualFile, entry: DiffSummaryEntry): Change? {
        val beforeRevision = entry.beforePath?.let { path ->
            val beforeFilePath = VcsUtil.getFilePath(Paths.get(root.path, path).toString(), false)
            JujutsuWorkingCopyBaseRevision(root, beforeFilePath, path, vcs, PARENT_REVSET)
        }
        val afterRevision = entry.afterPath?.let { path ->
            val afterFilePath = VcsUtil.getFilePath(Paths.get(root.path, path).toString(), false)
            CurrentContentRevision.create(afterFilePath)
        }
        if (beforeRevision == null && afterRevision == null) return null
        return Change(beforeRevision, afterRevision)
    }

    private data class DiffSummaryEntry(
        val status: Char,
        val beforePath: String?,
        val afterPath: String?
    )

    private class JujutsuWorkingCopyBaseRevision(
        private val root: VirtualFile,
        private val filePath: FilePath,
        private val relativePath: String,
        private val vcs: JujutsuVcs,
        private val revset: String
    ) : ContentRevision {
        override fun getContent(): String? {
            return try {
                vcs.commandExecutor.executeAndCheck(root, "file", "show", "-r", revset, "--", relativePath)
            } catch (e: Exception) {
                Logger.getInstance(JujutsuWorkingCopyBaseRevision::class.java)
                    .warn("Failed to read base content for $relativePath at $revset", e)
                null
            }
        }

        override fun getFile(): FilePath = filePath

        override fun getRevisionNumber(): VcsRevisionNumber = TextRevisionNumber(revset)
    }

    companion object {
        private const val PARENT_REVSET = "@-"
    }
}
