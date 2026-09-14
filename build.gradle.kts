plugins {
    `kotlin-dsl`
    `maven-publish`
}

// JitPack builds this repository on demand and serves it under the com.github.<owner> group.
// VERSION is the tag JitPack is building; the fallback keeps local builds and publishToMavenLocal working.
group = "com.github.EaRightWay"
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
    plugins {
        create("crowdin") {
            id = "io.github.earightway.crowdin"
            implementationClass = "io.github.earightway.crowdin.CrowdinPlugin"
            displayName = "Crowdin translation updater"
            description = "Gradle tasks that pull Crowdin translations into resource bundles and push the source bundle back"
        }
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
