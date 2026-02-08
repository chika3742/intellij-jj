@file:Suppress("UnstableApiUsage")

package net.chikach.intellijjj.commit

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListListener
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vcs.changes.ui.CommitMessageProvider
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.vcs.commit.CommitMessageUi
import com.intellij.vcs.commit.DelayedCommitMessageProvider
import com.intellij.vcsUtil.VcsUtil
import net.chikach.intellijjj.JujutsuVcs
import net.chikach.intellijjj.JujutsuVcsUtil
import net.chikach.intellijjj.jujutsu.Revset
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class JujutsuCommitMessageProvider : CommitMessageProvider, DelayedCommitMessageProvider {
    private val log = Logger.getInstance(JujutsuCommitMessageProvider::class.java)

    override fun getCommitMessage(forChangelist: LocalChangeList, project: Project): String? {
        return readCurrentDescription(project, forChangelist)
    }

    override fun init(project: Project, commitUi: CommitMessageUi, disposable: Disposable) {
        val lastAutoMessage = AtomicReference<String?>()
        val refreshInProgress = AtomicBoolean(false)
        val refreshRequested = AtomicBoolean(false)

        fun scheduleRefresh() {
            if (refreshInProgress.getAndSet(true)) {
                refreshRequested.set(true)
                return
            }
            ApplicationManager.getApplication().executeOnPooledThread {
                val message = readCurrentDescription(project, null)
                ApplicationManager.getApplication().invokeLater {
                    val shouldReschedule = refreshRequested.getAndSet(false)
                    if (!project.isDisposed) {
                        updateCommitMessage(commitUi, lastAutoMessage, message)
                    }
                    refreshInProgress.set(false)
                    if (shouldReschedule && !project.isDisposed) {
                        scheduleRefresh()
                    }
                }
            }
        }

        scheduleRefresh()
        project.messageBus.connect(disposable).subscribe(ChangeListListener.TOPIC, object : ChangeListListener {
            override fun changeListUpdateDone() {
                scheduleRefresh()
            }
        })
        ApplicationManager.getApplication().messageBus.connect(disposable)
            .subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    if (events.any { isJujutsuMetadataChange(it.path, project) }) {
                        scheduleRefresh()
                    }
                }
            })
    }

    private fun isJujutsuChange(vcsManager: ProjectLevelVcsManager, change: Change): Boolean {
        val filePath = change.afterRevision?.file ?: change.beforeRevision?.file ?: return false
        val vcs = vcsManager.getVcsFor(filePath) ?: return false
        return vcs.name == JujutsuVcs.VCS_NAME
    }

    private fun readCurrentDescription(project: Project, changeList: LocalChangeList?): String? {
        val vcs = JujutsuVcsUtil.getInstance(project) ?: return null
        val vcsManager = ProjectLevelVcsManager.getInstance(project)
        if (!vcsManager.checkVcsIsActive(JujutsuVcs.VCS_NAME)) return null

        val root = findRootForDescription(project, vcsManager, changeList) ?: return null
        return try {
            val output = vcs.commandExecutor.logCommand.executeWithTemplate(
                root,
                "description",
                revset = Revset.WORKING_COPY,
            )
            val message = output.trimEnd()
            message.ifBlank { null }
        } catch (e: Exception) {
            log.warn("Failed to read current change description", e)
            null
        }
    }

    private fun findRootForDescription(
        project: Project,
        vcsManager: ProjectLevelVcsManager,
        changeList: LocalChangeList?
    ): com.intellij.openapi.vfs.VirtualFile? {
        val changes = changeList?.changes ?: ChangeListManager.getInstance(project).defaultChangeList.changes
        val change = changes.firstOrNull { isJujutsuChange(vcsManager, it) }
        val filePath = change?.afterRevision?.file ?: change?.beforeRevision?.file
        if (filePath != null) {
            return VcsUtil.getVcsRootFor(project, filePath)
        }
        return vcsManager.getRootsUnderVcs(JujutsuVcsUtil.getInstance(project)!!).firstOrNull()
    }

    private fun updateCommitMessage(
        commitUi: CommitMessageUi,
        lastAutoMessage: AtomicReference<String?>,
        newMessage: String?
    ) {
        if (newMessage.isNullOrBlank()) return
        val current = commitUi.text
        val last = lastAutoMessage.get()
        val currentNormalized = current.trim()
        val lastNormalized = last?.trim()
        val newNormalized = newMessage.trim()

        if (lastNormalized == null) {
            if (currentNormalized.isEmpty()) {
                commitUi.text = newMessage
                lastAutoMessage.set(newMessage)
                return
            }
            if (currentNormalized == newNormalized) {
                lastAutoMessage.set(newMessage)
                return
            }
        }

        val shouldUpdate = currentNormalized.isEmpty() || (lastNormalized != null && currentNormalized == lastNormalized)
        if (shouldUpdate && currentNormalized != newNormalized) {
            commitUi.text = newMessage
            lastAutoMessage.set(newMessage)
        }
    }

    private fun isJujutsuMetadataChange(path: String, project: Project): Boolean {
        val vcs = JujutsuVcsUtil.getInstance(project) ?: return false
        val roots = ProjectLevelVcsManager.getInstance(project).getRootsUnderVcs(vcs)
        if (roots.isEmpty()) return false
        val normalizedPath = FileUtil.toSystemIndependentName(path)
        return roots.any { root ->
            val rootPath = FileUtil.toSystemIndependentName(root.path)
            normalizedPath == "$rootPath/.jj" || normalizedPath.startsWith("$rootPath/.jj/")
        }
    }
}
