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
import kotlinx.coroutines.launch

/** iOS's counterpart to MobileGoogleLoginContent — same shape/behavior (loading
 *  spinner, retry-on-cancel, error with retry), Google's own OAuth flow instead
 *  of Android's Credential Manager. See GoogleAuth.kt for the actual sign-in call. */
@Composable
fun GoogleSignInButton(api: PhotoApi, onLoginSuccess: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun start() {
        loading = true
        error = null
        scope.launch {
            runCatching {
                val idToken = signInWithGoogle() ?: return@runCatching null // user cancelled
                // Google's id_token identifies the user to Google, not to our backend — it
                // has to be exchanged for our own signed session JWT (same as Android's
                // MobileGoogleLoginContent) before it's usable as AppState.token. Skipping
                // this and using idToken directly is exactly what caused the "sign in
                // succeeds, then immediately bounces back to Login" bug: the gateway's JWT
                // filter can't verify a Google-signed token against our HMAC secret, so the
                // very next authenticated request (FolderCheckScreen's getFolderStatus) gets
                // a 401 and FolderCheckScreen logs the user back out.
                api.googleLogin(idToken, longLived = true).token
            }
                .onSuccess { appToken ->
                    loading = false
                    if (appToken != null) onLoginSuccess(appToken)
                }
                .onFailure { e ->
                    loading = false
                    error = e.message?.takeIf { it.isNotBlank() } ?: "Google sign-in failed"
                }
        }
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
