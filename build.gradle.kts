import Util.getBuildCacheDir

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.detekt)
}

allprojects {
    apply(plugin = rootProject.project.libs.plugins.detekt.get().pluginId)

    detekt {
        toolVersion = rootProject.project.libs.versions.detekt.get()
        parallel = false
        allRules = true
        debug = false
        ignoreFailures = false
        config.setFrom(files("../detekt-lint/src/main/resources/config.yml"))
        source.from(
            files(
                // Java
                "src/debug/java",
                "src/release/java",
                "src/main/java",
                "src/test/java",
                // Kotlin
                "src/debug/kotlin",
                "src/release/kotlin",
                "src/main/kotlin",
                "src/test/kotlin",
            ),
        )
    }

    dependencies {
        detektPlugins(rootProject.project.libs.detekt.formatting)
    }
}

buildscript {
    extra.set(Extra.KOTLIN_VERSION, libs.gradle.kotlin.get().version)
}

tasks {
    register("clean", Delete::class) {
        delete(layout.buildDirectory.get().asFile)
        delete(getBuildCacheDir())
    }
}