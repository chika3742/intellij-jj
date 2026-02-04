package net.chikach.intellijjj.ui

/**
 * Parser for jj log output
 */
object JujutsuLogParser {
    
    /**
     * Parse jj log output into a list of ChangeInfo objects
     * 
     * Typical format:
     * @  wtwtpovp  your.email@example.com  2025-01-29 23:46:57  6dae3649
     * │  (empty) description
     * ○  abcdef12  user@email.com  2025-01-28 10:22:11  cd123456
     * │  Some description
     */
    fun parse(logOutput: String): List<ChangeInfo> {
        val changes = mutableListOf<ChangeInfo>()
        val lines = logOutput.lines()
        
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            
            // Check if line starts with a change indicator
            val isCurrent = line.contains("@")
            val isChange = isCurrent || line.contains("○") || line.contains("o")
            
            if (isChange && !line.trim().isEmpty()) {
                // Parse the log line format:
                // symbol  change_id  author  date time  commit_id
                val parts = line.split("\\s+".toRegex()).filter { it.isNotEmpty() }
                
                if (parts.size >= 2) {
                    // Skip graph characters to find actual data
                    var dataIndex = 0
                    while (dataIndex < parts.size && (parts[dataIndex] in setOf("@", "○", "o", "│", "├", "┬", "╮", "╯", "─", "┤", "┴", "┼", "|"))) {
                        dataIndex++
                    }
                    
                    if (dataIndex < parts.size) {
                        val changeId = parts[dataIndex]
                        var author: String? = null
                        var date: String? = null
                        var description = ""
                        
                        // Try to extract author (email format)
                        if (dataIndex + 1 < parts.size && parts[dataIndex + 1].contains("@")) {
                            author = parts[dataIndex + 1]
                        }
                        
                        // Try to extract date (YYYY-MM-DD format)
                        if (dataIndex + 2 < parts.size && parts[dataIndex + 2].matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                            date = parts[dataIndex + 2]
                            // Add time if available
                            if (dataIndex + 3 < parts.size && parts[dataIndex + 3].matches(Regex("\\d{2}:\\d{2}:\\d{2}"))) {
                                date += " ${parts[dataIndex + 3]}"
                            }
                        }
                        
                        // Look for description on the next line
                        if (i + 1 < lines.size) {
                            val nextLine = lines[i + 1].trim()
                            // Description line usually starts with graph characters followed by text
                            if (nextLine.isNotEmpty() && !nextLine.contains(Regex("[○@o]\\s+\\w{8}"))) {
                                // Remove graph characters from description
                                description = nextLine.replace(Regex("^[│├┬╮╯─┤┴┼|\\s]+"), "").trim()
                            }
                        }
                        
                        changes.add(ChangeInfo(
                            changeId = changeId,
                            description = description,
                            author = author,
                            date = date,
                            isCurrent = isCurrent
                        ))
                    }
                }
            }
            
            i++
        }
        
        return changes
    }
}
