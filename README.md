# crowdin-translation-updater

Automatically syncs translated files from Crowdin to the GitHub repository.

A Gradle plugin that keeps `<bundle>_<language>.properties` resource bundles in step with a Crowdin
project. It works like a code generator: you run a task by hand and commit what it writes.

| Task | Direction |
|---|---|
| `updateTranslations` | Crowdin → repository. Builds an export, downloads it, rewrites the translated bundles. |
| `uploadTranslationSources` | Repository → Crowdin. Uploads the source-language bundles as the Crowdin sources. |

## Install

The plugin is served by [JitPack](https://jitpack.io), so the consuming build resolves it straight
from this repository's tags — no artifact hosting, no credentials.

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://jitpack.io")
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "io.github.earightway.crowdin") {
                useModule("com.github.EaRightWay:crowdin-translation-updater:1.0.0")
            }
        }
    }
}
```

```kotlin
// build.gradle.kts of the module that owns the resource bundles
plugins {
    id("io.github.earightway.crowdin")
}
```

The mapping in `resolutionStrategy` is needed because JitPack publishes under the
`com.github.EaRightWay` group rather than under a plugin marker.

## Configure

```kotlin
crowdin {
    // Credentials are read from the CROWDIN_PROJECT_ID and CROWDIN_TOKEN environment variables, and
    // failing that from the crowdin.projectId and crowdin.token Gradle properties
    // (~/.gradle/gradle.properties keeps them out of the repository). Override here if needed:
    // projectId.set("123456")
    // token.set(providers.gradleProperty("my.own.property"))

    bundles.set(listOf("site", "templates", "sms"))

    // Defaults worth knowing:
    // baseUrl.set("https://api.crowdin.com/api/v2")   // Crowdin Enterprise: https://<org>.api.crowdin.com/api/v2
    // resourcesDir.set(layout.projectDirectory.dir("src/main/resources"))
    // sourceLanguage.set("en")
    // writeFallbackBundle.set(true)                   // also write site.properties from the source language
    // skipUntranslatedStrings.set(false)         // true omits untranslated keys instead of falling back to English
    // exportApprovedOnly.set(false)
    // lineEndings.set("preserve")                // or "lf" / "crlf"

    // Crowdin language code -> file suffix, where it is not a plain "-" to "_" rewrite.
    languageMapping.set(mapOf("zh-CN" to "zh_CN", "zh-TW" to "zh_TW"))

    // Legacy duplicates: write site_in.properties from the Indonesian translation.
    languageAliases.set(mapOf("in" to "id"))

    // Source file name in Crowdin, where it is not <bundle>.properties.
    // sourceFileNames.set(mapOf("site" to "site_en.properties"))
}
```

## Run

```bash
export CROWDIN_PROJECT_ID=... CROWDIN_TOKEN=...        # personal access token with project scope
./gradlew updateTranslations
./gradlew updateTranslations --report-only             # list what would change, write nothing
./gradlew updateTranslations --language=de             # one language, repeatable
./gradlew uploadTranslationSources
./gradlew uploadTranslationSources --bundle=site       # one bundle, repeatable
```

## Behaviour

- Content is written byte-for-byte as exported. Whether a file arrives `\uXXXX`-escaped or as raw
  UTF-8 is a per-file setting in Crowdin, and the plugin does not second-guess it.
- Line endings follow `lineEndings`: `preserve` (default) keeps what each committed file already
  uses, so an export that switches from CRLF to LF does not rewrite every file. `lf` and `crlf`
  force one.
- Files whose content would not change are left alone, including their timestamps.
- The report names committed languages the export does not cover, so languages maintained outside
  Crowdin are visible rather than silently stale.
- Both Crowdin export layouts are understood — `de/site.properties` (language as a directory) and
  `site_de.properties` (language in the file name) — because the pattern is a Crowdin-side setting.
- The source-language bundle is the source of truth and is never overwritten unless Crowdin itself
  exports that language.
- Language aliases refresh only files that already exist. Creating a missing one would change which
  bundle the JVM resolves at runtime, which is a decision for the project, not for a sync tool.
- `uploadTranslationSources` updates existing Crowdin files; it never creates them, since file type
  and export pattern are configured in Crowdin. A missing file fails with the name it looked for.
- Both tasks are untracked: they always run, and they never take part in up-to-date checks or the
  build cache.

## Develop

```bash
./gradlew build        # compiles, validates the task annotations, runs the unit tests
```

To try a change from the consuming build without publishing, add the checkout as an included build:

```kotlin
// settings.gradle.kts of the consuming project
pluginManagement {
    includeBuild("../crowdin-translation-updater")
}
```

## Release

JitPack builds a tag on first request, so a release is just a tag:

```bash
git tag 1.0.1 && git push origin 1.0.1
```

Then point `useModule(...)` in the consuming build at the new version. Use bare version tags
(`1.0.1`, not `v1.0.1`) so the tag and the artifact version match.
