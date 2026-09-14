package io.github.earightway.crowdin

internal const val PROPERTIES_SUFFIX = ".properties"

internal data class TranslationEntry(val bundle: String, val language: String)

/**
 * Maps one entry of a Crowdin translation build to the bundle and language it holds.
 *
 * Both export layouts are accepted, because the pattern is a project setting in Crowdin:
 * `de/site.properties` (language as a directory) and `site_de.properties` (language in the name).
 */
internal fun resolveTranslationEntry(
    entryPath: String,
    bundles: Collection<String>,
): TranslationEntry? {
    val segments = entryPath.replace('\\', '/').split('/').filter { it.isNotEmpty() }
    val fileName = segments.lastOrNull()?.takeIf { it.endsWith(PROPERTIES_SUFFIX) } ?: return null
    val base = fileName.removeSuffix(PROPERTIES_SUFFIX)

    if (base in bundles) {
        val language = segments.dropLast(1).lastOrNull() ?: return null
        return TranslationEntry(base, language)
    }
    val bundle = bundles.filter { base.startsWith("${it}_") }.maxByOrNull { it.length } ?: return null
    return TranslationEntry(bundle, base.removePrefix("${bundle}_"))
}

internal fun bundleFileName(
    bundle: String,
    languageSuffix: String?,
): String = if (languageSuffix == null) "$bundle$PROPERTIES_SUFFIX" else "${bundle}_$languageSuffix$PROPERTIES_SUFFIX"

/**
 * Rewrites every non-ASCII character as a `\ uXXXX` escape, the form `java.util.Properties` reads and
 * the form already committed here. Content that Crowdin exported escaped passes through unchanged.
 */
internal fun escapeNonAscii(text: String): String {
    if (text.all { it.code <= LAST_ASCII }) return text
    val escaped = StringBuilder(text.length)
    text.forEach { character ->
        if (character.code <= LAST_ASCII) {
            escaped.append(character)
        } else {
            escaped.append("\\u").append(String.format("%04x", character.code))
        }
    }
    return escaped.toString()
}

private const val LAST_ASCII = 126
