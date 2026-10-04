package com.k4music.providers.sponsorblock

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import java.io.IOException

object SponsorBlock {
    internal val httpClient by lazy {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                    }
                )
            }

            defaultRequest {
                url("https://sponsor.ajay.app")

                accept(ContentType.Application.Json)
                contentType(ContentType.Application.Json)
            }

            expectSuccess = true

            // Reliability: a hung request must never spin forever, and transient
            // failures (network blip / 5xx) must retry before the UI gives up.
            install(HttpTimeout) {
                connectTimeoutMillis = 8000L
                requestTimeoutMillis = 15000L
                socketTimeoutMillis = 15000L
            }

            install(HttpRequestRetry) {
                exponentialDelay(base = 2.0, maxDelayMs = 8_000)
                retryOnServerErrors(maxRetries = 2)
                retryOnExceptionIf { _, cause -> cause is IOException }
            }
        }
    }
}
