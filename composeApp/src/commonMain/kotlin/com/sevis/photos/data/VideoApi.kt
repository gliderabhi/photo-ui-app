package com.sevis.photos.data

import com.sevis.photos.AppState
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*

/**
 * Video upload/delete go through photo-service (which resolves a per-user
 * storageBaseDir under the encrypted photo folder before forwarding to
 * stream-service's existing ffmpeg pipeline — see project decision to reuse
 * stream-service directly instead of building a separate pipeline).
 *
 * Listing/status/playback talk to stream-service directly through the gateway:
 * the gateway's JWT filter already injects a trustworthy X-User-Id header, and
 * stream-service scopes PHOTOS-origin videos to that header, so there's no need
 * to proxy binary HLS/thumbnail traffic through photo-service.
 */
class VideoApi(private val baseUrl: String, val client: HttpClient) {

    // Authorization is already added to every request by the shared client's
    // "DynamicAuth" plugin (see MainActivity.buildKtorClient) — adding it again
    // here would append a second, duplicate Authorization header (Ktor's
    // header() appends rather than replaces), which some backends/gateways
    // reject outright as malformed.
    private fun HttpRequestBuilder.auth() {
        AppState.folderPassword?.let { header("X-Folder-Password", it) }
    }

    suspend fun listVideos(): List<VideoResponse> =
        client.get("$baseUrl/stream-service/api/videos") {
            parameter("sourceApp", "PHOTOS")
        }.body()

    suspend fun getVideo(id: String): VideoResponse =
        client.get("$baseUrl/stream-service/api/videos/$id").body()

    suspend fun uploadVideo(bytes: ByteArray, filename: String, mimeType: String): VideoResponse =
        client.post("$baseUrl/photo-service/api/photos/videos/upload") {
            auth()
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            key = "file",
                            value = bytes,
                            headers = Headers.build {
                                append(HttpHeaders.ContentDisposition, "filename=\"$filename\"")
                                append(HttpHeaders.ContentType, mimeType)
                            }
                        )
                    }
                )
            )
        }.body()

    suspend fun deleteVideo(id: String): MessageResponse =
        client.delete("$baseUrl/photo-service/api/photos/videos/$id") { auth() }.body()

    /** Prefixes a gateway-relative URL (e.g. "/stream-service/api/videos/.../master.m3u8") with baseUrl. */
    fun resolveUrl(relativeUrl: String): String = "$baseUrl$relativeUrl"
}
