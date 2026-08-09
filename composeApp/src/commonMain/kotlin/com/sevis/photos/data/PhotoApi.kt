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

/** True if this came from a 404 — user-service's googleLogin returns this for a brand new
 *  Google identity (no account by that email at all yet). See isForbidden() for the other
 *  "needs signup" case, and AuthService#googleLogin for both. */
fun Throwable.isNotFound(): Boolean =
    this is ClientRequestException && response.status == HttpStatusCode.NotFound

/** True if this came from a 403 — user-service's googleLogin returns this when the email
 *  already has an account (from another app, e.g. Sevis CRM or RoomList) but no role for
 *  *this* app specifically yet. Combined with isNotFound(), covers both of
 *  AuthService#resolveRole's "needs signup" outcomes — both are resolved the identical way,
 *  by collecting a name and calling completeGoogleSignup(). */
fun Throwable.isForbidden(): Boolean =
    this is ClientRequestException && response.status == HttpStatusCode.Forbidden

/** True if googleLogin() failed for either reason a caller should show
 *  GoogleCompleteSignupForm instead of a plain error — see isNotFound()/isForbidden(). */
fun Throwable.needsGoogleSignupCompletion(): Boolean = isNotFound() || isForbidden()

/** user-service is shared across several apps (Sevis CRM, RoomList, Photos, …) and scopes
 *  Google identities' roles per app via this id — see AuthService's normalizeAppId/
 *  UserAppRole. Must stay in sync with whatever value (if any) the backend has already
 *  provisioned for Photos users under. */
const val PHOTOS_APP_ID = "PHOTOS"

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
                put("appId", PHOTOS_APP_ID)
            })
        }.body()

    /** Finishes signup for a Google identity googleLogin() reported 404 (isNotFound()) for
     *  — see GoogleCompleteSignupForm. idToken is re-verified server-side; only [name] is
     *  actually collected from the user, everything else about this account is fixed for
     *  a consumer app like Photos (no dealer/company fields to ask for). */
    suspend fun completeGoogleSignup(idToken: String, name: String): AuthResponse =
        client.post("$baseUrl/user-service/api/auth/google/complete") {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("idToken", idToken)
                put("name", name)
                put("role", "CUSTOMER")
                put("accountType", "INDIVIDUAL")
                put("appId", PHOTOS_APP_ID)
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

    // ── Faces / People (server-side face detection, see photo-service's
    //    FaceService — replaced the old on-device ML Kit/SFace pipeline) ──

    // No X-Folder-Password: these return only face geometry and person
    // metadata, never decrypted photo bytes, so they don't need it — same
    // as why photo-service's FaceController doesn't require it.
    suspend fun listPeople(): List<PersonResponse> =
        client.get("$baseUrl/photo-service/api/photos/people").body()

    suspend fun getPersonPhotos(personId: Long): List<PhotoResponse> =
        client.get("$baseUrl/photo-service/api/photos/people/$personId/photos").body()

    suspend fun renamePerson(personId: Long, label: String?): PersonResponse =
        client.patch("$baseUrl/photo-service/api/photos/people/$personId") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("label" to label))
        }.body()

    /** Detection normally only ever runs once, at upload time — this catches up
     *  photos uploaded before server-side face detection existed (see
     *  PhotoService#backfillFaces). Runs in the background server-side; this call
     *  itself returns as soon as the scan has started, not once it's finished. */
    suspend fun backfillFaces(): MessageResponse =
        client.post("$baseUrl/photo-service/api/photos/faces/backfill") { auth() }.body()

    suspend fun getPhotoFaces(photoId: Long): List<FaceResponse> =
        client.get("$baseUrl/photo-service/api/photos/$photoId/faces").body()

    // ── App updates ───────────────────────────────────────────────

    /** No auth — same publicly-served static file UpdateManager downloads the APK from. */
    suspend fun getAppVersion(): AppVersionResponse =
        client.get("$baseUrl/photo-service/downloads/version.json").body()
}
