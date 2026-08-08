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
        requestTimeoutMillis = 15_000
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 15_000
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
