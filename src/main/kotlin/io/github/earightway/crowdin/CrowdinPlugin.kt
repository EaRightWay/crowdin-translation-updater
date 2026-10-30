package io.github.earightway.crowdin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register

/**
 * Adds the translation tasks:
 *
 * - `downloadTranslations` pulls the current Crowdin translations into `src/main/resources`
 * - `downloadTranslationsSources` pulls only the Crowdin sources into the English bundles
 * - `uploadTranslationSources` pushes the English bundles back up as the Crowdin sources
 */
class CrowdinPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val crowdin = project.extensions.create<CrowdinExtension>("crowdin")
        crowdin.projectId.convention(
            project.providers
                .environmentVariable("CROWDIN_PROJECT_ID")
                .orElse(project.providers.gradleProperty("crowdin.projectId")),
        )
        crowdin.token.convention(
            project.providers
                .environmentVariable("CROWDIN_TOKEN")
                .orElse(project.providers.gradleProperty("crowdin.token")),
        )
        crowdin.baseUrl.convention("https://api.crowdin.com/api/v2")
        crowdin.resourcesDir.convention(project.layout.projectDirectory.dir("src/main/resources"))
        crowdin.bundles.convention(
            project.provider { discoverBundles(crowdin.resourcesDir.get().asFile) },
        )
        crowdin.sourceLanguage.convention("en")
        crowdin.writeFallbackBundle.convention(true)
        crowdin.skipUntranslatedStrings.convention(false)
        crowdin.exportApprovedOnly.convention(false)
        crowdin.lineEndings.convention("preserve")

        project.tasks.register<DownloadTranslationsTask>("downloadTranslations") {
            group = TASK_GROUP
            description = "Downloads the Crowdin translations into src/main/resources"
            projectId.set(crowdin.projectId)
            token.set(crowdin.token)
            baseUrl.set(crowdin.baseUrl)
            resourcesDir.set(crowdin.resourcesDir)
            bundles.set(crowdin.bundles)
            sourceLanguage.set(crowdin.sourceLanguage)
            languageMapping.set(crowdin.languageMapping)
            languageAliases.set(crowdin.languageAliases)
            skipUntranslatedStrings.set(crowdin.skipUntranslatedStrings)
            exportApprovedOnly.set(crowdin.exportApprovedOnly)
            lineEndings.set(crowdin.lineEndings)
            reportOnly.convention(false)
        }

        project.tasks.register<UploadTranslationSourcesTask>("uploadTranslationSources") {
            group = TASK_GROUP
            description = "Uploads the English bundles to Crowdin as translation sources"
            projectId.set(crowdin.projectId)
            token.set(crowdin.token)
            baseUrl.set(crowdin.baseUrl)
            resourcesDir.set(crowdin.resourcesDir)
            bundles.set(crowdin.bundles)
            sourceLanguage.set(crowdin.sourceLanguage)
            sourceFileNames.set(crowdin.sourceFileNames)
            reportOnly.convention(false)
        }

        project.tasks.register<DownloadTranslationSourcesTask>("downloadTranslationsSources") {
            group = TASK_GROUP
            description = "Downloads the Crowdin source files into the English bundles"
            projectId.set(crowdin.projectId)
            token.set(crowdin.token)
            baseUrl.set(crowdin.baseUrl)
            resourcesDir.set(crowdin.resourcesDir)
            bundles.set(crowdin.bundles)
            sourceLanguage.set(crowdin.sourceLanguage)
            sourceFileNames.set(crowdin.sourceFileNames)
            writeFallbackBundle.set(crowdin.writeFallbackBundle)
            lineEndings.set(crowdin.lineEndings)
            reportOnly.convention(false)
        }
    }

    private companion object {
        const val TASK_GROUP = "crowdin"
    }
}
