package net.chikach.intellijjj.jujutsu

object JujutsuTemplateUtil {
    /**
     * Creates a serializable template string from the given parameters.
     * 
     * @param expressions A map of the JSON key to Jujutsu template expression.
    **/
    fun createSerializableTemplate(expressions: Map<String, String>): String {
        return buildString {
            append("\"{\" ++ ")
            expressions.entries.map { (key, expr) ->
                "\"\\\"$key\\\":\" ++ $expr"
            }.joinTo(this, "++ \",\" ++") { it }
            append(" ++ \"}\" ++ \"\n\"")
        }
    }

    /**
     * Creates a Jujutsu template expression that represents a list by mapping the given expression.
     * 
     * @param targetExpr The expression that yields the target list. This must be `List` type.
     * @param transformExpr The expression to transform each element of the list. Each item must be `String` type which
     * represents the serialized JSON value.
     */
    fun mappedList(targetExpr: String, transformExpr: String): String {
        return buildString {
            append("\"[\" ++ $targetExpr.map(")
            append(transformExpr)
            append(").join(\",\") ++ \"]\"")
        }
    }
    
    /**
     * Creates a Jujutsu template expression that wraps the given expression in json(...).
     */
    fun json(expression: String): String {
        return buildString {
            append("json(")
            append(expression)
            append(")")
        }
    }

    fun escape(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
    }
}