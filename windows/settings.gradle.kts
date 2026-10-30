@file:Suppress("UnstableApiUsage")

// K4 Audio for Windows: a separate Gradle build that reuses the Android app's
// pure-JVM music-source modules in place (../providers, ../ktor-client-brotli).
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

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
    }
    versionCatalogs {
        create("libs") { from(files("../gradle/libs.versions.toml")) }
    }
}

rootProject.name = "K4Audio"

// Same project paths as the Android build, so the modules' `projects.*` accessors resolve unchanged.
listOf(
    "ktor-client-brotli",
    "providers/common",
    "providers/github",
    "providers/innertube",
    "providers/kugou",
    "providers/lrclib",
    "providers/sponsorblock",
    "providers/sync",
    "providers/translate"
).forEach { path ->
    val name = ":" + path.replace('/', ':')
    include(name)
    project(name).projectDir = file("../$path")
}
project(":providers").projectDir = file("../providers")
