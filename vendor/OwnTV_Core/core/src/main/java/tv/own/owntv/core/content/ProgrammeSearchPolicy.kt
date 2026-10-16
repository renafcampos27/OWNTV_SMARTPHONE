package tv.own.owntv.core.content

object ProgrammeSearchPolicy {
    /** Search text is literal, so percent/underscore cannot unexpectedly match the entire guide. */
    fun literalPattern(query: String): String = "%" + query.trim().replace("\\", "\\\\")
        .replace("%", "\\%").replace("_", "\\_") + "%"
}
