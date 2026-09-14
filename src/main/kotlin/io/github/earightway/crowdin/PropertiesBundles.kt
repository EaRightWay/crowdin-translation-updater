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
 * Line endings to write, since a Crowdin export and the committed file need not agree.
 *
 * `PRESERVE` keeps whatever the committed file already uses, which is what keeps a pull from
 * rewriting every file the first time an export switches from CRLF to LF or back.
 */
enum class LineEndings {
    PRESERVE,
    LF,
    CRLF,
    ;

    companion object {
        fun of(name: String): LineEndings =
            values().firstOrNull { it.name.equals(name, ignoreCase = true) }
                ?: error("Unknown lineEndings '$name'. Use one of ${values().joinToString { it.name.lowercase() }}.")
    }
}

/**
 * Gives [content] the line endings [mode] asks for, reading them off [existing] when preserving.
 * A file that does not exist yet keeps the endings Crowdin exported.
 */
internal fun applyLineEndings(
    content: String,
    existing: String?,
    mode: LineEndings,
): String {
    val unix = content.replace("\r\n", "\n")
    val carriageReturns =
        when (mode) {
            LineEndings.LF -> false
            LineEndings.CRLF -> true
            LineEndings.PRESERVE -> existing?.contains("\r\n") ?: (unix.length != content.length)
        }
    return if (carriageReturns) unix.replace("\n", "\r\n") else unix
}

