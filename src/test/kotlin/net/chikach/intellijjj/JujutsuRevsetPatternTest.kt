package net.chikach.intellijjj

import net.chikach.intellijjj.jujutsu.Pattern
import net.chikach.intellijjj.jujutsu.Revset
import kotlin.test.Test
import kotlin.test.assertEquals

class JujutsuRevsetPatternTest {
    @Test
    fun revsetStringifyOperators() {
        val revset = Revset.and(
            Revset.commitId("abc"),
            Revset.parentOf(Revset.WORKING_COPY)
        )
        assertEquals("(commit_id(abc)&(@-))", revset.stringify())
    }

    @Test
    fun rangeWithRootRendersEmptySides() {
        assertEquals("(::)", Revset.rangeWithRoot().stringify())
    }

    @Test
    fun patternStringify() {
        val pattern = Pattern.or(
            listOf(
                Pattern.root("src/main/kotlin"),
                Pattern.root("src/main/java"),
            )
        )
        assertEquals("root:\"src/main/kotlin\" | root:\"src/main/java\"", pattern.stringify())
    }

    @Test
    fun patternStringifyCaseInsensitive() {
        val pattern = Pattern.substring("Abc", caseSensitive = false)
        assertEquals("substring-i:\"Abc\"", pattern.stringify())
    }
}
