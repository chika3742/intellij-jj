package net.chikach.intellijjj.jujutsu

/**
 * Parses `jj diff --summary` output into structured entries used by change and log providers.
 */
object JujutsuDiffParser {
    /**
     * Normalized representation of a single summary line.
     *
     * @property status single-letter status from summary output (A, D, M, R, C)
     * @property beforePath path before the change, null for additions/copies
     * @property afterPath path after the change, null for deletions
     */
    data class DiffSummaryEntry(
        val status: Char,
        val beforePath: String?,
        val afterPath: String?
    )

    /**
     * Parses one summary line into a [DiffSummaryEntry].
     *
     * @return parsed entry, or null when the line is not a summary entry.
     * @throws IllegalArgumentException when the status token is unknown.
     */
    fun parseSummaryLine(line: String): DiffSummaryEntry? {
        val trimmed = line.trim()
        if (trimmed.length < 3 || trimmed[1] != ' ') return null
        val status = trimmed[0]
        val pathPart = trimmed.substring(2)
        return when (status) {
            'A' -> DiffSummaryEntry(status, null, pathPart)
            'D' -> DiffSummaryEntry(status, pathPart, null)
            'M' -> DiffSummaryEntry(status, pathPart, pathPart)
            'R' -> {
                val (beforePath, afterPath) = parseRenamePath(pathPart)
                DiffSummaryEntry(status, beforePath, afterPath)
            }
            'C' -> {
                val (_, afterPath) = parseRenamePath(pathPart)
                DiffSummaryEntry(status, null, afterPath)
            }
            else -> throw IllegalArgumentException("Unexpected diff status: $status")
        }
    }

    private fun parseRenamePath(pathPart: String): Pair<String, String> {
        val openBrace = pathPart.indexOf('{')
        val closeBrace = if (openBrace != -1) pathPart.indexOf('}', startIndex = openBrace + 1) else -1
        if (openBrace == -1 || closeBrace == -1) {
            val (beforePath, afterPath) = splitRenamePart(pathPart)
            return if (beforePath == afterPath) pathPart to pathPart else beforePath to afterPath
        }
        val prefix = pathPart.substring(0, openBrace)
        val suffix = pathPart.substring(closeBrace + 1)
        val inside = pathPart.substring(openBrace + 1, closeBrace)
        val (before, after) = splitRenamePart(inside)
        return (prefix + before + suffix) to (prefix + after + suffix)
    }

    private fun splitRenamePart(part: String): Pair<String, String> {
        val arrowIndex = part.indexOf(" => ")
        if (arrowIndex != -1) {
            return part.substring(0, arrowIndex) to part.substring(arrowIndex + 4)
        }
        val fallbackIndex = part.indexOf("=>")
        if (fallbackIndex != -1) {
            return part.substring(0, fallbackIndex).trim() to part.substring(fallbackIndex + 2).trim()
        }
        val spacedArrowIndex = part.indexOf(" -> ")
        if (spacedArrowIndex != -1) {
            return part.substring(0, spacedArrowIndex) to part.substring(spacedArrowIndex + 4)
        }
        val tightArrowIndex = part.indexOf("->")
        if (tightArrowIndex != -1) {
            return part.substring(0, tightArrowIndex).trim() to part.substring(tightArrowIndex + 2).trim()
        }
        return part to part
    }
}
