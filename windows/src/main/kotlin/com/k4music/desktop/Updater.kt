package com.k4music.desktop

import com.k4music.providers.utils.useCancellable

import com.k4music.desktop.ui.screens.compareVersions
import com.k4music.providers.github.GitHub
import com.k4music.providers.github.requests.releases
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.security.MessageDigest
import kotlinx.coroutines.ensureActive

/**
 * In-app updates: finds the newest K4 Music release, downloads its Windows installer and starts
 * it. The installer then shows "Updating K4 Music", closes this app properly and starts it again.
 * A release without a Windows installer leads to the website instead.
 */
object Updater {
    /** A newer release; [installerUrl] is null when it has no Windows installer yet. */
    class Update(val version: String, val notes: String?, val installerUrl: String?, val installerSize: Long, val digest: String? = null)

    val currentVersion: String get() = System.getProperty("jpackage.app-version") ?: "2.14.26"

    private val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS).callTimeout(10, TimeUnit.MINUTES).build()
    private val verified = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val installerName = Regex("K4Music-Setup-.*\\.exe", RegexOption.IGNORE_CASE)

    /** The newest release if it is newer than this app; null when up to date. Throws when offline. */
    suspend fun check(): Update? = withContext(Dispatchers.IO) {
        // Releases of the public K4Music repo.
        val releases = GitHub.releases(owner = "KartikLangwan", repo = "K4Music")?.getOrThrow().orEmpty()
        val latest = releases.filter { !it.draft && !it.preRelease }
            .maxWithOrNull { a, b -> compareVersions(a.tag.trimStart('v', 'V'), b.tag.trimStart('v', 'V')) }
            ?: return@withContext null
        val version = latest.tag.trimStart('v', 'V')
        if (compareVersions(version, currentVersion) <= 0) return@withContext null
        val asset = latest.assets.firstOrNull { installerName.matches(it.name) }
        val digest = asset?.digest?.takeIf { it.matches(Regex("sha256:[0-9a-fA-F]{64}")) }
        // Releases without a verifiable digest remain available through the website.
        Update(version, latest.markdown?.take(1_500), asset?.downloadUrl?.toString()?.takeIf { digest != null }, asset?.size ?: -1L, digest)
    }

    /** Downloads the installer (resuming nothing, overwriting a stale copy), reporting 0..1 progress. */
    suspend fun download(update: Update, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val url = URI(requireNotNull(update.installerUrl))
        require(url.scheme == "https" && url.host.equals("github.com", true) &&
            url.path.startsWith("/KartikLangwan/K4Music/releases/download/", true)) { "Unexpected update source" }
        require(update.version.matches(Regex("[0-9]+(?:\\.[0-9]+){1,3}"))) { "Invalid update version" }
        val expected = requireNotNull(update.digest).removePrefix("sha256:")
        require(expected.matches(Regex("[0-9a-fA-F]{64}"))) { "Missing update checksum" }
        val dir = AppPaths.local.resolve("updates").apply { mkdirs() }
        val target = dir.resolve("K4Music-Setup-${update.version}.exe")
        val part = File.createTempFile("installer-", ".part", dir)
        try {
            client.newCall(Request.Builder().url(url.toString()).build()).useCancellable { response ->
                check(response.isSuccessful) { "Download failed (HTTP ${response.code})" }
                val total = update.installerSize.takeIf { it > 0 } ?: response.body.contentLength()
                val hash = MessageDigest.getInstance("SHA-256")
                var done = 0L
                response.body.byteStream().use { input ->
                    java.io.FileOutputStream(part).use { out ->
                        val buffer = ByteArray(256 * 1024)
                        while (true) {
                            kotlin.coroutines.coroutineContext.ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            out.write(buffer, 0, count)
                            hash.update(buffer, 0, count)
                            done += count
                            check(total <= 0 || done <= total) { "Unexpected update size" }
                            if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                        }
                        out.fd.sync()
                    }
                }
                check(done > 0 && (total <= 0 || done == total)) { "Download was cut off" }
                val actual = hash.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
                check(actual.equals(expected, true)) { "Update checksum mismatch" }
            }
            kotlin.coroutines.coroutineContext.ensureActive()
            java.nio.file.Files.move(part.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            verified[target.canonicalPath] = expected
            target
        } finally { part.delete() }
    }

    /** Starts the installer; it closes this app itself (see the installer's PrepareToInstall). */
    suspend fun install(installer: File) = withContext(Dispatchers.IO) {
        // /FROMAPP=1: the user already chose "Update now", so Setup closes the app without asking.
        val expected = requireNotNull(verified[installer.canonicalPath]) { "Download and verify the update first" }
        val hash = MessageDigest.getInstance("SHA-256")
        installer.inputStream().use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) { val count = input.read(buffer); if (count < 0) break; hash.update(buffer, 0, count) }
        }
        val actual = hash.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
        check(actual.equals(expected, true)) { "Update changed after verification" }
        ProcessBuilder(installer.absolutePath, "/FROMAPP=1").start()
        AppLog.i("Updater", "Started ${installer.name}")
    }
}
