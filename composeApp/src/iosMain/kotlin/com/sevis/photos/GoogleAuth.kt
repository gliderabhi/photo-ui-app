package com.sevis.photos

import io.ktor.client.call.body
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.forms.submitForm
import io.ktor.http.Parameters
import io.ktor.http.encodeURLParameter
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.AuthenticationServices.ASWebAuthenticationSessionErrorCodeCanceledLogin
import platform.Foundation.NSURL
import platform.Foundation.NSURLComponents
import platform.Foundation.NSURLQueryItem
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private const val REDIRECT_PATH = "oauth2redirect"

// Kept alive at file scope for the duration of a sign-in attempt — ASWebAuthenticationSession
// holds its presentationContextProvider *weakly*, so a purely local val would be deallocated
// before the browser sheet ever calls back into it.
private var activeSession: ASWebAuthenticationSession? = null
private var activeContextProvider: NSObject? = null

/**
 * Google sign-in via the standard OAuth 2.0 Authorization Code + PKCE flow, run through
 * the system browser sheet (ASWebAuthenticationSession) — the iOS-native equivalent of
 * Android's Credential Manager Google ID flow (MobileGoogleLoginContent.kt). Returns the
 * id_token — the exact same shape api.googleLogin(idToken, ...) already expects on both
 * platforms — or null if the user cancelled.
 */
@OptIn(ExperimentalForeignApi::class, ExperimentalEncodingApi::class)
suspend fun signInWithGoogle(): String? {
    check(GOOGLE_IOS_CLIENT_ID.isNotBlank() && GOOGLE_IOS_URL_SCHEME.isNotBlank()) {
        "Google sign-in isn't configured — add an iOS OAuth client in Google Cloud Console " +
            "and set GOOGLE_IOS_CLIENT_ID/GOOGLE_IOS_URL_SCHEME in AppConfig.kt"
    }

    val verifierBytes = ByteArray(32)
    verifierBytes.usePinned { pinned ->
        SecRandomCopyBytes(kSecRandomDefault, verifierBytes.size.toULong(), pinned.addressOf(0))
    }
    val codeVerifier = Base64.UrlSafe.encode(verifierBytes).trimEnd('=')
    val codeChallenge = Base64.UrlSafe.encode(sha256(codeVerifier.encodeToByteArray())).trimEnd('=')
    val redirectUri = "$GOOGLE_IOS_URL_SCHEME:/$REDIRECT_PATH"

    val authUrl = buildString {
        append("https://accounts.google.com/o/oauth2/v2/auth")
        append("?client_id=").append(GOOGLE_IOS_CLIENT_ID.encodeURLParameter())
        append("&redirect_uri=").append(redirectUri.encodeURLParameter())
        append("&response_type=code")
        append("&scope=").append("openid email profile".encodeURLParameter())
        append("&code_challenge=").append(codeChallenge)
        append("&code_challenge_method=S256")
    }

    val callbackUrl = presentAuthSession(authUrl, GOOGLE_IOS_URL_SCHEME) ?: return null
    val code = queryParam(callbackUrl, "code")
        ?: error("Google sign-in didn't return an authorization code")
    return exchangeCodeForIdToken(code, codeVerifier, redirectUri)
}

@OptIn(ExperimentalForeignApi::class)
private suspend fun presentAuthSession(url: String, callbackScheme: String): String? =
    suspendCancellableCoroutine { cont ->
        val contextProvider = object : NSObject(), ASWebAuthenticationPresentationContextProvidingProtocol {
            override fun presentationAnchorForWebAuthenticationSession(session: ASWebAuthenticationSession): ASPresentationAnchor {
                return UIApplication.sharedApplication.keyWindow ?: UIWindow()
            }
        }

        val session = ASWebAuthenticationSession(
            uRL = NSURL(string = url),
            callbackURLScheme = callbackScheme,
        ) { callbackUrl, error ->
            activeSession = null
            activeContextProvider = null
            if (!cont.isActive) return@ASWebAuthenticationSession
            when {
                callbackUrl != null -> cont.resume(callbackUrl.absoluteString)
                error?.code == ASWebAuthenticationSessionErrorCodeCanceledLogin -> cont.resume(null)
                else -> cont.resumeWithException(RuntimeException(error?.localizedDescription ?: "Google sign-in failed"))
            }
        }
        session.presentationContextProvider = contextProvider
        session.prefersEphemeralWebBrowserSession = true

        // Hold strong references for as long as the session is running.
        activeSession = session
        activeContextProvider = contextProvider

        cont.invokeOnCancellation {
            session.cancel()
            activeSession = null
            activeContextProvider = null
        }
        session.start()
    }

private fun queryParam(urlString: String, name: String): String? {
    val components = NSURLComponents(uRL = NSURL(string = urlString) ?: return null, resolvingAgainstBaseURL = false)
    @Suppress("UNCHECKED_CAST")
    val items = components.queryItems as? List<NSURLQueryItem> ?: return null
    return items.firstOrNull { it.name == name }?.value
}

@Serializable
private data class GoogleTokenResponse(
    val id_token: String? = null,
    val error: String? = null,
    val error_description: String? = null,
)

private suspend fun exchangeCodeForIdToken(code: String, codeVerifier: String, redirectUri: String): String {
    // A short-lived, unauthenticated client — this talks to Google, not our
    // backend, and doesn't need the shared client's DynamicAuth Bearer header.
    val client = buildKtorClient()
    val response: GoogleTokenResponse = client.submitForm(
        url = "https://oauth2.googleapis.com/token",
        formParameters = Parameters.build {
            append("client_id", GOOGLE_IOS_CLIENT_ID)
            append("code", code)
            append("code_verifier", codeVerifier)
            append("grant_type", "authorization_code")
            append("redirect_uri", redirectUri)
        },
    ) { expectSuccess = false }.body()
    return response.id_token ?: error(response.error_description ?: response.error ?: "Google didn't return an id_token")
}
