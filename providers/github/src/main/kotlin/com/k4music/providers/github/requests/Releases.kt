package com.k4music.providers.github.requests

import com.k4music.providers.github.GitHub
import com.k4music.providers.github.models.Release
import com.k4music.providers.utils.runCatchingCancellable
import io.ktor.client.call.body
import io.ktor.client.request.get

suspend fun GitHub.releases(
    owner: String,
    repo: String,
    page: Int = 1,
    pageSize: Int = 30
) = runCatchingCancellable {
    httpClient.get("repos/$owner/$repo/releases") {
        withPagination(page = page, size = pageSize)
    }.body<List<Release>>()
}
