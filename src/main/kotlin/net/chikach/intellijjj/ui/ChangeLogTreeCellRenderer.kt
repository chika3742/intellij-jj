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
                // Display current change indicator
                if (userObject.isCurrent) {
                    append("@ ", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                } else {
                    append("○ ", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }
                
                // Display change ID
                append(userObject.changeId, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                
                // Display description
                if (userObject.description.isNotEmpty()) {
                    append(" ", SimpleTextAttributes.REGULAR_ATTRIBUTES)
                    append(userObject.description, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                } else {
                    append(" (empty)", SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES)
                }
                
                // Display author if available
                if (userObject.author != null) {
                    append(" [", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                    append(userObject.author, SimpleTextAttributes.GRAYED_ATTRIBUTES)
                    append("]", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }
                
                // Display date if available
                if (userObject.date != null) {
                    append(" ", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                    append(userObject.date, SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
                }
            }
            is String -> {
                // Root node or other string nodes
                append(userObject, SimpleTextAttributes.REGULAR_ATTRIBUTES)
            }
        }
    }
}
