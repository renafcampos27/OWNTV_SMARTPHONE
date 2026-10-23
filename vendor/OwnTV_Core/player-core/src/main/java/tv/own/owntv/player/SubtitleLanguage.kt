package tv.own.owntv.player

import java.util.Locale

/** Matches ISO-639-1/2 and regional tags (for example eng, en, and en-US). */
internal fun subtitleLanguageMatches(preferred: String, actual: String?): Boolean {
    if (preferred.isBlank() || actual.isNullOrBlank()) return false
    return languageIdentity(preferred) == languageIdentity(actual)
}

private fun languageIdentity(code: String): String {
    val normalized = code.trim().replace('_', '-').lowercase(Locale.ROOT)
    val locale = Locale.forLanguageTag(normalized)
    return runCatching { locale.isO3Language.lowercase(Locale.ROOT) }
        .getOrNull()
        .takeUnless { it.isNullOrBlank() }
        ?: normalized.substringBefore('-')
}

/** Explicit carry-over may normalize ISO aliases, but never exchanges two known regions/scripts. */
internal fun explicitTrackLanguageMatches(preferred: String, actual: String?): Boolean {
    if (!subtitleLanguageMatches(preferred, actual)) return false
    val wanted = Locale.forLanguageTag(preferred.trim().replace('_', '-'))
    val available = Locale.forLanguageTag(actual!!.trim().replace('_', '-'))
    return (wanted.country.isEmpty() || available.country.isEmpty() || wanted.country.equals(available.country, true)) &&
        (wanted.script.isEmpty() || available.script.isEmpty() || wanted.script.equals(available.script, true))
}
