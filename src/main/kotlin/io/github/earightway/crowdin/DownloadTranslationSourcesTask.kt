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

/**
 * Downloads the Crowdin source files and rewrites the English bundles, the reverse of
 * `uploadTranslationSources`, for when source strings were edited in Crowdin directly.
 */
@UntrackedTask(because = "Reads the current state of Crowdin and rewrites committed resources")
abstract class DownloadTranslationSourcesTask : DefaultTask() {
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
    abstract val sourceFileNames: MapProperty<String, String>

    @get:Internal
    abstract val writeFallbackBundle: Property<Boolean>

    @get:Internal
    abstract val lineEndings: Property<String>

    @get:Internal
    @get:Option(option = "bundle", description = "Only download these bundles, e.g. --bundle=site. Repeatable.")
    abstract val bundleFilter: ListProperty<String>

    @get:Internal
    @get:Option(option = "report-only", description = "Report what would change without writing any file.")
    abstract val reportOnly: Property<Boolean>

    @TaskAction
    fun downloadSources() {
        val api = CrowdinApi(baseUrl.get(), token.orNull.orEmpty().ifEmpty { missing("token", "CROWDIN_TOKEN") })
        val project = projectId.orNull.orEmpty().ifEmpty { missing("projectId", "CROWDIN_PROJECT_ID") }
        val selected = bundles.get().filter { bundleFilter.get().isEmpty() || it in bundleFilter.get() }
        if (selected.isEmpty()) throw GradleException("No bundle matches --bundle=${bundleFilter.get().joinToString()}")

        val remoteFiles = api.files(project)
        selected.forEach { bundle -> download(api, project, bundle, remoteFiles) }
    }

    private fun download(
        api: CrowdinApi,
        project: String,
        bundle: String,
        remoteFiles: Map<String, Long>,
    ) {
        val remoteName = sourceFileNames.get()[bundle] ?: "$bundle$PROPERTIES_SUFFIX"
        val fileId =
            remoteFiles[remoteName]
                ?: throw GradleException(
                    "Crowdin project $project has no file named '$remoteName'. Map it via crowdin.sourceFileNames.",
                )
        val url =
            api
                .get("/projects/$project/files/$fileId/download")
                .requireField("data")
                .requireField("url")
                .asText()
        // ISO-8859-1 passes the bytes through unchanged, the same as for translation exports.
        val content = api.download(url).toString(Charsets.ISO_8859_1)
        write(bundleFileName(bundle, sourceLanguage.get()), content)
        if (writeFallbackBundle.get()) write(bundleFileName(bundle, null), content)
    }

    private fun write(
        fileName: String,
        content: String,
    ) {
        val target = resourcesDir.get().file(fileName).asFile
        val existing = target.takeIf { it.exists() }?.readText(Charsets.ISO_8859_1)
        val escaped = applyLineEndings(content, existing, LineEndings.of(lineEndings.get()))
        if (existing == escaped) {
            logger.lifecycle("unchanged: $fileName")
            return
        }
        if (reportOnly.get()) {
            logger.lifecycle("would write: $fileName")
            return
        }
        target.writeText(escaped, Charsets.ISO_8859_1)
        logger.lifecycle("updated: $fileName")
    }

    private fun missing(
        property: String,
        environmentVariable: String,
    ): Nothing =
        throw GradleException(
            "Crowdin $property is not set. Export $environmentVariable, add crowdin.$property to ~/.gradle/gradle.properties, " +
                "or set it in the crowdin { } block.",
        )
}
