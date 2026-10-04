package com.k4music.providers.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class Thumbnail(
    val url: String,
    val height: Int?,
    val width: Int?
) {
    fun size(size: Int): String {
        val cleanSize = size.coerceAtLeast(300)
        val base = if (url.contains("=")) url.substringBefore("=") else url
        return when {
            url.startsWith("https://lh3.googleusercontent.com") ||
                url.startsWith("https://yt3.googleusercontent.com") -> "$base=w$cleanSize-h$cleanSize-l90-rj"
            url.startsWith("https://yt3.ggpht.com") -> "$base=s$cleanSize-c-k-c0x00ffffff-no-rj"
            url.contains("saavncdn.com") -> url.replace(Regex("-\\d+x\\d+\\.jpg"), "-500x500.jpg")
            url.contains("i.ytimg.com") -> url.replace("/default.jpg", "/hqdefault.jpg").replace("/mqdefault.jpg", "/hqdefault.jpg")
            else -> url
        }
    }
}
