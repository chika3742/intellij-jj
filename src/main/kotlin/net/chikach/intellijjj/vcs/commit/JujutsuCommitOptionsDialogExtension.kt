package net.chikach.intellijjj.vcs.commit

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.ChangeListListener
import com.intellij.openapi.vcs.ui.CommitOptionsDialogExtension
import com.intellij.openapi.vcs.ui.RefreshableOnComponent
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import net.chikach.intellijjj.JujutsuVcs
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.SwingUtilities

class JujutsuCommitOptionsDialogExtension : CommitOptionsDialogExtension {
    override fun getOptions(project: Project): Collection<RefreshableOnComponent> {
        val vcsManager = ProjectLevelVcsManager.getInstance(project)
        if (!vcsManager.checkVcsIsActive(JujutsuVcs.VCS_NAME)) return emptyList()
        return listOf(JujutsuCurrentChangeFilesPanel(project))
    }
}

private class JujutsuCurrentChangeFilesPanel(private val project: Project) : RefreshableOnComponent {
    private val listModel = DefaultListModel<String>()
    private val list = JBList(listModel).apply {
        emptyText.text = "Loading Jujutsu change files..."
        visibleRowCount = 6
    }
    private val component = panel {
        group("Jujutsu Current Change Files") {
            row {
                cell(ScrollPaneFactory.createScrollPane(list, true))
                    .align(Align.FILL)
            }.resizableRow()
        }
    }
    private val refreshInProgress = AtomicBoolean(false)
    private val refreshRequested = AtomicBoolean(false)

    init {
        project.messageBus.connect(project).subscribe(ChangeListListener.TOPIC, object : ChangeListListener {
            override fun changeListUpdateDone() {
                scheduleRefresh()
            }
        })
        scheduleRefresh()
    }

    override fun getComponent(): JComponent = component

    override fun saveState() = Unit

    override fun restoreState() {
        scheduleRefresh()
    }

    private fun scheduleRefresh() {
        if (refreshInProgress.getAndSet(true)) {
            refreshRequested.set(true)
            return
        }
        list.emptyText.text = "Loading Jujutsu change files..."
        ApplicationManager.getApplication().executeOnPooledThread {
            val result = loadCurrentChangeFiles()
            SwingUtilities.invokeLater {
                val shouldReschedule = refreshRequested.getAndSet(false)
                if (!project.isDisposed) {
                    updateList(result)
                }
                refreshInProgress.set(false)
                if (shouldReschedule && !project.isDisposed) {
                    scheduleRefresh()
                }
            }
        }
    }

    private fun loadCurrentChangeFiles(): ChangeFilesResult {
        val vcs = JujutsuVcs.getInstance(project)
            ?: return ChangeFilesResult(emptyList(), "Jujutsu VCS is not active")
        val vcsManager = ProjectLevelVcsManager.getInstance(project)
        val roots = vcsManager.getRootsUnderVcs(vcs).toList()
        if (roots.isEmpty()) {
            return ChangeFilesResult(emptyList(), "No Jujutsu roots found")
        }

        val files = mutableListOf<String>()
        var error: String? = null
        val multiRoot = roots.size > 1
        for (root in roots) {
            val prefix = if (multiRoot) "${vcsManager.getShortNameForVcsRoot(root)}: " else ""
            try {
                val output = vcs.commandExecutor.executeAndCheck(root, "diff", "--name-only", "-r", "@")
                output.lineSequence()
                    .map { it.trimEnd() }
                    .filter { it.isNotEmpty() }
                    .mapTo(files) { prefix + it }
            } catch (e: VcsException) {
                if (error == null) {
                    error = e.message?.lineSequence()?.firstOrNull()?.trim().orEmpty()
                }
            }
        }

        return ChangeFilesResult(files, error?.ifEmpty { "Failed to load Jujutsu change files" })
    }

    private fun updateList(result: ChangeFilesResult) {
        listModel.clear()
        result.files.forEach { listModel.addElement(it) }
        list.emptyText.text = when {
            result.files.isNotEmpty() -> ""
            result.error != null -> result.error
            else -> "No files in current change"
        }
    }

    private data class ChangeFilesResult(val files: List<String>, val error: String?)
}
