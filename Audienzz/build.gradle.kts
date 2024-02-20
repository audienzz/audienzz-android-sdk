plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.allopen)
    alias(libs.plugins.kotlin.serialization)
}

android {
    compileSdk = libs.versions.sdk.compile.get().toInt()
    buildToolsVersion = libs.versions.build.tools.version.get()

    defaultConfig {
        namespace = "ch.audienzz"
        minSdk = libs.versions.sdk.min.get().toInt()
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        targetCompatibility(libs.versions.jvm.target.asProvider().get().toInt())
        sourceCompatibility(libs.versions.jvm.target.asProvider().get().toInt())
    }

    kotlinOptions {
        jvmTarget = libs.versions.jvm.target.kotlin.get()
    }

    lint.checkDependencies = true
}

tasks.withType(Test::class) {
    testLogging {
        setExceptionFormat("full")
    }

    addTestListener(TestListenerImpl())
}

dependencies {
    // Kotlin
    implementation(platform(libs.kotlin.bom))
    implementation(libs.bundles.kotlin.bom)

    // prebid
    // TODO change api to implementation
    api(libs.prebid)

    // Frameworks
    implementation(libs.bundles.multithreading)
    implementation(libs.bundles.data)

    // Test
    testImplementation(libs.bundles.test)
}
