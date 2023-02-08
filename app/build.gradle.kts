import org.jetbrains.kotlin.util.capitalizeDecapitalize.capitalizeAsciiOnly
import java.util.Properties

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    alias(libs.plugins.com.android.application)
    alias(libs.plugins.org.jetbrains.kotlin.android)
    alias(libs.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(libs.plugins.org.jetbrains.kotlin.kapt)
    alias(libs.plugins.com.google.dagger.hilt.android)
    alias(libs.plugins.com.squareup.sqldelight)
    alias(libs.plugins.com.google.gms.googleservices)
    alias(libs.plugins.com.google.firebase.crashlytics)

    alias(libs.plugins.jk1.dependencylicensereport)
}

val secrets = File(rootProject.projectDir, "secrets.properties")
    .inputStream()
    .use { inputStream -> Properties().also { it.load(inputStream) } }

open class CopyApkTask : DefaultTask() {
    @get:InputFiles
    var dir: Provider<Directory>? = null

    @get:Internal
    var builtArtifactsLoader: com.android.build.api.variant.BuiltArtifactsLoader? = null

    @get:OutputFile
    var outputFileName: String? = null

    @TaskAction
    fun copy() {
        val dir = dir!!.get()
        val builtArtifactsLoader = builtArtifactsLoader!!

        val src = builtArtifactsLoader.load(dir)!!.elements.single().outputFile.let(::File)
        val dest = File(dir.asFile, outputFileName!!)

        if (dest.exists())
            logger.warn("File ${dest.absolutePath} already exists, overwriting")

        src.copyTo(dest, overwrite = true)
    }
}

android {
    compileSdk = 33
    namespace = "de.hpi.etranslation"

    defaultConfig {
        applicationId = "de.hpi.etranslation"
        minSdk = 23
        targetSdk = 33
        versionCode = 2
        versionName = "0.1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "ES_USER",
            "\"${secrets["es.user"] as String}\"",
        )
        buildConfigField(
            "String",
            "ES_PASSWORD",
            "\"${secrets["es.password"] as String}\"",
        )
    }

    androidComponents {
        onVariants { variant ->
            val suffix = if (System.getenv("IS_SNAPSHOT") == "true")
                "-snapshot"
            else
                ""

            project.tasks.register<CopyApkTask>("copy${variant.buildType?.capitalizeAsciiOnly()}") {
                dir = variant.artifacts.get(com.android.build.api.artifact.SingleArtifact.APK)
                builtArtifactsLoader = variant.artifacts.getBuiltArtifactsLoader()
                outputFileName =
                    "${variant.applicationId.get()}-v${variant.outputs.single().versionName.get()}-${variant.name}$suffix.apk"

                dependsOn("assemble${variant.buildType?.capitalizeAsciiOnly()}")
            }
        }
    }

    buildFeatures {
        viewBinding = true
    }

    buildTypes {
        debug {
            matchingFallbacks.add("release")
        }

        release {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }
}

sqldelight {
    database("DocumentsDatabase") {
        packageName = "de.hpi.etranslation.data"
    }
}

licenseReport {
    // explore the different names with
    // $ jq -r '.dependencies[] | .moduleLicense | select(. != null)' index.json | sort | uniq
    // on the json report.
    renderers = arrayOf(com.github.jk1.license.render.JsonReportRenderer())
}

dependencies {
    coreLibraryDesugaring(libs.desugar)

    implementation(projects.libChdp)

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.core)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.hilt.work)
    kapt(libs.androidx.hilt.compiler)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.com.google.android.material)

    implementation(libs.conductor)
    implementation(libs.conductor.androidx.transition)
    implementation(libs.conductor.archlifecycle)
    implementation(libs.conductor.viewpager2)

    implementation(libs.dotsindicator)

    implementation(libs.firebase.crashlytics)

    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    implementation(libs.insetter)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.auth)
    implementation(libs.ktor.client.contentnegotiation)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.serialization.kotlinx.json)

    debugRuntimeOnly(libs.leakcanary)

    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)

    implementation(libs.result)
    implementation(libs.result.coroutines)

    implementation(libs.sqldelight.coroutines)
    implementation(libs.sqldelight.driver)
}
