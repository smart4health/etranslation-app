import java.util.Properties

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

val secrets = File(rootProject.projectDir, "secrets.properties")
    .inputStream()
    .use { inputStream -> Properties().also { it.load(inputStream) } }

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()

        listOf(
            "https://maven.pkg.github.com/d4l-data4life/hc-util-sdk-kmp",
            "https://maven.pkg.github.com/d4l-data4life/hc-fhir-sdk-java",
            "https://maven.pkg.github.com/d4l-data4life/hc-fhir-helper-sdk-kmp",
        ).forEach { path ->
            maven {
                url = uri(path)
                credentials {
                    username = secrets.getProperty("gpr.user")
                        ?: System.getenv("GPR_USER")

                    password = secrets.getProperty("gpr.token")
                        ?: System.getenv("GPR_TOKEN")
                }
            }
        }
    }

    versionCatalogs {
        create("libs") {
            from(files("libs.versions.toml"))
        }
    }
}
rootProject.name = "eTranslation"
include(":app")
include(":lib-chdp")
