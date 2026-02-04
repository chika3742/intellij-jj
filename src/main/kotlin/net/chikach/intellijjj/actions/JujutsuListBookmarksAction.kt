package net.chikach.intellijjj.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import net.chikach.intellijjj.JujutsuVcs
import javax.swing.JComponent
import javax.swing.JTextArea

class JujutsuListBookmarksAction : JujutsuAction() {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val vcs = JujutsuVcs.getInstance(project) ?: return
        
        try {
            val root = getProjectRoot(project)
            val bookmarks = vcs.commandExecutor.listBookmarks(root)
            
            val dialog = BookmarksDialog(project, bookmarks)
            dialog.show()
        } catch (ex: Exception) {
            handleException(project, ex)
        }
    }
    
    private class BookmarksDialog(
        project: com.intellij.openapi.project.Project,
        private val bookmarksContent: String
    ) : DialogWrapper(project) {
        
        init {
            title = "Jujutsu Bookmarks"
            init()
        }
        
        override fun createCenterPanel(): JComponent {
            val textArea = JTextArea(bookmarksContent).apply {
                isEditable = false
                rows = 20
                columns = 60
            }
            return JBScrollPane(textArea)
        }
    }
}
