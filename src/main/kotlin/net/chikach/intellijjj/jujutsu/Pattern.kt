package net.chikach.intellijjj.jujutsu

sealed class Pattern : RevsetNode() {
    companion object {
        /**
         * Creates an OR pattern for the provided list.
         */
        fun or(patterns: List<Pattern>): Pattern {
            return PatternOperator("|", OperatorType.INFIX, patterns)
        }
        
        /**
         * Creates an AND pattern for the provided list.
         */
        fun and(patterns: List<Pattern>): Pattern {
            return PatternOperator("&", OperatorType.INFIX, patterns)
        }

        /**
         * Substring string pattern.
         */
        fun substring(text: String, caseSensitive: Boolean = true): Pattern {
            return PatternExpression("substring", text, caseSensitive)
        }

        /**
         * Regex string pattern.
         */
        fun regex(regex: String, caseSensitive: Boolean = true): Pattern {
            return PatternExpression("regex", regex, caseSensitive)
        }

        /**
         * File pattern matches workspace-relative file (or exact) path.
         */
        fun root(query: String, caseSensitive: Boolean = true): Pattern {
            return PatternExpression("root", query, caseSensitive)
        }

        /**
         * Date pattern for "after".
         */
        fun dateAfter(date: String): Pattern {
            return PatternExpression("after", date, true)
        }

        /**
         * Date pattern for "before".
         */
        fun dateBefore(date: String): Pattern {
            return PatternExpression("before", date, true)
        }
    }
    
    private data class PatternExpression(val type: String, val text: String, val caseSensitive: Boolean): Pattern() {
        override fun stringify(): String = buildString {
            append(type)
            if (!caseSensitive) {
                append("-i")
            }
            append(":\"")
            append(JujutsuTemplateUtil.escape(text))
            append('"')
        }
    }

    private data class PatternOperator(val token: String, val type: OperatorType, val operands: List<Pattern>) : Pattern() {
        init {
            require(operands.isNotEmpty()) { "operands must not be empty" }
        }
        
        override fun stringify(): String {
            return when (type) {
                OperatorType.INFIX -> operands.joinToString(" $token ") { it.stringify() }
                OperatorType.PREFIX -> "$token${operands[0].stringify()}"
            }
        }
    }
    
    enum class OperatorType {
        INFIX,
        PREFIX,
    }
}
