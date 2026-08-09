package com.sevis.photos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sevis.photos.data.PhotoApi
import com.sevis.photos.data.needsGoogleSignupCompletion
import com.sevis.photos.screens.GoogleCompleteSignupForm
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Bridges GoogleNativeSignIn's callback shape into the same nullable-return-or-throw
 *  contract signInWithGoogle() has, so callers below don't need two code paths. Prefers
 *  the native SDK (Gmail/Google app hand-off) when Swift has wired one up; falls back to
 *  the browser-based PKCE flow otherwise — see GoogleNativeSignIn.kt. */
private suspend fun signInWithGoogleNativeOrFallback(): String? {
    val native = googleNativeSignIn ?: return signInWithGoogle()
    val (idToken, errorMessage) = suspendCancellableCoroutine { cont ->
        native.signIn { idToken, errorMessage -> cont.resume(idToken to errorMessage) }
    }
    if (errorMessage != null) error(errorMessage)
    return idToken
}

/** iOS's counterpart to MobileGoogleLoginContent — same shape/behavior (loading
 *  spinner, retry-on-cancel, error with retry, and now the shared "complete your
 *  signup" step for a first-time Photos identity). Prefers Google's native SDK (see
 *  GoogleNativeSignIn.kt, iosApp/GoogleSignInBridge.swift) so sign-in can hand off to
 *  the Google/Gmail app the way Android's Credential Manager flow does; falls back to
 *  the plain browser sheet (GoogleAuth.kt) if that's unavailable. */
@Composable
fun GoogleSignInButton(api: PhotoApi, onLoginSuccess: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Set once googleLogin() reports 404 — this identity has no role in Photos yet (see
    // user-service's AuthService#googleLogin/PhotoApi.isNotFound()).
    var pendingIdToken by remember { mutableStateOf<String?>(null) }

    fun start() {
        loading = true
        error = null
        scope.launch {
            val signInResult = runCatching { signInWithGoogleNativeOrFallback() }
            val idToken = signInResult.getOrNull()
            if (signInResult.isFailure) {
                loading = false
                val e = signInResult.exceptionOrNull()
                error = e?.message?.takeIf { it.isNotBlank() } ?: "Google sign-in failed"
                return@launch
            }
            if (idToken == null) {
                loading = false // user cancelled — quietly back to idle, not an error
                return@launch
            }

            runCatching { api.googleLogin(idToken, longLived = true).token }
                .onSuccess { appToken ->
                    loading = false
                    onLoginSuccess(appToken)
                }
                .onFailure { e ->
                    loading = false
                    if (e.needsGoogleSignupCompletion()) {
                        pendingIdToken = idToken
                    } else {
                        error = e.message?.takeIf { it.isNotBlank() } ?: "Google sign-in failed"
                    }
                }
        }
    }

    if (pendingIdToken != null) {
        GoogleCompleteSignupForm(
            api = api,
            idToken = pendingIdToken!!,
            onComplete = onLoginSuccess,
            onCancel = { pendingIdToken = null },
        )
        return
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        when {
            loading -> CircularProgressIndicator(modifier = Modifier.size(28.dp))
            error != null -> {
                Text(error!!, fontSize = 13.sp, color = Color(0xFFDC2626))
                OutlinedButton(onClick = { start() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Try again")
                }
            }
            else -> {
                OutlinedButton(onClick = { start() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Sign in with Google")
                }
            }
        }
    }
}
