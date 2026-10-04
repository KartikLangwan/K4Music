package com.k4music.providers.innertube.requests

import com.k4music.providers.innertube.Innertube
import com.k4music.providers.innertube.models.SearchSuggestionsResponse
import com.k4music.providers.innertube.models.bodies.SearchSuggestionsBody
import com.k4music.providers.utils.runCatchingCancellable
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody

suspend fun Innertube.searchSuggestions(body: SearchSuggestionsBody) = runCatchingCancellable {
    val response = client.post(SEARCH_SUGGESTIONS) {
        setBody(body)
        @Suppress("all")
        mask(
            "contents.searchSuggestionsSectionRenderer.contents.searchSuggestionRenderer.navigationEndpoint.searchEndpoint.query"
        )
    }.body<SearchSuggestionsResponse>()

    response
        .contents
        ?.firstOrNull()
        ?.searchSuggestionsSectionRenderer
        ?.contents
        ?.mapNotNull { content ->
            content
                .searchSuggestionRenderer
                ?.navigationEndpoint
                ?.searchEndpoint
                ?.query
        }
}
