pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = rootProject.projectDir.name
include(
        ":Audienzz",
        ":PrebidDemoJava",
        ":PrebidDemoKotlin",
        ":PrebidInternalTestApp",
)

project(":PrebidDemoJava").projectDir = File("Example/PrebidDemoJava")
project(":PrebidDemoKotlin").projectDir = File("Example/PrebidDemoKotlin")
project(":PrebidInternalTestApp").projectDir = File("Example/PrebidInternalTestApp")

buildCache {
    local {
        directory = File(rootDir, "build-cache")
        removeUnusedEntriesAfterDays = 7
    }
}
