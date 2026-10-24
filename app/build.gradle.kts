import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

abstract class PackageQuickJs : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val binaries: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun packageBinaries() {
        val perAbi = binaries.files.groupBy { it.parentFile.name }
        check("arm64-v8a" in perAbi.keys) {
            "QuickJS binaries are missing. Build the native targets before packaging."
        }
        perAbi.forEach { (abi, candidates) ->
            val target = outputDirectory.file("$abi/libqjs.so").get().asFile
            target.parentFile.mkdirs()
            candidates.maxBy { it.lastModified() }.copyTo(target, overwrite = true)
        }
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.chaquo)
}

// Development signing must be an explicit local choice, never an accidental production fallback.
val allowDevelopmentSigning = providers.gradleProperty("allowDevelopmentSigning").map(String::toBoolean).orElse(false)

android {
    // Merged identity: applicationId/namespace define the Play identity.
    // All Kotlin packages were renamed to com.k4music.* in the same pass.
    namespace = "com.k4music.app"
    compileSdk = 37

    // Shipped builds are arm64 only (every modern phone; x86 Chromebooks translate ARM): x86_64 adds
    // ~13 MB of Python/QuickJS natives. Debug keeps it for the emulator.
    val shipping = gradle.startParameter.taskNames.any { "Release" in it || "Nightly" in it }
    val abis = if (shipping) listOf("arm64-v8a") else listOf("arm64-v8a", "x86_64")
    val cmakeVersion = "4.1.2"
    ndkVersion = "29.0.14206865"

    defaultConfig {
        applicationId = "com.k4music.app"

        minSdk = 24
        targetSdk = 37

        versionCode = System.getenv("ANDROID_VERSION_CODE")?.toIntOrNull() ?: 2142600
        versionName = project.version.toString()

        multiDexEnabled = true

        ndk {
            //noinspection ChromeOsAbiSupport
            abiFilters += abis
        }

        @Suppress("UnstableApiUsage")
        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=c++_static")
            }
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            // Merged release: one universal APK instead of per-ABI splits.
            isUniversalApk = true
        }
    }

    signingConfigs {
        create("ci") {
            storeFile = System.getenv("ANDROID_NIGHTLY_KEYSTORE")?.let { file(it) }
            storePassword = System.getenv("ANDROID_NIGHTLY_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("ANDROID_NIGHTLY_KEYSTORE_ALIAS")
            keyPassword = System.getenv("ANDROID_NIGHTLY_KEYSTORE_PASSWORD")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
            manifestPlaceholders["appName"] = "K4 Music Debug"
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Production builds require an explicitly configured signing identity.
            signingConfig = signingConfigs.findByName("release")?.takeIf { it.storeFile?.isFile == true }
                ?: signingConfigs.findByName("ci")?.takeIf { it.storeFile?.isFile == true }
                ?: signingConfigs.getByName("debug").takeIf { allowDevelopmentSigning.get() }
            manifestPlaceholders["appName"] = "K4 Music"
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        create("nightly") {
            initWith(getByName("release"))
            matchingFallbacks += "release"

            applicationIdSuffix = ".nightly"
            versionNameSuffix = "-NIGHTLY"
            manifestPlaceholders["appName"] = "K4 Music Nightly"
            signingConfig = signingConfigs.findByName("ci")?.takeIf { it.storeFile?.isFile == true }
                ?: signingConfigs.getByName("debug").takeIf { allowDevelopmentSigning.get() }
        }
    }

    buildFeatures {
        buildConfig = true
        resValues = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }

    packaging {
        resources.excludes.add("META-INF/**/*")
        jniLibs.useLegacyPackaging = true
    }

    androidResources {
        @Suppress("UnstableApiUsage")
        generateLocaleConfig = true
    }

    externalNativeBuild {
        cmake {
            version = cmakeVersion
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            // android.util.Log calls appear in crash/DB failure paths; returning
            // defaults keeps those paths testable instead of throwing.
            isReturnDefaultValues = true
        }
    }

    // QuickJS is generated per variant, without reusing a previous build's executable.
    sourceSets.getByName("main").jniLibs.setSrcDirs(emptyList<String>())

    lint {
        // Existing locales are partial; Android intentionally falls back to English.
        // Credential Manager is only used for optional sign-ins; no first-party website asset links.
        warning += setOf("MissingTranslation", "CredManMissingDal")
    }
}

androidComponents.onVariants { variant ->
    val capitalized = variant.name.replaceFirstChar { it.uppercase() }
    val nativeBuildType = if (variant.buildType == "debug") "Debug" else "RelWithDebInfo"
    val packageQuickJs = tasks.register<PackageQuickJs>("package${capitalized}QuickJs") {
        dependsOn("externalNativeBuild$capitalized")
        binaries.from(fileTree(layout.buildDirectory.dir("intermediates/cxx/$nativeBuildType")) {
            include("**/obj/*/qjs")
        })
        outputDirectory.set(layout.buildDirectory.dir("generated/quickjs/${variant.name}"))
    }
    variant.sources.jniLibs?.addGeneratedSourceDirectory(packageQuickJs, PackageQuickJs::outputDirectory)
}

kotlin {
    jvmToolchain(libs.versions.jvm.get().toInt())

    compilerOptions {
        languageVersion.set(KotlinVersion.KOTLIN_2_5)

        freeCompilerArgs.addAll(
            "-Xconsistent-data-class-copy-visibility"
        )
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

composeCompiler {
    if (project.findProperty("enableComposeCompilerReports") == "true") {
        val dest = layout.buildDirectory.dir("compose_metrics")
        metricsDestination = dest
        reportsDestination = dest
    }
}

chaquopy {
    defaultConfig {
        version = "3.14"
        pip {
            install("yt-dlp>=2026.08.19")
            install("yt-dlp-ejs>=0.8.0")
            install("pip")
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugaring)

    implementation(projects.compose.persist)
    implementation(projects.compose.preferences)
    implementation(projects.compose.routing)
    implementation(projects.compose.reordering)

    implementation(fileTree(projectDir.resolve("vendor")))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.activity)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.util)
    implementation(libs.compose.shimmer)
    implementation(libs.compose.lottie)
    implementation(libs.compose.material3)

    implementation(libs.coil.compose)
    implementation(libs.coil.ktor)

    implementation(libs.palette)
    implementation(libs.monet)
    runtimeOnly(projects.core.materialCompat)

    implementation(libs.exoplayer)
    implementation(libs.exoplayer.workmanager)
    implementation(libs.media3.session)
    implementation(libs.media)

    implementation(libs.workmanager)
    implementation(libs.workmanager.ktx)

    implementation(libs.credentials)
    implementation(libs.credentials.play)

    implementation(libs.kotlin.coroutines)
    implementation(libs.kotlin.immutable)
    implementation(libs.kotlin.datetime)

    // K4 merge: JioSaavn search/resolve + crash-safe downloader use OkHttp.
    implementation(libs.okhttp)

    implementation(libs.room)
    ksp(libs.room.compiler)

    implementation(libs.log4j)
    implementation(libs.slf4j)
    implementation(libs.logback)

    testImplementation(libs.junit)

    implementation(projects.providers.github)
    implementation(projects.providers.common)
    implementation(projects.providers.innertube)
    implementation(projects.providers.kugou)
    implementation(projects.providers.lrclib)
    implementation(projects.providers.sync)
    implementation(projects.providers.sponsorblock)
    implementation(projects.providers.translate)
    implementation(projects.core.data)
    implementation(projects.core.ui)
}

listOf("release", "nightly").forEach { variant ->
    val suffix = variant.replaceFirstChar { it.uppercase() }
    val verifySigning = tasks.register("verify${suffix}Signing") {
        group = "verification"
        description = "Rejects missing or accidental debug signing for $variant distributions"
        doLast {
            val signing = android.buildTypes.getByName(variant).signingConfig
            check(signing != null && signing.storeFile?.isFile == true &&
                !signing.keyAlias.isNullOrBlank() && signing.storePassword != null && signing.keyPassword != null &&
                (signing.name != "debug" || allowDevelopmentSigning.get())) {
                "Production signing is not configured for $variant. Configure the CI keystore credentials, or use -PallowDevelopmentSigning=true only for local development builds."
            }
        }
    }
    tasks.matching { it.name == "pre${suffix}Build" }.configureEach { dependsOn(verifySigning) }
}
