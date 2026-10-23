import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.security.MessageDigest

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    id("org.jetbrains.compose") version "1.12.1"
}

val appVersion = "2.14.26"
group = "com.k4music"
version = appVersion

// The shared modules build into windows/build/shared, never into the Android build's folders.
subprojects {
    layout.buildDirectory.set(rootProject.layout.buildDirectory.dir("shared/${path.trim(':').replace(':', '-')}"))
}

// Android source files that are plain Kotlin compile here unchanged: the Room models, the
// recommender, the JioSaavn client and branding. Copied at build time, never duplicated in git.
val appSources = layout.projectDirectory.dir("../app/src/main/kotlin")
val syncSharedSources by tasks.registering(Sync::class) {
    description = "Copies the Android app's platform-neutral Kotlin sources into the desktop build"
    into(layout.buildDirectory.dir("generated/sharedSrc"))
    from(appSources) {
        include(
            "com/k4music/android/Brand.kt",
            "com/k4music/android/k4/K4Models.kt",
            "com/k4music/android/k4/K4Recommend.kt",
            "com/k4music/android/k4/JioSaavnApi.kt",
            "com/k4music/android/k4/SaavnKeys.kt",
            "com/k4music/android/models/*.kt",
            "com/k4music/android/playlistimport/PlaylistImport.kt",
            "com/k4music/android/playlistimport/SpotifySource.kt",
            "com/k4music/android/playlistimport/YouTubeMusicSource.kt"
        )
        exclude("com/k4music/android/models/Mood.kt", "com/k4music/android/models/QueuedMediaItem.kt")
    }
    from(layout.projectDirectory.dir("../core/data/src/main/kotlin")) {
        include("com/k4music/core/data/enums/*.kt", "com/k4music/core/data/utils/Bytes.kt")
    }
}

// Room writes Kotlin (its Java writer cannot delegate the DAO's interface default methods).
ksp { arg("room.generateKotlin", "true") }

kotlin {
    jvmToolchain(libs.versions.jvm.get().toInt())
    sourceSets.main { kotlin.srcDir(syncSharedSources) }
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
    implementation(libs.okhttp)
    implementation(libs.ktor.http)
    implementation("org.json:json:20250517")
    implementation("net.jthink:jaudiotagger:3.0.1")
    implementation("androidx.room:room-runtime:${libs.versions.room.get()}")
    implementation("androidx.sqlite:sqlite-bundled:2.6.2")
    ksp("androidx.room:room-compiler:${libs.versions.room.get()}")

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
        // Desktop vectors take literal colours only.
        filesMatching("**/*.xml") {
            filter { line -> line.replace("@android:color/white", "#FFFFFFFF").replace("@android:color/black", "#FF000000") }
        }
    }
    from(androidRes.dir("font")) { into("font") }
    // Desktop-only strings sit beside the Android ones.
    from(layout.projectDirectory.dir("src/main/desktopRes"))
}

compose.resources {
    packageOfResClass = "com.k4music.desktop.res"
    generateResClass = always
    customDirectory("main", layout.dir(syncAndroidResources.map { it.destinationDir }))
}

// ---- Native tools bundled next to the app: libmpv, yt-dlp, QuickJS ----

val toolsDir = layout.projectDirectory.dir("downloads")
val pinnedTools = mapOf(
    // yt-dlp's folder build: no self-unpacking on every call, so each lookup starts faster.
    "yt-dlp_win.zip" to "30b4c14aafab6082becff7881e41b76df46dc43ea7633479410a91e29da492bf",
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
    from(zipTree(toolsDir.file("yt-dlp_win.zip"))) { into("windows-x64/yt-dlp") }
    from(toolsDir.file("qjs-windows-x86_64.exe")) { into("windows-x64"); rename { "qjs.exe" } }
}

