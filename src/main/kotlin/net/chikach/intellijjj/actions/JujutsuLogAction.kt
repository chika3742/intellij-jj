package net.chikach.intellijjj.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import net.chikach.intellijjj.JujutsuVcs
import net.chikach.intellijjj.ui.ChangeLogTreeCellRenderer
import net.chikach.intellijjj.ui.ChangeLogTreeModel
import net.chikach.intellijjj.ui.JujutsuLogParser
import java.awt.Dimension
import javax.swing.JComponent

class JujutsuLogAction : JujutsuAction() {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val vcs = JujutsuVcs.getInstance(project) ?: return
        
        try {
            val root = getProjectRoot(project)
            val logOutput = vcs.commandExecutor.getLog(root)
            
            val dialog = LogDialog(project, logOutput)
            dialog.show()
        } catch (ex: Exception) {
            handleException(project, ex)
        }
    }
    
    private class LogDialog(
        project: com.intellij.openapi.project.Project,
        private val logContent: String
    ) : DialogWrapper(project) {
        
        init {
            title = "Jujutsu Change Log"
            init()
        }
        
        override fun createCenterPanel(): JComponent {
            // Parse the log output
            val changes = JujutsuLogParser.parse(logContent)
            
            // Create tree model
            val treeModel = ChangeLogTreeModel(changes)
            
            // Create tree with custom renderer
            val tree = Tree(treeModel).apply {
                cellRenderer = ChangeLogTreeCellRenderer()
                isRootVisible = false
                showsRootHandles = true
                preferredSize = Dimension(800, 500)
            }
            
            // Expand all nodes by default
            for (i in 0 until tree.rowCount) {
                tree.expandRow(i)
            }
            
            return JBScrollPane(tree)
        }
    }
}
