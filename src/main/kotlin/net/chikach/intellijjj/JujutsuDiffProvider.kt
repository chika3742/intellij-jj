package net.chikach.intellijjj

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.changes.TextRevisionNumber
import com.intellij.openapi.vcs.diff.DiffProvider
import com.intellij.openapi.vcs.diff.ItemLatestState
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsUtil
import net.chikach.intellijjj.jujutsu.Revset

class JujutsuDiffProvider(
    private val project: Project,
    private val vcs: JujutsuVcs
) : DiffProvider {
    private val log = Logger.getInstance(JujutsuDiffProvider::class.java)

    override fun getCurrentRevision(file: VirtualFile): VcsRevisionNumber? {
        if (file.isDirectory) return null
        val root = VcsUtil.getVcsRootFor(project, file) ?: return null
        val revision = readWorkingCopyCommitId(root) ?: return null
        return TextRevisionNumber(revision)
    }

    override fun getLastRevision(virtualFile: VirtualFile): ItemLatestState? {
        if (virtualFile.isDirectory) return null
        val revision = getCurrentRevision(virtualFile) ?: return null
        return ItemLatestState(revision, virtualFile.exists(), true)
    }

    override fun getLastRevision(filePath: FilePath): ItemLatestState? {
        if (filePath.isDirectory) return null
        val root = VcsUtil.getVcsRootFor(project, filePath) ?: return null
        val revision = readWorkingCopyCommitId(root) ?: return null
        return ItemLatestState(TextRevisionNumber(revision), filePath.ioFile.exists(), true)
    }

    override fun createFileContent(revisionNumber: VcsRevisionNumber, selectedFile: VirtualFile): ContentRevision? {
        if (selectedFile.isDirectory) return null
        val revision = revisionNumber.asString().trim()
        if (revision.isEmpty()) return null
        val root = VcsUtil.getVcsRootFor(project, selectedFile) ?: return null
        val relativePath = VfsUtilCore.getRelativePath(selectedFile, root) ?: return null
        val filePath = VcsUtil.getFilePath(selectedFile)
        return JujutsuFileContentRevision(root, filePath, relativePath, revision, vcs)
    }

    override fun getLatestCommittedRevision(vcsRoot: VirtualFile): VcsRevisionNumber? {
        val revision = readWorkingCopyCommitId(vcsRoot) ?: return null
        return TextRevisionNumber(revision)
    }

    private fun readWorkingCopyCommitId(root: VirtualFile): String? {
        return try {
            vcs.commandExecutor.logCommand.readFirstNonBlankLine(root, "commit_id", Revset.WORKING_COPY)
        } catch (e: VcsException) {
            log.warn("Failed to read working copy commit id for ${root.path}", e)
            null
        }
    }

    private class JujutsuFileContentRevision(
        private val root: VirtualFile,
        private val filePath: FilePath,
        private val relativePath: String,
        private val revision: String,
        private val vcs: JujutsuVcs
    ) : ContentRevision {
        private val log = Logger.getInstance(JujutsuFileContentRevision::class.java)

        override fun getContent(): String? {
            return try {
                vcs.commandExecutor.executeAndCheck(
                    root,
                    "file",
                    "show",
                    "-r",
                    Revset.commitId(revision).stringify(),
                    "--",
                    relativePath
                )
            } catch (e: Exception) {
                log.warn("Failed to read content for $relativePath at $revision", e)
                null
            }
        }

        override fun getFile(): FilePath = filePath

        override fun getRevisionNumber(): VcsRevisionNumber {
            return TextRevisionNumber(revision)
        }
    }
}