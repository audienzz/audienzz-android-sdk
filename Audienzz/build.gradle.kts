plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.allopen)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.ksp)
    `maven-publish`
}

ksp {
    arg(RoomSchemaArgProvider(File(projectDir, "schemas")))
}

android {
    compileSdk = libs.versions.sdk.compile.get().toInt()
    buildToolsVersion = libs.versions.build.tools.version.get()
    version = getVersionName("0.0.0")

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }

    defaultConfig {
        namespace = "org.audienzz"
        minSdk = libs.versions.sdk.min.get().toInt()
        buildConfigField(
            "String",
            "EVENTS_BASE_URL",
            "\"https://dev-api.adnz.co/api/ws-event-ingester/\""
        )
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

afterEvaluate {
    publishing {
        publications {
            register<MavenPublication>("release") {
                afterEvaluate {
                    from(components["release"])
                }
            }
        }
        repositories.maven {
            val ciApiUrl = System.getenv("CI_API_V4_URL")
            val projectId = System.getenv("CI_PROJECT_ID")
            url = uri("$ciApiUrl/projects/$projectId/packages/maven")
            credentials(HttpHeaderCredentials::class) {
                name = "Job-Token"
                value = System.getenv("CI_JOB_TOKEN")
            }
            authentication {
                create<HttpHeaderAuthentication>("header")
            }
        }
    }
}

fun getVersionName(baseVersion: String): String {
    val branchName = getCurrentBranchNameOrLocal()
    val postfix = if (branchName == "master") "" else ".${getBuildNumber()}-$branchName"
    return "$baseVersion$postfix"
}

fun getBuildNumber(): String {
    return System.getenv("CI_PIPELINE_ID") ?: "snapshot"
}

fun getCurrentBranchNameOrLocal(): String {
    return System.getenv("CI_COMMIT_REF_NAME") ?: "local"
}

tasks.withType(Test::class) {
    testLogging {
        setExceptionFormat("full")
    }

    addTestListener(TestListenerImpl())

    jvmArgs = listOf("--add-opens=java.base/java.util=ALL-UNNAMED")
}

dependencies {
    // Kotlin
    implementation(platform(libs.kotlin.bom))
    implementation(libs.bundles.kotlin.bom)

    // prebid
    implementation(libs.bundles.prebid)

    // GMS
    implementation(libs.gms.ads)

    // Frameworks
    implementation(libs.bundles.multithreading)
    implementation(libs.bundles.data)

    // Storage
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)

    // Network
    implementation(libs.bundles.network)

    // DI
    implementation(libs.dagger.runtime)
    ksp(libs.dagger.compiler)

    // Test
    testImplementation(libs.bundles.test)

    // Lint
    detektPlugins(libs.detekt.formatting)
}