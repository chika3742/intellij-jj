@file:OptIn(ExperimentalTime::class)

package net.chikach.intellijjj.history

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.RepositoryLocation
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.TextRevisionNumber
import com.intellij.openapi.vcs.history.DiffFromHistoryHandler
import com.intellij.openapi.vcs.history.VcsAbstractHistorySession
import com.intellij.openapi.vcs.history.VcsAppendableHistorySessionPartner
import com.intellij.openapi.vcs.history.VcsDependentHistoryComponents
import com.intellij.openapi.vcs.history.VcsFileRevision
import com.intellij.openapi.vcs.history.VcsHistoryProvider
import com.intellij.openapi.vcs.history.VcsHistorySession
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsUtil
import net.chikach.intellijjj.JujutsuVcs
import net.chikach.intellijjj.jujutsu.Pattern
import net.chikach.intellijjj.jujutsu.Revset
import net.chikach.intellijjj.jujutsu.models.JujutsuCommit
import java.nio.charset.StandardCharsets
import java.util.Date
import javax.swing.JComponent
import kotlin.time.ExperimentalTime

/**
 * File history provider backed by `jj log` and `jj file show`.
 */
class JujutsuHistoryProvider(
    private val project: Project,
    private val vcs: JujutsuVcs,
) : VcsHistoryProvider {
    private val log = Logger.getInstance(JujutsuHistoryProvider::class.java)
    private val vcsManager = ProjectLevelVcsManager.getInstance(project)

    override fun getUICustomization(
        session: VcsHistorySession,
        forShortcutNavigation: JComponent,
    ): VcsDependentHistoryComponents {
        return VcsDependentHistoryComponents.createOnlyColumns(emptyArray())
    }

    override fun getAdditionalActions(refreshCallback: Runnable): Array<AnAction> = emptyArray()

    override fun isDateOmittable(): Boolean = false

    override fun getHelpId(): String = ""

    override fun createSessionFor(filePath: FilePath): VcsHistorySession {
        return createHistorySession(filePath)
    }

    override fun reportAppendableHistory(filePath: FilePath, partner: VcsAppendableHistorySessionPartner) {
        try {
            val session = createHistorySession(filePath)
            val emptySession = JujutsuHistorySession(emptyList(), session.currentRevisionNumber)
            partner.reportCreatedEmptySession(emptySession)
            session.revisionList.forEach { partner.acceptRevision(it) }
        } catch (e: VcsException) {
            log.warn("Failed to append history for ${filePath.path}", e)
            partner.reportException(e)
        }
    }

    override fun supportsHistoryForDirectories(): Boolean = false

    override fun getHistoryDiffHandler(): DiffFromHistoryHandler? = null

    override fun canShowHistoryFor(file: VirtualFile): Boolean {
        if (file.isDirectory) return false
        val root = VcsUtil.getVcsRootFor(project, file) ?: return false
        return toRelativePath(root, file.path) != null
    }

    private fun createHistorySession(filePath: FilePath): JujutsuHistorySession {
        if (filePath.isDirectory) {
            throw VcsException("Directory history is not supported")
        }
        val root = vcsManager.getVcsRootFor(filePath)
            ?: throw VcsException("No Jujutsu root found for ${filePath.path}")
        val relativePath = toRelativePath(root, filePath.path)
            ?: throw VcsException("Path is outside the repository root: ${filePath.path}")

        val commits = vcs.commandExecutor.logCommand.getCommits(
            root,
            Revset.files(Pattern.root(relativePath)),
        )
        val revisions = commits.map { commit ->
            JujutsuFileRevision(
                root = root,
                relativePath = relativePath,
                commit = commit,
                vcs = vcs,
            )
        }
        return JujutsuHistorySession(revisions)
    }

    private fun toRelativePath(root: VirtualFile, path: String): String? {
        val rootPath = FileUtil.toSystemIndependentName(root.path)
        val filePath = FileUtil.toSystemIndependentName(path)
        return FileUtil.getRelativePath(rootPath, filePath, '/')
            ?.takeIf { it.isNotBlank() }
    }

    /**
     * History session implementation storing revisions returned by `jj log`.
     */
    private class JujutsuHistorySession(
        revisions: List<VcsFileRevision>,
        private val currentRevision: VcsRevisionNumber = revisions.firstOrNull()?.revisionNumber ?: VcsRevisionNumber.NULL,
    ) : VcsAbstractHistorySession(revisions, currentRevision) {
        override fun calcCurrentRevisionNumber(): VcsRevisionNumber = currentRevision

        override fun copy(): VcsHistorySession {
            return JujutsuHistorySession(ArrayList(revisionList), currentRevision)
        }
    }

    /**
     * File revision backed by `jj file show`.
     */
    private class JujutsuFileRevision(
        private val root: VirtualFile,
        private val relativePath: String,
        private val commit: JujutsuCommit,
        private val vcs: JujutsuVcs,
    ) : VcsFileRevision {
        private val revisionNumber = TextRevisionNumber(commit.commitId, commit.commitId.take(7))

        override fun getRevisionNumber(): VcsRevisionNumber = revisionNumber

        override fun getRevisionDate(): Date = Date(commit.committer.timestamp.toEpochMilliseconds())

        override fun getAuthor(): String = commit.author.safeName

        override fun getCommitMessage(): String = commit.readableDescription

        override fun getBranchName(): String? = commit.bookmarks.firstOrNull()?.name

        override fun getChangedRepositoryPath(): RepositoryLocation? = null

        override fun loadContent(): ByteArray {
            val content = vcs.commandExecutor.fileCommand.getContents(
                root,
                relativePath,
                Revset.commitId(commit.commitId),
            )
            return content.toByteArray(StandardCharsets.UTF_8)
        }

        @Deprecated("Deprecated in Java")
        override fun getContent(): ByteArray = loadContent()
    }
}
