import java.util.Properties

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    alias(libs.plugins.com.android.library)
    alias(libs.plugins.org.jetbrains.kotlin.android)
}

fun propertyMap(filename: String): Map<String, Any> =
    File(rootProject.projectDir, filename)
        .inputStream()
        .use { inputStream -> Properties().also { it.load(inputStream) } }
        .mapKeys { it.key as String }

android {
    compileSdk = 33
    namespace = "de.hpi.etranslation.lib.chdp"

    defaultConfig {
        minSdk = 23
        targetSdk = 33
    }

    buildTypes {
        debug {
            propertyMap("debug.chdp.properties")
                .let(this::addManifestPlaceholders)
            matchingFallbacks.add("release")
        }

        release {
            propertyMap("release.chdp.properties")
                .let(this::addManifestPlaceholders)
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar)

    implementation(libs.androidx.activity)
    implementation(libs.androidx.browser)

    api(libs.d4l.android)
    api(libs.d4l.fhir)

    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.result)
    implementation(libs.result.coroutines)

    api(libs.threetenabp)
}
