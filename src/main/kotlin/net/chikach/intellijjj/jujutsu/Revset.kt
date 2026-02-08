package net.chikach.intellijjj.jujutsu

import com.intellij.openapi.diagnostic.logger
import com.intellij.vcs.log.VcsUser

abstract class RevsetNode {
    /**
     * Renders this revset into Jujutsu CLI syntax.
     */
    abstract fun stringify(): String
}

/**
 * Represents a Jujutsu revset expression that can be rendered for CLI usage.
 */
sealed class Revset : RevsetNode() {
    companion object {
        /**
         * Empty revset placeholder used in operators that allow omitted sides.
         */
        val EMPTY: Revset = Symbol("")

        /**
         * Revset for the working copy.
         */
        val WORKING_COPY: Revset = Symbol("@")

        /**
         * Revset for the working copy.
         */
        val ALL: Revset = Function("all")

        /**
         * Creates an AND revset, e.g. "(a&b)".
         */
        fun and(a: Revset, b: Revset): Revset {
            return Operator("&", listOf(a, b), OperatorType.INFIX)
        }

        /**
         * Creates an OR revset for the provided list.
         */
        fun or(revsets: List<Revset>): Revset {
            if (revsets.isEmpty()) return EMPTY
            if (revsets.size == 1) return revsets.first()
            return Operator("|", revsets, OperatorType.INFIX)
        }

        /**
         * Creates an OR revset.
         */
        fun or(a: Revset, b: Revset): Revset {
            return or(listOf(a, b))
        }
        
        /**
         * Creates a parent-of revset, e.g. "@" -> "(@-)".
         */
        fun parentOf(revset: Revset): Revset {
            return Operator("-", listOf(revset), OperatorType.POSTFIX)
        }

        fun author(user: VcsUser): Revset {
            return Function("author", listOf(StringLiteral(user.name)))
        }

        fun committer(user: VcsUser): Revset {
            return Function("committer", listOf(StringLiteral(user.name)))
        }

        fun user(user: VcsUser): Revset {
            logger<Revset>().warn(user.toString())
            return or(author(user), committer(user))
        }

        fun files(filePattern: Pattern): Revset {
            return Function("files", listOf(filePattern))
        }

        /**
         * Creates a revset that matches bookmarks.
         */
        fun bookmarks(): Revset {
            // TODO: Implement pattern matching for bookmarks
            return Function("bookmarks", emptyList())
        }

        /**
         * Creates a range revset, allowing either side to be omitted.
         */
        fun rangeWithRoot(from: Revset? = null, to: Revset? = null): Revset {
            return Operator("::", listOf(from ?: EMPTY, to ?: EMPTY), OperatorType.INFIX_STANDALONE)
        }

        /**
         * Creates a commit_id(...) revset.
         */
        fun commitId(value: String): Revset {
            return Function("commit_id", listOf(Symbol(value)))
        }

        /**
         * Creates a description(...) revset.
         */
        fun description(pattern: RevsetNode): Revset {
            return Function("description", listOf(pattern))
        }

        fun committerDate(pattern: Pattern): Revset {
            return Function("committer_date", listOf(pattern))
        }
    }
    
    private data class Symbol(val name: String) : Revset() {
        override fun stringify(): String {
            return name
        }
    }
    
    private data class StringLiteral(val string: String) : Revset() {
        override fun stringify(): String {
            return "\"${JujutsuTemplateUtil.escape(string)}\""
        }
    }
    
    private data class Function(
        val name: String,
        val args: List<RevsetNode> = emptyList()
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
}