compose.desktop {
    application {
        mainClass = "com.k4music.desktop.MainKt"
        jvmArgs += listOf(
            "-Dfile.encoding=UTF-8", "-Xss2m",
            // A music app idles in the background for hours: give unused heap back to Windows.
            "-XX:G1PeriodicGCInterval=60000", "-XX:MinHeapFreeRatio=10", "-XX:MaxHeapFreeRatio=30"
        )

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

// jlink takes the runtime's DLLs from the jmods, which are unsigned; Smart App Control then blocks
// awt.dll / freetype.dll at random ("An Application Control policy has blocked this file") and the
// app dies at start. The same JDK's bin\ holds the identical files signed by Microsoft: use those.
val signRuntimeDlls by tasks.registering {
    description = "Replaces the app image's unsigned runtime DLLs with the JDK's signed copies"
    dependsOn("createDistributable")
    val jdk = javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(libs.versions.jvm.get().toInt())) }
        .map { it.metadata.installationPath.asFile.resolve("bin") }
    val runtimeBin = layout.buildDirectory.dir("compose/binaries/main/app/K4 Music/runtime/bin").get().asFile
    doLast {
        val signedBin = jdk.get()
        var replaced = 0
        runtimeBin.walkTopDown().filter { it.isFile && it.extension.lowercase() in setOf("dll", "exe") }.forEach { file ->
            val signed = signedBin.resolve(file.relativeTo(runtimeBin).path)
            if (signed.isFile && signed.length() != file.length()) { signed.copyTo(file, overwrite = true); replaced++ }
        }
        logger.lifecycle("Signed runtime: replaced $replaced DLLs with the JDK's signed copies")
    }
}

// The trimmed runtime ships without the JDK's class-data archive; baking one in lets the JVM
// map its core classes instead of loading them, so the window opens sooner (~2.4 s -> ~1.9 s).
val bakeCdsArchive by tasks.registering(Exec::class) {
    description = "Creates the runtime's default CDS archive (classes.jsa) in the app image"
    dependsOn("createDistributable", signRuntimeDlls)
    val appImage = layout.buildDirectory.dir("compose/binaries/main/app/K4 Music").get().asFile
    outputs.file(appImage.resolve("runtime/bin/server/classes.jsa"))
    // The launcher starts the bundled JVM, which dumps the archive and exits before main().
    commandLine(appImage.resolve("K4 Music.exe").absolutePath)
    environment("JAVA_TOOL_OPTIONS", "-Xshare:dump")
}

// Smart App Control blocks a Setup.exe that is not code-signed, while the app itself is allowed:
// this updates the K4 Music installed on this PC by copying the build over it (settings, library
// and the uninstaller stay). ponytail: per-user install folder only; signed installers make it unnecessary.
val installLocal by tasks.registering {
    description = "Installs or updates K4 Music on this PC straight from the build, without Setup.exe"
    group = "distribution"
    dependsOn(bakeCdsArchive)
    val appImage = layout.buildDirectory.dir("compose/binaries/main/app/K4 Music").get().asFile
    doLast {
        val target = File(System.getenv("LOCALAPPDATA"), "Programs/K4 Music")
        val exe = target.resolve("K4 Music.exe")
        target.mkdirs()
        fun running() = ProcessBuilder("tasklist", "/FI", "IMAGENAME eq K4 Music.exe", "/NH").start()
            .inputStream.bufferedReader().readText().contains("K4 Music.exe")
        if (running()) {
            if (exe.isFile) {
                // Close it properly (it saves the queue); older builds without --quit get ended after 10 s.
                ProcessBuilder(exe.absolutePath, "--quit").start()
                repeat(40) { if (running()) Thread.sleep(250) }
            }
            if (running()) ProcessBuilder("taskkill", "/F", "/T", "/IM", "K4 Music.exe").start().waitFor()
            Thread.sleep(1000)
        }
        appImage.copyRecursively(target, overwrite = true)
        val startMenu = File(System.getenv("APPDATA"), "Microsoft/Windows/Start Menu/Programs/K4 Music")
        startMenu.mkdirs()
        val shortcutScript = """
            ${'$'}ws = New-Object -ComObject WScript.Shell
            ${'$'}s = ${'$'}ws.CreateShortcut('${startMenu.resolve("K4 Music.lnk").absolutePath.replace("'", "''")}')
            ${'$'}s.TargetPath = '${exe.absolutePath.replace("'", "''")}'
            ${'$'}s.WorkingDirectory = '${target.absolutePath.replace("'", "''")}'
            ${'$'}s.Save()
        """.trimIndent()
        ProcessBuilder("powershell", "-NoProfile", "-Command", shortcutScript).start().waitFor()
        logger.lifecycle("Installed/Updated K4 Music in $target")
    }
}

val packageInstaller by tasks.registering(Exec::class) {
    description = "Builds K4Music-Setup-$appVersion.exe (installer + uninstaller) with portable Inno Setup"
    group = "distribution"
    dependsOn("createDistributable", bakeCdsArchive)
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

// Tests may exercise persistence, but never the installed user's library or settings.
tasks.withType<Test>().configureEach {
    val testData = layout.buildDirectory.dir("test-data").get().asFile
    systemProperty("k4.dataDir", testData.absolutePath)
    systemProperty("java.io.tmpdir", testData.resolve("tmp").absolutePath)
    doFirst { testData.resolve("tmp").mkdirs() }
}
