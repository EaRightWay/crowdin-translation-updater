// A standalone build that consumes the plugin from the checkout next to it, so the plugin can be
// tried end to end with `./gradlew -p sample ...` without publishing or tagging anything first.
pluginManagement {
    includeBuild("..")
}

rootProject.name = "crowdin-sample"
