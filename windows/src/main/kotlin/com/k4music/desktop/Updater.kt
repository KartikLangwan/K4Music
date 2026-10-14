package com.k4music.desktop

import com.k4music.desktop.ui.screens.compareVersions
import com.k4music.providers.github.GitHub
import com.k4music.providers.github.requests.releases
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * In-app updates: finds the newest K4 Music release, downloads its Windows installer and starts
 * it. The installer then shows "Updating K4 Music", closes this app properly and starts it again.
 * A release without a Windows installer leads to the website instead.
 */
object Updater {
    /** A newer release; [installerUrl] is null when it has no Windows installer yet. */
    class Update(val version: String, val notes: String?, val installerUrl: String?, val installerSize: Long)

    val currentVersion: String get() = System.getProperty("jpackage.app-version") ?: "2.12.14"

    private val installerName = Regex("K4Music-Setup-.*\\.exe", RegexOption.IGNORE_CASE)

    /** The newest release if it is newer than this app; null when up to date. Throws when offline. */
    suspend fun check(): Update? = withContext(Dispatchers.IO) {
        val releases = GitHub.releases(owner = "KartikLangwan", repo = "K4Music")?.getOrThrow().orEmpty()
        val latest = releases.filter { !it.draft && !it.preRelease }
            .maxWithOrNull { a, b -> compareVersions(a.tag.trimStart('v', 'V'), b.tag.trimStart('v', 'V')) }
            ?: return@withContext null
        val version = latest.tag.trimStart('v', 'V')
        if (compareVersions(version, currentVersion) <= 0) return@withContext null
        val asset = latest.assets.firstOrNull { installerName.matches(it.name) }
        Update(version, latest.markdown?.take(1_500), asset?.downloadUrl?.toString(), asset?.size?.toLong() ?: -1L)
    }

    /** Downloads the installer (resuming nothing, overwriting a stale copy), reporting 0..1 progress. */
    suspend fun download(update: Update, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val url = requireNotNull(update.installerUrl)
        val dir = AppPaths.local.resolve("updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // only ever keep the one being installed
        val target = dir.resolve("K4Music-Setup-${update.version}.exe")
        val part = File(target.path + ".part")
        val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(20)).build()
        val response = client.send(HttpRequest.newBuilder(URI(url)).GET().build(), HttpResponse.BodyHandlers.ofInputStream())
        check(response.statusCode() == 200) { "Download failed (HTTP ${response.statusCode()})" }
        val total = response.headers().firstValueAsLong("Content-Length").orElse(update.installerSize)
        response.body().use { input ->
            part.outputStream().use { out ->
                val buffer = ByteArray(256 * 1024)
                var done = 0L
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    done += n
                    if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }
        check(total <= 0 || part.length() == total) { "Download was cut off" }
        check(part.renameTo(target)) { "Could not save the installer" }
        target
    }

    /** Starts the installer; it closes this app itself (see the installer's PrepareToInstall). */
    fun install(installer: File) {
        // /FROMAPP=1: the user already chose "Update now", so Setup closes the app without asking.
        ProcessBuilder(installer.absolutePath, "/FROMAPP=1").start()
        AppLog.i("Updater", "Started ${installer.name}")
    }
}
