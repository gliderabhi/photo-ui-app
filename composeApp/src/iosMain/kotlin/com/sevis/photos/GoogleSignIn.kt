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

/** iOS's counterpart to MobileGoogleLoginContent — same shape/behavior (loading
 *  spinner, retry-on-cancel, error with retry, and now the shared "complete your
 *  signup" step for a first-time Photos identity), Google's own OAuth flow instead
 *  of Android's Credential Manager. See GoogleAuth.kt for the actual sign-in call. */
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
            val signInResult = runCatching { signInWithGoogle() }
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
