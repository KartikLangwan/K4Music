plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.lint)
}

dependencies {
    implementation(libs.kotlin.coroutines)

    implementation(libs.ktor.serialization.json) // kotlinx-serialization-json, as in the other providers
}

kotlin {
    jvmToolchain(libs.versions.jvm.get().toInt())
}
