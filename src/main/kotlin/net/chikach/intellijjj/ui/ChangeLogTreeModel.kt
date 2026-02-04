package net.chikach.intellijjj.ui

import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * Data class representing a single change in the jj log
 */
data class ChangeInfo(
    val changeId: String,
    val description: String,
    val author: String? = null,
    val date: String? = null,
    val isCurrent: Boolean = false
)

/**
 * Tree model for displaying jj log as a hierarchical tree
 */
class ChangeLogTreeModel(changes: List<ChangeInfo>) : DefaultTreeModel(DefaultMutableTreeNode("Changes")) {
    
    init {
        buildTree(changes)
    }
    
    private fun buildTree(changes: List<ChangeInfo>) {
        val rootNode = root as DefaultMutableTreeNode
        
        // For now, create a simple flat list
        // In the future, this could be enhanced to parse parent-child relationships
        changes.forEach { change ->
            val changeNode = DefaultMutableTreeNode(change)
            rootNode.add(changeNode)
        }
    }
}
