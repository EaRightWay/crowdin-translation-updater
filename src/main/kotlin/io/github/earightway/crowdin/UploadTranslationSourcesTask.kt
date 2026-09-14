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
 * Uploads the English bundles as the Crowdin sources, so translators see new and changed strings.
 *
 * Existing translations and approvals are kept; strings that disappeared from the source are removed
 * by Crowdin the same way the web upload does it.
 */
@UntrackedTask(because = "Pushes the current state of committed resources to Crowdin")
abstract class UploadTranslationSourcesTask : DefaultTask() {
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
    @get:Option(option = "bundle", description = "Only upload these bundles, e.g. --bundle=site. Repeatable.")
    abstract val bundleFilter: ListProperty<String>

    @get:Internal
    @get:Option(option = "report-only", description = "Report what would be uploaded without touching Crowdin.")
    abstract val reportOnly: Property<Boolean>

    @TaskAction
    fun uploadSources() {
        val api = CrowdinApi(baseUrl.get(), token.orNull.orEmpty().ifEmpty { missing("token", "CROWDIN_TOKEN") })
        val project = projectId.orNull.orEmpty().ifEmpty { missing("projectId", "CROWDIN_PROJECT_ID") }
        val selected = bundles.get().filter { bundleFilter.get().isEmpty() || it in bundleFilter.get() }
        if (selected.isEmpty()) throw GradleException("No bundle matches --bundle=${bundleFilter.get().joinToString()}")

        val remoteFiles = remoteFiles(api, project)
        selected.forEach { bundle -> upload(api, project, bundle, remoteFiles) }
    }

    private fun upload(
        api: CrowdinApi,
        project: String,
        bundle: String,
        remoteFiles: Map<String, Long>,
    ) {
        val source = resourcesDir.get().file(bundleFileName(bundle, sourceLanguage.get())).asFile
        if (!source.isFile) throw GradleException("Source bundle ${source.name} does not exist")

        val remoteName = sourceFileNames.get()[bundle] ?: "$bundle$PROPERTIES_SUFFIX"
        val fileId =
            remoteFiles[remoteName]
                ?: throw GradleException(
                    "Crowdin project $project has no file named '$remoteName'. Create it once in Crowdin " +
                        "(so its type and export pattern are set there), or map it via crowdin.sourceFileNames.",
                )
        if (reportOnly.get()) {
            logger.lifecycle("would upload: ${source.name} -> $remoteName (file $fileId)")
            return
        }
        val storageId = api.addToStorage(remoteName, source.readBytes())
        api.put(
            "/projects/$project/files/$fileId",
            mapOf("storageId" to storageId, "updateOption" to "keep_translations_and_approvals"),
        )
        logger.lifecycle("uploaded: ${source.name} -> $remoteName")
    }

    private fun remoteFiles(
        api: CrowdinApi,
        project: String,
    ): Map<String, Long> {
        val files = mutableMapOf<String, Long>()
        var offset = 0
        while (true) {
            val page = api.get("/projects/$project/files?limit=$PAGE_SIZE&offset=$offset").requireField("data")
            page.elements().forEach { element ->
                val file = element.requireField("data")
                files.putIfAbsent(file.requireField("name").asText(), file.requireField("id").asLong())
            }
            if (page.size() < PAGE_SIZE) return files
            offset += PAGE_SIZE
        }
    }

    private fun missing(
        property: String,
        environmentVariable: String,
    ): Nothing = throw GradleException("Crowdin $property is not set. Export $environmentVariable or set crowdin.$property in the build.")

    private companion object {
        const val PAGE_SIZE = 500
    }
}
