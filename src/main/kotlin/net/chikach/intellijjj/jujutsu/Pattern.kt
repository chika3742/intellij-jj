package net.chikach.intellijjj.jujutsu

sealed class Pattern : RevsetNode() {
    data class PatternExpression(val type: String, val text: String, val caseSensitive: Boolean): Pattern() {
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

    data class PatternOperator(val token: String, val type: OperatorType, val operands: List<Pattern>) : Pattern() {
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
