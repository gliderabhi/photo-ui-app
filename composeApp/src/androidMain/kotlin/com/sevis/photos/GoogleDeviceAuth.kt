package com.sevis.photos

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Google's OAuth 2.0 Device Authorization Grant (the same "sign in on your phone"
 * flow YouTube/Netflix TV apps use). Requires an OAuth client of type "TV and
 * Limited-Input devices" specifically — Google rejects this flow outright for
 * other client types (e.g. "Desktop"/"installed") with an invalid_client error.
 */
object GoogleDeviceAuth {

    // The web app's GSI button uses a different (Web) OAuth client; this one must
    // be the TV/Limited-Input client since only that type is accepted by Google's
    // device authorization endpoint. Sourced from local.properties (gitignored)
    // via BuildConfig — see build.gradle.kts.
    private val CLIENT_ID = BuildConfig.GOOGLE_TV_CLIENT_ID
    private val CLIENT_SECRET = BuildConfig.GOOGLE_TV_CLIENT_SECRET

    // Deliberately not the app's own AppState-aware client (buildKtorClient()) —
    // these calls go straight to Google, not our backend, and don't need our
    // session's Authorization header riding along.
    private val client = HttpClient(Android) {
        expectSuccess = false
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
    }

    @Serializable
    data class DeviceCodeResponse(
        val device_code: String,
        val user_code: String,
        val verification_url: String,
        val expires_in: Int,
        val interval: Int
    )

    @Serializable
    data class TokenResponse(
        val access_token: String? = null,
        val id_token: String? = null,
        val error: String? = null
    )

    suspend fun requestDeviceCode(): DeviceCodeResponse =
        client.submitForm(
            url = "https://oauth2.googleapis.com/device/code",
            formParameters = Parameters.build {
                append("client_id", CLIENT_ID)
                append("scope", "openid email profile")
            }
        ).body()

    /** Returns the id_token once the user completes sign-in on their phone, or
     *  null while still pending (caller should keep polling at [interval]).
     *  Throws once Google reports a terminal error (expired/denied/etc). */
    suspend fun pollOnce(deviceCode: String): String? {
        val response = client.submitForm(
            url = "https://oauth2.googleapis.com/token",
            formParameters = Parameters.build {
                append("client_id", CLIENT_ID)
                append("client_secret", CLIENT_SECRET)
                append("device_code", deviceCode)
                append("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
            }
        )
        val body: TokenResponse = response.body()
        return when {
            body.id_token != null -> body.id_token
            body.error == "authorization_pending" || body.error == "slow_down" -> null
            else -> error(body.error ?: "Google sign-in failed")
        }
    }
}
