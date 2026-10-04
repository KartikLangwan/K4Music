package com.k4music.providers.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class MusicShelfRenderer(
    val bottomEndpoint: NavigationEndpoint?,
    val contents: List<Content>?,
    val continuations: List<Continuation>?,
    val title: Runs?
) {
    @Serializable
    data class Content(
        val musicResponsiveListItemRenderer: MusicResponsiveListItemRenderer?,
        val continuationItemRenderer: ContinuationItemRenderer? = null
    ) {
        /** Newer responses end a long list with an item that carries the next-page token. */
        @Serializable
        data class ContinuationItemRenderer(val continuationEndpoint: Endpoint?) {
            @Serializable
            data class Endpoint(val continuationCommand: Command?)

            @Serializable
            data class Command(val token: String?)
        }

        val runs: Pair<List<Runs.Run>, List<List<Runs.Run>>>
            get() = musicResponsiveListItemRenderer
                ?.flexColumns
                ?.firstOrNull()
                ?.musicResponsiveListItemFlexColumnRenderer
                ?.text
                ?.runs
                .orEmpty() to
                musicResponsiveListItemRenderer
                    ?.flexColumns
                    ?.let { it.getOrNull(1) ?: it.lastOrNull() }
                    ?.musicResponsiveListItemFlexColumnRenderer
                    ?.text
                    ?.splitBySeparator()
                    .orEmpty()

        val thumbnail: Thumbnail?
            get() = musicResponsiveListItemRenderer
                ?.thumbnail
                ?.musicThumbnailRenderer
                ?.thumbnail
                ?.thumbnails
                ?.firstOrNull()
    }
}
