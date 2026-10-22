package com.k4music.android.k4

import java.net.URI

const val SAAVN_CATALOG_PREFIX = "saavn:id:"
private val CATALOG_ID = Regex("[A-Za-z0-9_-]{1,128}")
private val QUALITY_SUFFIX = Regex("_(?:96|160|320)\\.(?:mp4|m4a)$", RegexOption.IGNORE_CASE)

internal fun saavnCatalogueId(key: String): String? = key.takeIf { it.startsWith(SAAVN_CATALOG_PREFIX) }
    ?.removePrefix(SAAVN_CATALOG_PREFIX)?.takeIf(CATALOG_ID::matches)

/** New catalogue results have stable identities; existing playback references keep their bytes/relations. */
fun K4Song.saavnMediaKey(): String = playbackKey ?: id.removePrefix("saavn_")
    .takeIf { id.startsWith("saavn_") && CATALOG_ID.matches(it) }
    ?.let { SAAVN_CATALOG_PREFIX + it } ?: "saavn:$contentUri"

private fun streamIdentity(url: String): String? = runCatching {
    val uri = URI(url)
    val host = uri.host?.lowercase() ?: return@runCatching null
    if (host != "saavncdn.com" && !host.endsWith(".saavncdn.com")) return@runCatching null
    val scheme = uri.scheme?.lowercase()
    if (uri.userInfo != null || scheme !in setOf("http", "https")) return@runCatching null
    if (uri.port != -1 && uri.port != (if (scheme == "https") 443 else 80)) return@runCatching null
    host + uri.rawPath.replace(QUALITY_SUFFIX, "")
}.getOrNull()

/** Only a proven matching CDN file may reuse a legacy id; metadata guesses never merge a library. */
fun preserveLegacySaavnKeys(songs: List<K4Song>, legacyIds: List<String>): List<K4Song> {
    if (legacyIds.isEmpty()) return songs
    val aliases = LinkedHashMap<String, String>()
    legacyIds.forEach { key ->
        if (key.startsWith("saavn:http")) streamIdentity(key.removePrefix("saavn:"))?.let { aliases.putIfAbsent(it, key) }
    }
    return songs.map { song -> song.copy(playbackKey = streamIdentity(song.contentUri)?.let(aliases::get)) }
}
