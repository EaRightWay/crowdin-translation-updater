plugins {
    id("io.github.earightway.crowdin")
}

crowdin {
    // Credentials come from CROWDIN_PROJECT_ID and CROWDIN_TOKEN, and failing that from the
    // crowdin.projectId and crowdin.token Gradle properties. Point the sample at your own project:
    // projectId.set("123456")

    bundles.set(listOf("site"))

    // The bits a real build usually needs, kept here so the sample exercises them too.
    languageMapping.set(mapOf("zh-CN" to "zh_CN", "zh-TW" to "zh_TW"))
    languageAliases.set(mapOf("in" to "id"))
}
