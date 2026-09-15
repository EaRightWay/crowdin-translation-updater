plugins {
    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "2.2.1"
}

// Coordinates match the plugin id namespace on the Gradle Plugin Portal. VERSION names the release
// being published; the fallback keeps local builds and publishToMavenLocal working.
group = "io.github.earightway"
version = System.getenv("VERSION") ?: "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.4")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

gradlePlugin {
    website = "https://github.com/EaRightWay/crowdin-translation-updater"
    vcsUrl = "https://github.com/EaRightWay/crowdin-translation-updater.git"

    plugins.create("crowdin") {
        id = "io.github.earightway.crowdin"
        implementationClass = "io.github.earightway.crowdin.CrowdinPlugin"
        displayName = "Crowdin translation updater"
        description = "Gradle tasks that pull Crowdin translations into resource bundles and push the source bundle back"
        tags = listOf("crowdin", "i18n", "l10n", "localization", "translations", "properties")
    }
}

// The portal and any Maven consumer read the license off the POM, not off the LICENSE file.
publishing.publications.withType<MavenPublication>().configureEach {
    pom.licenses {
        license {
            name = "The Apache License, Version 2.0"
            url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
