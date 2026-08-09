package com.sevis.photos

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** iOS mirror of MainActivity's buildKtorClient() — same config, Darwin engine
 *  instead of Android's, since NSURLSession (Darwin) is what Ktor uses on iOS. */
fun buildKtorClient(): HttpClient = HttpClient(Darwin) {
    expectSuccess = true
    install(HttpTimeout) {
        // 15s was too tight for two real request shapes this client makes: a large
        // HEIC/video upload over a slow connection, and POST .../faces/scan, which
        // processes its whole batch synchronously server-side (decrypt + a face-service
        // round trip per photo) before responding at all — both timed out in practice.
        // Connect timeout stays tight; it's the request itself that needs the room.
        requestTimeoutMillis = 60_000
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 60_000
    }
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true; isLenient = true })
    }
    install(createClientPlugin("DynamicAuth") {
        onRequest { request, _ ->
            AppState.token?.let {
                request.headers.append(HttpHeaders.Authorization, "Bearer $it")
            }
        }
    })
}
