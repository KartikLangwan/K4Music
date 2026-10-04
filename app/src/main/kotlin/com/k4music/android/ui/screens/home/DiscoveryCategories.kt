package com.k4music.android.ui.screens.home

import androidx.annotation.StringRes
import com.k4music.app.R
import com.k4music.providers.innertube.Innertube

internal data class DiscoveryCategory(@StringRes val title: Int, @StringRes val subtitle: Int, val query: String, val color: Long)

internal val indianCategories = listOf(
    DiscoveryCategory(R.string.genre_bollywood, R.string.genre_hindi_description, "Bollywood Hindi hit songs", 0xFFECA5A8),
    DiscoveryCategory(R.string.genre_punjabi, R.string.genre_punjabi_description, "Punjabi hit songs", 0xFFA7BFEF),
    DiscoveryCategory(R.string.genre_south, R.string.genre_south_description, "Tamil Telugu Malayalam hit songs", 0xFFA5DCC2),
    DiscoveryCategory(R.string.genre_indian_indie, R.string.genre_indie_description, "Indian indie music", 0xFFD6B3ED)
)

internal val globalCategories = listOf(
    DiscoveryCategory(R.string.genre_pop, R.string.genre_pop_description, "Pop hit songs", 0xFFE8BF84),
    DiscoveryCategory(R.string.genre_rock, R.string.genre_rock_description, "Rock essentials songs", 0xFF9FBDE7),
    DiscoveryCategory(R.string.genre_hiphop, R.string.genre_hiphop_description, "Hip hop hit songs", 0xFFD1ACDB),
    DiscoveryCategory(R.string.genre_lofi, R.string.genre_lofi_description, "Lofi chill songs", 0xFF9AD0C9)
)

/** Stable ordering keeps useful genres visible even when the provider changes its shelves. */
internal fun discoverMoodPriority(title: String): Int {
    val name = title.lowercase(java.util.Locale.ROOT)
    return when {
        listOf("indian", "bollywood", "hindi", "punjabi", "tamil", "telugu").any(name::contains) -> 0
        listOf("pop", "rock", "hip", "indie", "lo-fi", "lofi", "chill").any(name::contains) -> 1
        else -> 2
    }
}

internal fun visibleDiscoverMoods(moods: List<Innertube.Mood.Item>) = moods
    .filter { !it.endpoint.browseId.isNullOrBlank() }
    .distinctBy { it.key }
    .sortedBy { discoverMoodPriority(it.title) }
