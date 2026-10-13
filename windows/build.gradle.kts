import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.security.MessageDigest

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    id("org.jetbrains.compose") version "1.12.1"
}

val appVersion = "2.12.14"
group = "com.k4music"
version = appVersion

// The shared modules build into windows/build/shared, never into the Android build's folders.
subprojects {
    layout.buildDirectory.set(rootProject.layout.buildDirectory.dir("shared/${path.trim(':').replace(':', '-')}"))
}

kotlin {
    jvmToolchain(libs.versions.jvm.get().toInt())
}

dependencies {
    implementation(projects.providers.common)
    implementation(projects.providers.github)
    implementation(projects.providers.innertube)
    implementation(projects.providers.kugou)
    implementation(projects.providers.lrclib)
    implementation(projects.providers.piped)
    implementation(projects.providers.sponsorblock)
    implementation(projects.providers.translate)

    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.components.resources)

    implementation(libs.kotlin.coroutines)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")
    implementation(libs.kotlin.immutable)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.coil.ktor)
    implementation("net.java.dev.jna:jna:5.17.0")

    testImplementation(kotlin("test"))
}

// ---- Android strings, icons and fonts, reused at build time (one source of truth) ----

val androidRes = layout.projectDirectory.dir("../app/src/main/res")
val syncAndroidResources by tasks.registering(Sync::class) {
    description = "Copies the Android app's strings, vector icons and fonts into Compose resources"
    into(layout.buildDirectory.dir("generated/k4res"))
    // Strings: every language Compose understands (b+ script qualifiers and -night/-v31 are Android-only).
    from(androidRes) {
        include("values/*.xml", "values-*/*.xml")
        exclude("values-b+*/**", "values-night*/**", "values-v*/**", "**/colors.xml", "**/themes.xml")
    }
    from(androidRes.dir("drawable")) {
        include("*.xml", "*.png")
        exclude("splash_background.xml") // a layer-list, not a vector
        into("drawable")
    }
    from(androidRes.dir("font")) { into("font") }
}

compose.resources {
    packageOfResClass = "com.k4music.desktop.res"
    generateResClass = always
    customDirectory("main", layout.dir(syncAndroidResources.map { it.destinationDir }))
}

// ---- Native tools bundled next to the app: libmpv, yt-dlp, QuickJS ----

val toolsDir = layout.projectDirectory.dir("downloads")
val pinnedTools = mapOf(
    "yt-dlp.exe" to "66674953fe251b89f4d08c5f0e35e0728679bd67ab3d7d05c0562af101dd3e7a",
    "qjs-windows-x86_64.exe" to "2aeabf0092c3262d6b2609824418f7dd7ed1f1df939f73b2b15645230cac0d77"
)

val verifyNativeTools by tasks.registering {
    description = "Fails unless the pinned native tools are present in windows/downloads"
    inputs.files(pinnedTools.keys.map { toolsDir.file(it) } + toolsDir.file("mpv/libmpv-2.dll"))
    doLast {
        pinnedTools.forEach { (name, sha) ->
            val file = toolsDir.file(name).asFile
            check(file.isFile) { "Missing $file — see windows/README.md (Native tools)" }
            val actual = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
            check(actual == sha) { "$name checksum mismatch: $actual" }
        }
        check(toolsDir.file("mpv/libmpv-2.dll").asFile.isFile) { "Missing libmpv-2.dll — see windows/README.md" }
    }
}

val stageNativeTools by tasks.registering(Sync::class) {
    dependsOn(verifyNativeTools)
    into(layout.buildDirectory.dir("appResources"))
    from(toolsDir.file("mpv/libmpv-2.dll")) { into("windows-x64") }
    from(toolsDir.file("yt-dlp.exe")) { into("windows-x64") }
    from(toolsDir.file("qjs-windows-x86_64.exe")) { into("windows-x64"); rename { "qjs.exe" } }
}

compose.desktop {
    application {
        mainClass = "com.k4music.desktop.MainKt"
        jvmArgs += listOf("-Dfile.encoding=UTF-8", "-Xss2m")

        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "K4 Music"
            packageVersion = appVersion
            description = "K4 Music"
            vendor = "Kartik Langwan"
            copyright = "© 2026 Kartik Langwan. GPL-3.0."
            licenseFile.set(layout.projectDirectory.file("../LICENSE"))
            appResourcesRootDir.set(layout.buildDirectory.dir("appResources"))
            modules(
                "java.instrument", "java.management", "java.naming", "java.net.http", "java.prefs",
                "java.sql", "jdk.crypto.ec", "jdk.unsupported", "jdk.zipfs"
            )
            windows {
                iconFile.set(layout.projectDirectory.file("icons/k4music.ico"))
                menuGroup = "K4 Music"
                upgradeUuid = "6F1D2B7A-4E3C-4A8B-9C21-5D7E3A9B1C04"
            }
        }
    }
}

tasks.matching { it.name == "prepareAppResources" }.configureEach { dependsOn(stageNativeTools) }

// Development runs keep every file inside the project: app data, temp, Skia and JNA natives.
tasks.withType<JavaExec>().configureEach {
    val dev = layout.buildDirectory.dir("dev-data").get().asFile
    systemProperty("k4.dataDir", dev.absolutePath)
    systemProperty("java.io.tmpdir", dev.resolve("tmp").absolutePath)
    systemProperty("skiko.data.path", dev.resolve("skiko").absolutePath)
    systemProperty("jna.tmpdir", dev.resolve("tmp").absolutePath)
    doFirst { dev.resolve("tmp").mkdirs() }
}

// ---- Installer: app image (createDistributable) + Inno Setup -> one K4Music-Setup-x.y.z.exe ----

val packageInstaller by tasks.registering(Exec::class) {
    description = "Builds K4Music-Setup-$appVersion.exe (installer + uninstaller) with portable Inno Setup"
    group = "distribution"
    dependsOn("createDistributable")
    val iscc = toolsDir.file("innosetup/ISCC.exe").asFile
    val script = layout.projectDirectory.file("installer/K4Music.iss").asFile
    val appImage = layout.buildDirectory.dir("compose/binaries/main/app/K4 Music").get().asFile
    val out = layout.buildDirectory.dir("installer").get().asFile
    inputs.dir(appImage)
    inputs.file(script)
    outputs.dir(out)
    doFirst {
        check(iscc.isFile) { "Missing $iscc — see windows/README.md (Native tools)" }
        out.mkdirs()
    }
    commandLine(
        iscc.absolutePath, "/Q",
        "/DAppVersion=$appVersion",
        "/DAppImage=${appImage.absolutePath}",
        "/DOutDir=${out.absolutePath}",
        "/DRepoRoot=${layout.projectDirectory.dir("..").asFile.canonicalPath}",
        script.absolutePath
    )
}
