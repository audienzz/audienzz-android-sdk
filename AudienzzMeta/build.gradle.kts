import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.SonatypeHost

/*
 * Optional Meta Audience Network mediation for the Audienzz SDK, published as
 * `com.audienzz:sdk-meta`. Only publishers that sell to Meta add it; everyone else keeps Meta's SDK
 * out of their app.
 *
 * It ships no ad code of its own: Google Mobile Ads discovers the Meta adapter on the classpath and
 * Ad Manager (SDK Bidding yield groups) decides when Meta bids. The core SDK detects the adapter at
 * runtime and sends `au_meta=1`, which the yield group targets — see the README.
 */
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.maven.publish)
    `maven-publish`
    `signing`
}

// Released together with the core SDK, under the same version.
evaluationDependsOn(":Audienzz")
version = project(":Audienzz").version

android {
    namespace = "org.audienzz.mobile.meta"
    compileSdk = libs.versions.sdk.compile.get().toInt()
    buildToolsVersion = libs.versions.build.tools.version.get()

    defaultConfig {
        minSdk = libs.versions.sdk.min.get().toInt()
    }

    compileOptions {
        targetCompatibility(libs.versions.jvm.target.asProvider().get().toInt())
        sourceCompatibility(libs.versions.jvm.target.asProvider().get().toInt())
    }

    kotlinOptions {
        jvmTarget = libs.versions.jvm.target.kotlin.get()
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(platform(libs.kotlin.bom))
    implementation(libs.bundles.kotlin.bom)

    // `api` so the app can call Meta's AdSettings directly for anything not wrapped here.
    api(libs.gms.mediation.meta)

    testImplementation(libs.test.junit)

    detektPlugins(libs.detekt.formatting)
}

signing {
    useGpgCmd()
}

mavenPublishing {
    configure(AndroidSingleVariantLibrary())
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, automaticRelease = true)
    signAllPublications()
    coordinates("com.audienzz", "sdk-meta", version.toString())
    pom {
        name.set("Audienzz Meta mediation")
        description.set("Optional Meta Audience Network mediation for the Audienzz Android SDK")
        inceptionYear.set("2026")
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
