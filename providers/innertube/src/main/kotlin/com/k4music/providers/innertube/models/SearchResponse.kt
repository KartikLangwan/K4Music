package com.k4music.providers.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class SearchResponse(
    val contents: Contents?
) {
    @Serializable
    data class Contents(
        val tabbedSearchResultsRenderer: Tabs?
    )
}
