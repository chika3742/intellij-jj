@file:Suppress("UnstableApiUsage")

package net.chikach.intellijjj.log

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import com.intellij.vcs.log.VcsCommitStyleFactory
import com.intellij.vcs.log.VcsLogDataPack
import com.intellij.vcs.log.VcsLogHighlighter
import com.intellij.vcs.log.VcsLogHighlighter.VcsCommitStyle
import com.intellij.vcs.log.VcsLogHighlighter.TextStyle
import com.intellij.vcs.log.VcsLogUi
import com.intellij.vcs.log.VcsShortCommitDetails
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.ui.VcsLogUiEx
import com.intellij.vcs.log.ui.highlighters.VcsLogHighlighterFactory
import com.intellij.vcs.log.ui.table.VcsLogGraphTable
import net.chikach.intellijjj.JujutsuVcsUtil
import net.chikach.intellijjj.jujutsu.JujutsuCommandExecutor
import java.awt.Color
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Highlights the commit that matches the current working-copy commit (`@`) per
 * repository root in the VCS Log table.
 */
class JujutsuCurrentChangeHighlighter(
    private val logData: VcsLogData,
    private val logUi: VcsLogUi
) : VcsLogHighlighter {
    private val log = Logger.getInstance(JujutsuCurrentChangeHighlighter::class.java)
    private val commandExecutor: JujutsuCommandExecutor
        get() = JujutsuVcsUtil.getCommandExecutor(logData.project)
    private val showCommand
        get() = commandExecutor.showCommand
    private val currentCommitByRoot = ConcurrentHashMap<VirtualFile, String>()
    private val updateCounter = AtomicInteger(0)

    override fun getStyle(
        commitId: Int,
        commitDetails: VcsShortCommitDetails,
        column: Int,
        isSelected: Boolean
    ): VcsCommitStyle {
        if (!isSubjectColumn(column)) return VcsCommitStyle.DEFAULT
        val currentCommit = currentCommitByRoot[commitDetails.root] ?: return VcsCommitStyle.DEFAULT
        val commitHash = commitDetails.id.asString()
        return if (commitHash.equals(currentCommit, ignoreCase = true)) {
            if (isSelected) {
                VcsCommitStyleFactory.bold()
            } else {
                VcsCommitStyleFactory.createStyle(CURRENT_CHANGE_FOREGROUND, null, TextStyle.BOLD)
            }
        } else {
            VcsCommitStyle.DEFAULT
        }
    }

    override fun update(dataPack: VcsLogDataPack, refreshHappened: Boolean) {
        val roots = dataPack.logProviders
            .filterValues { it.supportedVcs == JujutsuVcsUtil.getKey() }
            .keys
        if (roots.isEmpty()) {
            currentCommitByRoot.clear()
            return
        }
        val updateId = updateCounter.incrementAndGet()
        ApplicationManager.getApplication().executeOnPooledThread {
            val next = HashMap<VirtualFile, String>()
            for (root in roots) {
                readWorkingCopyCommitId(root)?.let { next[root] = it }
            }
            ApplicationManager.getApplication().invokeLater {
                if (updateCounter.get() != updateId) return@invokeLater
                currentCommitByRoot.clear()
                currentCommitByRoot.putAll(next)
                val table = (logUi as? VcsLogUiEx)?.table as? VcsLogGraphTable
                table?.repaint()
            }
        }
    }

    private fun isSubjectColumn(columnModelIndex: Int): Boolean {
        val table = (logUi as? VcsLogUiEx)?.table as? VcsLogGraphTable ?: return false
        return table.commitColumn.modelIndex == columnModelIndex
    }

    private fun readWorkingCopyCommitId(root: VirtualFile): String? {
        return try {
            showCommand.getWorkingCopyCommitId(root)
        } catch (e: Exception) {
            log.warn("Failed to read working copy commit id for ${root.path}", e)
            null
        }
    }

    companion object {
        private val CURRENT_CHANGE_FOREGROUND = JBColor(
            Color.decode("#00ae3a"),
            Color.decode("#66e991"),
        )
    }

    /**
     * Registers the highlighter in the VCS Log highlighter extension point.
     */
    class Factory : VcsLogHighlighterFactory {
        override fun createHighlighter(logData: VcsLogData, logUi: VcsLogUi): VcsLogHighlighter {
            return JujutsuCurrentChangeHighlighter(logData, logUi)
        }

        override fun getId(): String = "JUJUTSU_CURRENT_CHANGE"

        override fun getTitle(): String = "Highlight current change"

        override fun showMenuItem(): Boolean = false
    }
}
