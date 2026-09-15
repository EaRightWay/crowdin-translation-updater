plugins {
    `kotlin-dsl`
    `maven-publish`
    id("com.gradle.plugin-publish") version "1.3.1"
}

// Published to the Gradle Plugin Portal, so the coordinates match the plugin id namespace.
// VERSION names the release being published; the fallback keeps local builds and publishToMavenLocal
// working. The portal rejects a version it has already served, so bump one of the two per release.
group = "io.github.earightway"
version = System.getenv("VERSION") ?: "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.4")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.assertj:assertj-core:3.25.1")
}

gradlePlugin {
    website.set("https://github.com/EaRightWay/crowdin-translation-updater")
    vcsUrl.set("https://github.com/EaRightWay/crowdin-translation-updater.git")

    plugins {
        create("crowdin") {
            id = "io.github.earightway.crowdin"
            implementationClass = "io.github.earightway.crowdin.CrowdinPlugin"
            displayName = "Crowdin translation updater"
            description = "Gradle tasks that pull Crowdin translations into resource bundles and push the source bundle back"
            tags.set(listOf("crowdin", "i18n", "l10n", "localization", "translations", "properties"))
        }
    }
}

// The portal and any Maven consumer read the license off the POM, not off the LICENSE file, and
// java-gradle-plugin adds the marker publication as well as the module itself.
publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            licenses {
                license {
                    name.set("The Apache License, Version 2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                }
            }
        }
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
