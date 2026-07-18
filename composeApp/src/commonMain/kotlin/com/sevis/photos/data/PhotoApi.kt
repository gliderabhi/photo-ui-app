package com.sevis.photos.data

import com.sevis.photos.AppState
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** True if this came from a 401 response — e.g. an expired/missing JWT that the
 *  gateway rejected outright (with an empty body) before reaching the backend. */
fun Throwable.isUnauthorized(): Boolean =
    this is ClientRequestException && response.status == HttpStatusCode.Unauthorized

class PhotoApi(private val baseUrl: String, val client: HttpClient) {

    // Authorization is already added to every request by the shared client's
    // "DynamicAuth" plugin (see MainActivity.buildKtorClient) — adding it again
    // here via header() would append a second, duplicate Authorization header
    // (Ktor's header() appends rather than replaces), which some backends/
    // gateways reject outright as malformed, surfacing as a confusing 401.
    private fun HttpRequestBuilder.auth() {
        AppState.folderPassword?.let { header("X-Folder-Password", it) }
    }

    private fun HttpRequestBuilder.folderAuth(password: String? = null) {
        val pwd = password ?: AppState.folderPassword
        pwd?.let { header("X-Folder-Password", it) }
    }

    // ── Auth ──────────────────────────────────────────────────────

    suspend fun login(email: String, password: String): AuthResponse =
        client.post("$baseUrl/user-service/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("email" to email, "password" to password))
        }.body()

    suspend fun googleLogin(idToken: String, longLived: Boolean = false): AuthResponse =
        client.post("$baseUrl/user-service/api/auth/google") {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("idToken", idToken)
                put("longLived", longLived)
            })
        }.body()

    suspend fun logout() {
        runCatching {
            client.post("$baseUrl/user-service/api/auth/logout") {
                AppState.token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            }
        }
    }

    // ── Folder ────────────────────────────────────────────────────

    suspend fun getFolderStatus(): FolderStatusResponse =
        client.get("$baseUrl/photo-service/api/photos/folder/status") { auth() }.body()

    suspend fun setupFolder(password: String, currentPassword: String? = null): MessageResponse =
        client.post("$baseUrl/photo-service/api/photos/folder/setup") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(buildMap<String, String> {
                put("password", password)
                currentPassword?.let { put("currentPassword", it) }
            })
        }.body()

    suspend fun verifyFolder(password: String): MessageResponse =
        client.post("$baseUrl/photo-service/api/photos/folder/verify") {
            folderAuth(password)
        }.body()

    // ── Photos ────────────────────────────────────────────────────

    suspend fun listPhotos(): List<PhotosByDate> =
        client.get("$baseUrl/photo-service/api/photos") { auth() }.body()

    suspend fun uploadImage(bytes: ByteArray, filename: String, mimeType: String): PhotoResponse =
        client.post("$baseUrl/photo-service/api/photos/upload") {
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

    suspend fun deletePhoto(photoId: Int): MessageResponse =
        client.delete("$baseUrl/photo-service/api/photos/$photoId") { auth() }.body()

    suspend fun bulkDeletePhotos(photoIds: List<Int>): MessageResponse =
        client.delete("$baseUrl/photo-service/api/photos/bulk") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(mapOf("photoIds" to photoIds))
        }.body()

    // ── Albums ────────────────────────────────────────────────────

    suspend fun listAlbums(): List<AlbumResponse> =
        client.get("$baseUrl/photo-service/api/albums") { auth() }.body()

    suspend fun createAlbum(name: String): AlbumResponse =
        client.post("$baseUrl/photo-service/api/albums") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(mapOf("name" to name))
        }.body()

    suspend fun deleteAlbum(albumId: Int): MessageResponse =
        client.delete("$baseUrl/photo-service/api/albums/$albumId") { auth() }.body()

    suspend fun getAlbumPhotos(albumId: Int): List<PhotoResponse> =
        client.get("$baseUrl/photo-service/api/albums/$albumId/photos") { auth() }.body()

    suspend fun addPhotosToAlbum(albumId: Int, photoIds: List<Int>): MessageResponse =
        client.post("$baseUrl/photo-service/api/albums/$albumId/photos") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(mapOf("photoIds" to photoIds))
        }.body()

    suspend fun removePhotoFromAlbum(albumId: Int, photoId: Int): MessageResponse =
        client.delete("$baseUrl/photo-service/api/albums/$albumId/photos/$photoId") {
            auth()
        }.body()

    // ── App updates ───────────────────────────────────────────────

    /** No auth — same publicly-served static file UpdateManager downloads the APK from. */
    suspend fun getAppVersion(): AppVersionResponse =
        client.get("$baseUrl/photo-service/downloads/version.json").body()
}
