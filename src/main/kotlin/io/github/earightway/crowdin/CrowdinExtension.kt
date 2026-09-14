package io.github.earightway.crowdin

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property

/**
 * Configuration for the Crowdin translation tasks.
 *
 * The English bundles in `src/main/resources` are the source of truth and are edited by hand; every
 * other language is generated from Crowdin, in the same "regenerate and commit" spirit as jOOQ's
 * `src/main/jooq`.
 */
abstract class CrowdinExtension {
    /** Numeric Crowdin project id. Defaults to the `CROWDIN_PROJECT_ID` environment variable. */
    abstract val projectId: Property<String>

    /** Personal access token with `project` scope. Defaults to the `CROWDIN_TOKEN` environment variable. */
    abstract val token: Property<String>

    /** API root. Crowdin Enterprise uses `https://<organization>.api.crowdin.com/api/v2`. */
    abstract val baseUrl: Property<String>

    /** Directory holding the `<bundle>_<language>.properties` files. */
    abstract val resourcesDir: DirectoryProperty

    /** Bundle base names, e.g. `site`, `templates`. */
    abstract val bundles: ListProperty<String>

    /** Language whose bundle is uploaded as the Crowdin source. */
    abstract val sourceLanguage: Property<String>

    /** Also write the suffixless fallback bundle (`site.properties`) from the source language. */
    abstract val writeFallbackBundle: Property<Boolean>

    /**
     * Crowdin language code to file suffix, for codes that are not a straight `-` to `_` rewrite.
     * Example: `"zh-CN" to "zh_CN"`.
     */
    abstract val languageMapping: MapProperty<String, String>

    /**
     * Extra file suffixes written from another language's translation, for the legacy Java codes
     * this repository still ships. Example: `"in" to "id"` writes `site_in.properties` from Indonesian.
     */
    abstract val languageAliases: MapProperty<String, String>

    /**
     * Bundle to source file name in Crowdin, when it is not `<bundle>.properties`.
     * Only used when uploading sources.
     */
    abstract val sourceFileNames: MapProperty<String, String>

    /** Leave strings without a translation out of the export instead of falling back to English. */
    abstract val skipUntranslatedStrings: Property<Boolean>

    /** Export only strings that a proofreader approved. */
    abstract val exportApprovedOnly: Property<Boolean>
}
