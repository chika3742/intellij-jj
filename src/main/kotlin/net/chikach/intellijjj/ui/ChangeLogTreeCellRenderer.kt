package net.chikach.intellijjj.ui

import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import javax.swing.JTree

/**
 * Custom tree cell renderer for displaying change information
 */
class ChangeLogTreeCellRenderer : ColoredTreeCellRenderer() {
    
    override fun customizeCellRenderer(
        tree: JTree,
        value: Any?,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean
    ) {
        if (value == null) return
        
        val userObject = (value as? javax.swing.tree.DefaultMutableTreeNode)?.userObject
        
        when (userObject) {
            is ChangeInfo -> {
                // Display change ID
                if (userObject.isCurrent) {
                    append("@ ", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                }
                append(userObject.changeId, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                
                // Display description
                if (userObject.description.isNotEmpty()) {
                    append(" - ", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                    append(userObject.description, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                }
                
                // Display author if available
                if (userObject.author != null) {
                    append(" (", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                    append(userObject.author, SimpleTextAttributes.GRAYED_ATTRIBUTES)
                    append(")", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }
            }
            is String -> {
                // Root node or other string nodes
                append(userObject, SimpleTextAttributes.REGULAR_ATTRIBUTES)
            }
        }
    }
}
