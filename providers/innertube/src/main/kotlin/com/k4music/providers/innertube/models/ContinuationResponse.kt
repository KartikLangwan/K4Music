package com.k4music.providers.innertube.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ContinuationResponse(
    val continuationContents: ContinuationContents?,
    val onResponseReceivedActions: List<ResponseAction>? = null
) {
    /** Newer responses deliver extra list items here instead of in [continuationContents]. */
    @Serializable
    data class ResponseAction(val appendContinuationItemsAction: AppendAction?) {
        @Serializable
        data class AppendAction(val continuationItems: List<MusicShelfRenderer.Content>?)
    }

    @Serializable
    data class ContinuationContents(
        @JsonNames("musicPlaylistShelfContinuation")
        val musicShelfContinuation: MusicShelfRenderer?,
        val playlistPanelContinuation: NextResponse.MusicQueueRenderer.Content.PlaylistPanelRenderer?
    )
}
