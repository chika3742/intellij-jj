package net.chikach.intellijjj

import net.chikach.intellijjj.jujutsu.JujutsuTemplateUtil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JujutsuTemplateUtilTest {
    @Test
    fun escapeHandlesQuotesAndBackslashes() {
        val escaped = JujutsuTemplateUtil.escape("a\"b\\c")
        assertEquals("a\\\"b\\\\c", escaped)
    }

    @Test
    fun mappedListCreatesJsonArrayExpression() {
        val expr = JujutsuTemplateUtil.mappedList("parents", "|p| json(p)")
        assertEquals("\"[\" ++ parents.map(|p| json(p)).join(\",\") ++ \"]\"", expr)
    }

    @Test
    fun createSerializableTemplateBuildsExpectedShape() {
        val template = JujutsuTemplateUtil.createSerializableTemplate(
            linkedMapOf(
                "a" to "json(a)",
                "b" to "json(b)",
            )
        )
        assertTrue(template.startsWith("\"{\" ++ "))
        assertTrue(template.contains("\"\\\"a\\\":\" ++ json(a)"))
        assertTrue(template.contains("\"\\\"b\\\":\" ++ json(b)"))
        assertTrue(template.endsWith(" ++ \"}\" ++ \"\n\""))
    }
}
