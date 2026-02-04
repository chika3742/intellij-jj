package net.chikach.intellijjj.ui

/**
 * Parser for jj log output
 */
object JujutsuLogParser {
    
    /**
     * Parse jj log output into a list of ChangeInfo objects
     */
    fun parse(logOutput: String): List<ChangeInfo> {
        val changes = mutableListOf<ChangeInfo>()
        val lines = logOutput.lines()
        
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            
            // Look for change ID patterns
            // jj log typically shows lines like:
            // @ change_id description
            // or
            // ○ change_id description
            
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty()) {
                i++
                continue
            }
            
            // Check if line starts with a change indicator
            val isCurrent = trimmedLine.startsWith("@")
            val isChange = isCurrent || trimmedLine.startsWith("○") || trimmedLine.startsWith("o")
            
            if (isChange) {
                // Remove the leading indicator and graph characters
                val contentPart = trimmedLine.removePrefix("@")
                    .removePrefix("○")
                    .removePrefix("o")
                    .trim()
                    .split("\\s+".toRegex(), limit = 2)
                
                if (contentPart.isNotEmpty()) {
                    val changeId = contentPart[0]
                    val description = if (contentPart.size > 1) contentPart[1] else ""
                    
                    changes.add(ChangeInfo(
                        changeId = changeId,
                        description = description,
                        isCurrent = isCurrent
                    ))
                }
            }
            
            i++
        }
        
        return changes
    }
}
