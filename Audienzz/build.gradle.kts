import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.SonatypeHost

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.allopen)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.dokka)
    `maven-publish`
    `signing`
}

ksp {
    arg(RoomSchemaArgProvider(File(projectDir, "schemas")))
}

android {
    compileSdk = libs.versions.sdk.compile.get().toInt()
    buildToolsVersion = libs.versions.build.tools.version.get()
    version = "0.1.1"

    defaultConfig {
        namespace = "org.audienzz"
        minSdk = libs.versions.sdk.min.get().toInt()
        buildConfigField(
            "String",
            "EVENTS_BASE_URL",
            "\"https://api.adnz.co/api/ws-clickstream-collector/\"",
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

configurations.configureEach {
    exclude(group = "com.applovin")
}

afterEvaluate {
    publishing {
        repositories.maven {
            name = "Gitlab"
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

fun getBuildNumber(): String =
    System.getenv("CI_PIPELINE_ID") ?: "snapshot"

fun getCurrentBranchNameOrLocal(): String =
    System.getenv("CI_COMMIT_REF_NAME") ?: "local"

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
    implementation(libs.room.ktx)
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

    implementation(libs.core.ktx)
}

signing {
    useGpgCmd()

    // sign(publishing.publications.named("maven").get())
}

mavenPublishing {
    configure(AndroidSingleVariantLibrary())
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, automaticRelease = true)
    signAllPublications()
    coordinates("com.audienzz", "sdk", version.toString())
    pom {
        name.set("Audienzz")
        description.set("Android implementation of the Audienzz SDK")
        inceptionYear.set("2025")
        url.set("https://github.com/audienzz/audienzz-android-sdk/blob/main/README.md")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("mirko.mikulic@audienzz.ch")
                name.set("Mirko Mikulic")
                url.set("https://github.com/audienzz")
            }
        }
        scm {
            connection.set("scm:git:git://github.com/audienzz/audienzz-android-sdk.git")
            developerConnection.set("scm:git:ssh://github.com/audienzz/audienzz-android-sdk.git")
            url.set("https://github.com/audienzz/audienzz-android-sdk")
        }
    }
}
