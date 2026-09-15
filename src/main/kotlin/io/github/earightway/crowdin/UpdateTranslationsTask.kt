package io.github.earightway.crowdin

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.api.tasks.options.Option
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * Downloads the current Crowdin translations and rewrites the `<bundle>_<language>.properties` files.
 *
 * Run it by hand and commit the result, the same way `generateJooq` regenerates `src/main/jooq`.
 */
@UntrackedTask(because = "Reads the current state of Crowdin and rewrites committed resources")
abstract class UpdateTranslationsTask : DefaultTask() {
    @get:Internal
    abstract val projectId: Property<String>

    @get:Internal
    abstract val token: Property<String>

    @get:Internal
    abstract val baseUrl: Property<String>

    @get:Internal
    abstract val resourcesDir: DirectoryProperty

    @get:Internal
    abstract val bundles: ListProperty<String>

    @get:Internal
    abstract val sourceLanguage: Property<String>

    @get:Internal
    abstract val writeFallbackBundle: Property<Boolean>

    @get:Internal
    abstract val languageMapping: MapProperty<String, String>

    @get:Internal
    abstract val languageAliases: MapProperty<String, String>

    @get:Internal
    abstract val skipUntranslatedStrings: Property<Boolean>

    @get:Internal
    abstract val exportApprovedOnly: Property<Boolean>

    @get:Internal
    abstract val lineEndings: Property<String>

    @get:Internal
    @get:Option(
        option = "language",
        description = "Only write these file suffixes, e.g. --language=de --language=zh_CN. Repeatable.",
    )
    abstract val languageFilter: ListProperty<String>

    @get:Internal
    @get:Option(option = "report-only", description = "Report what would change without writing any file.")
    abstract val reportOnly: Property<Boolean>

    @TaskAction
    fun updateTranslations() {
        val bundleNames = bundles.get().ifEmpty { noBundles() }
        val api = CrowdinApi(baseUrl.get(), token.orNull.orEmpty().ifEmpty { missing("token", "CROWDIN_TOKEN") })
        val project = projectId.orNull.orEmpty().ifEmpty { missing("projectId", "CROWDIN_PROJECT_ID") }

        val archive = api.download(exportUrl(api, project))
        val written = writeBundles(archive, bundleNames)
        writeAliases(written)
        report(written, bundleNames)
    }

    private fun exportUrl(
        api: CrowdinApi,
        project: String,
    ): String {
        val build =
            api
                .post(
                    "/projects/$project/translations/builds",
                    mapOf(
                        "skipUntranslatedStrings" to skipUntranslatedStrings.get(),
                        "exportApprovedOnly" to exportApprovedOnly.get(),
                    ),
                ).requireField("data")
        val buildId = build.requireField("id").asLong()
        logger.lifecycle("Crowdin build $buildId requested for project $project")
        awaitBuild(api, project, buildId)
        return api
            .get("/projects/$project/translations/builds/$buildId/download")
            .requireField("data")
            .requireField("url")
            .asText()
    }

