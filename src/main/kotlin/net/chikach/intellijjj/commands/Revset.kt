package net.chikach.intellijjj.commands

sealed class Revset {
    companion object {
        val EMPTY: Revset = Symbol("")
        val WORKING_COPY: Revset = Symbol("@")

        fun parentOf(revset: Revset): Revset {
            return Operator("-", listOf(revset), OperatorType.POSTFIX)
        }

        fun and(a: Revset, b: Revset): Revset {
            return Operator("&", listOf(a, b), OperatorType.INFIX)
        }

        fun or(revsets: List<Revset>): Revset {
            if (revsets.isEmpty()) return EMPTY
            if (revsets.size == 1) return revsets.first()
            return Operator("|", revsets, OperatorType.INFIX)
        }

        fun bookmarks(): Revset {
            // TODO: Implement pattern matching for bookmarks
            return Function("bookmarks", emptyList())
        }

        fun rangeWithRoot(from: Revset? = null, to: Revset? = null): Revset {
            return Operator("::", listOf(from ?: EMPTY, to ?: EMPTY), OperatorType.INFIX_STANDALONE)
        }

        fun commitId(value: String): Revset {
            return Function("commit_id", listOf(Symbol(value)))
        }
    }
    
    private data class Symbol(val name: String) : Revset() {
        override fun stringify(): String {
            return name
        }
    }

    private data class Function(
        val name: String,
        val args: List<Revset> = emptyList()
    ) : Revset() {
        init {
            require(name.isNotBlank()) { "Function name cannot be blank" }
        }

        override fun stringify(): String {
            return buildString {
                append(name)
                append('(')
                append(args.joinToString(", ") { it.stringify() })
                append(')')
            }
        }
    }

    private enum class OperatorType {
        INFIX_STANDALONE,
        INFIX,
        POSTFIX,
    }

    private data class Operator(
        val token: String,
        val operands: List<Revset>,
        val type: OperatorType,
    ) : Revset() {
        init {
            require(token.isNotBlank()) { "Operator token cannot be blank" }
            when (type) {
                OperatorType.INFIX_STANDALONE -> {
                    require(operands.size <= 2) { "Too many operands" }
                }
                OperatorType.INFIX -> {
                    require(operands.size >= 2) { "Infix operator requires at least 2 operands" }
                }
                OperatorType.POSTFIX -> {
                    require(operands.size == 1) { "Postfix operator require one operand" }
                }
            }
        }

        override fun stringify(): String {
            val rendered = when (type) {
                OperatorType.POSTFIX -> buildString {
                    append(operands.first().stringify())
                    append(token)
                }
                OperatorType.INFIX_STANDALONE -> buildString {
                    operands.getOrNull(0).let {
                        if (it != null) append(it.stringify())
                    }
                    append(token)
                    operands.getOrNull(1).let {
                        if (it != null) append(it.stringify())
                    }
                }
                OperatorType.INFIX -> operands.joinToString(token) { it.stringify() }
            }
            return "($rendered)" 
        }
    }
    
    abstract fun stringify(): String
}
