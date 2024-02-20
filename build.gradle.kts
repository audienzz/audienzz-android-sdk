import Util.getBuildCacheDir

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
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