    private fun awaitBuild(
        api: CrowdinApi,
        project: String,
        buildId: Long,
    ) {
        val deadline = System.currentTimeMillis() + BUILD_TIMEOUT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            val status = api.get("/projects/$project/translations/builds/$buildId").requireField("data")
            when (val state = status.requireField("status").asText()) {
                "finished" -> return
                "created", "inProgress" -> logger.lifecycle("Crowdin build $buildId: ${status.get("progress")?.asInt() ?: 0}%")
                else -> throw GradleException("Crowdin build $buildId ended with status '$state'")
            }
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }
        throw GradleException("Crowdin build $buildId did not finish within ${BUILD_TIMEOUT_MILLIS / MILLIS_PER_MINUTE} minutes")
    }

    private fun writeBundles(
        archive: ByteArray,
        bundleNames: List<String>,
    ): Map<TranslationEntry, String> {
        val written = mutableMapOf<TranslationEntry, String>()
        ZipInputStream(ByteArrayInputStream(archive)).use { zip ->
            generateSequence { zip.nextEntry }
                .filterNot { it.isDirectory }
                .forEach { entry ->
                    // ISO-8859-1 maps every byte to one char and back, so the export passes through
                    // unchanged whether Crowdin escaped it or wrote raw UTF-8.
                    val content = zip.readBytes().toString(Charsets.ISO_8859_1)
                    val resolved = resolveTranslationEntry(entry.name, bundleNames) ?: return@forEach
                    val suffix = languageMapping.get()[resolved.language] ?: resolved.language.replace('-', '_')
                    if (!accepted(suffix)) return@forEach
                    write(bundleFileName(resolved.bundle, suffix), content)
                    written[TranslationEntry(resolved.bundle, suffix)] = content
                    if (suffix == sourceLanguage.get() && writeFallbackBundle.get()) {
                        write(bundleFileName(resolved.bundle, null), content)
                    }
                }
        }
        return written
    }

    /**
     * Refreshes the legacy duplicates that are already committed (Java's `in` for Indonesian, say).
     * A duplicate that does not exist yet is left alone: adding one changes which bundle the JVM
     * resolves at runtime, which is a product decision rather than a job for the sync.
     */
    private fun writeAliases(written: Map<TranslationEntry, String>) {
        languageAliases.get().forEach { (alias, source) ->
            if (!accepted(alias)) return@forEach
            written.filterKeys { it.language == source }.forEach { (entry, content) ->
                val fileName = bundleFileName(entry.bundle, alias)
                if (resourcesDir
                        .get()
                        .file(fileName)
                        .asFile
                        .exists()
                ) {
                    write(fileName, content)
                } else {
                    logger.info("skipped absent alias: $fileName")
                }
            }
        }
    }

    private fun accepted(suffix: String): Boolean = languageFilter.get().let { it.isEmpty() || suffix in it }

    private fun write(
        fileName: String,
        content: String,
    ) {
        val target = resourcesDir.get().file(fileName).asFile
        val existing = target.takeIf { it.exists() }?.readText(Charsets.ISO_8859_1)
        val escaped = applyLineEndings(content, existing, LineEndings.of(lineEndings.get()))
        if (existing == escaped) {
            logger.info("unchanged: $fileName")
            return
        }
        if (reportOnly.get()) {
            logger.lifecycle("would write: $fileName")
            return
        }
        target.writeText(escaped, Charsets.ISO_8859_1)
        logger.lifecycle("updated: $fileName")
    }

    private fun report(
        written: Map<TranslationEntry, String>,
        bundleNames: List<String>,
    ) {
        if (written.isEmpty()) {
            throw GradleException(
                "The Crowdin export held none of the configured bundles ($bundleNames). " +
                    "Check crowdin.bundles against the file names in the Crowdin project.",
            )
        }
        val languages = written.keys.map { it.language }.toSortedSet()
        logger.lifecycle("${written.size} files across ${languages.size} languages: ${languages.joinToString()}")
        bundleNames
            .filterNot { bundle -> written.keys.any { it.bundle == bundle } }
            .takeIf { it.isNotEmpty() }
            ?.let { logger.warn("No translations exported for: ${it.joinToString()}") }

        val covered = languages + languageAliases.get().keys + sourceLanguage.get()
        (committedLanguages(bundleNames) - covered)
            .takeIf { it.isNotEmpty() }
            ?.let { logger.warn("Committed languages the export does not cover, left untouched: ${it.joinToString()}") }
    }

    private fun committedLanguages(bundleNames: List<String>): Set<String> =
        resourcesDir
            .get()
            .asFile
            .listFiles()
            .orEmpty()
            .mapNotNull { resolveTranslationEntry(it.name, bundleNames) }
            .map { it.language }
            .toSortedSet()

    private fun noBundles(): Nothing =
        throw GradleException(
            "No bundles found in ${resourcesDir.get().asFile}. Translated files are recognised as " +
                "<bundle>_<language>.properties with at least $DEFAULT_MINIMUM_LANGUAGES languages; " +
                "set crowdin.bundles explicitly if yours look different.",
        )

    private fun missing(
        property: String,
        environmentVariable: String,
    ): Nothing =
        throw GradleException(
            "Crowdin $property is not set. Export $environmentVariable, add crowdin.$property to ~/.gradle/gradle.properties, " +
                "or set it in the crowdin { } block.",
        )

    private companion object {
        const val POLL_INTERVAL_MILLIS = 3_000L
        const val MILLIS_PER_MINUTE = 60_000L
        const val BUILD_TIMEOUT_MILLIS = 15 * MILLIS_PER_MINUTE
    }
}
