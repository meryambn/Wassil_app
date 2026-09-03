val localProperties = java.util.Properties().apply {
    val localFile = file("local.properties")
    if (localFile.exists()) {
        localFile.inputStream().use { load(it) }
    }
}
val mapboxDownloadsToken = (localProperties.getProperty("MAPBOX_DOWNLOADS_TOKEN")
    ?: System.getenv("MAPBOX_DOWNLOADS_TOKEN"))
    ?: throw GradleException(
        "Missing MAPBOX_DOWNLOADS_TOKEN. Add it to local.properties (gitignored) " +
            "or set it as an environment variable."
    )

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Mapbox Maven repository
        maven {
            url = uri("https://api.mapbox.com/downloads/v2/releases/maven")
            credentials.username = "mapbox"
            credentials.password = mapboxDownloadsToken
            authentication { create<BasicAuthentication>("basic") }
        }
    }
}

rootProject.name = "Wassil app"
include(":app")
 