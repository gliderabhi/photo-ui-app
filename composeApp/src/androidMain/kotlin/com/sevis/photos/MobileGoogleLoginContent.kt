package com.sevis.photos

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.OutlinedButton
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.gms.auth.api.signin.GoogleSignIn as LegacyGoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.sevis.photos.data.PhotoApi
import com.sevis.photos.data.needsGoogleSignupCompletion
import com.sevis.photos.screens.GoogleCompleteSignupForm
import kotlinx.coroutines.launch

/**
 * Native "Sign in with Google" for phones/tablets via Credential Manager — the
 * standard Gmail account picker, distinct from the TV app's QR/device-code flow
 * (which only exists because a D-pad-driven browser sign-in is impractical).
 *
 * Credential Manager is Google's current recommended API and normally shows this same
 * native picker without ever touching a browser — but on some devices/Play services
 * versions it can't find anything to offer (NoCredentialException) even though the device
 * genuinely has a Google account, and either errors out or the OS falls back to a
 * Chrome-based OAuth page instead. GoogleSignInClient (com.google.android.gms.auth.api.signin
 * — the pre-Credential-Manager entry point) talks to Play services more directly and tends
 * to still find accounts Credential Manager misses, so it's tried as a second native
 * attempt on exactly that failure before ever giving up to a real error screen.
 */
@Composable
fun MobileGoogleLoginContent(api: PhotoApi, onLoginSuccess: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }
    // Set once googleLogin() reports 404/403 — this identity has no role in Photos yet, so
    // GoogleCompleteSignupForm takes over instead of treating it as a real error (see
    // user-service's AuthService#googleLogin/PhotoApi.needsGoogleSignupCompletion()).
    var pendingIdToken by remember { mutableStateOf<String?>(null) }

    val legacyClient = remember {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .requestEmail()
            .build()
        LegacyGoogleSignIn.getClient(context, options)
    }

    // Shared by both the Credential Manager and legacy paths below, so a Google idToken
    // from either one is handed to the backend identically.
    suspend fun completeLogin(idToken: String) {
        runCatching { api.googleLogin(idToken, longLived = true) }
            .onSuccess { onLoginSuccess(it.token) }
            .onFailure { e ->
                loading = false
                if (e.needsGoogleSignupCompletion()) {
                    pendingIdToken = idToken
                } else {
                    error = e.message?.takeIf { it.isNotBlank() } ?: "Google sign-in failed: ${e::class.simpleName}"
                }
            }
    }

    val legacyLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            // User backed out of the legacy picker too — same as a Credential Manager
            // cancellation, quietly back to idle rather than showing an error.
            loading = false
            return@rememberLauncherForActivityResult
        }
        runCatching {
            LegacyGoogleSignIn.getSignedInAccountFromIntent(result.data)
                .getResult(ApiException::class.java)
                .idToken
        }.onSuccess { idToken ->
            if (idToken == null) {
                error = "Google sign-in failed: no ID token"
                loading = false
            } else {
                scope.launch { completeLogin(idToken) }
            }
        }.onFailure { e ->
            error = e.message?.takeIf { it.isNotBlank() } ?: "Google sign-in failed"
            loading = false
        }
    }

    LaunchedEffect(attempt) {
        loading = true
        error = null
        if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank()) {
            error = "Google sign-in isn't configured (missing google.web.client.id)"
            loading = false
            return@LaunchedEffect
        }
        try {
            val option = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                .build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val result = CredentialManager.create(context).getCredential(context, request)
            val googleCredential = GoogleIdTokenCredential.createFrom(result.credential.data)
            completeLogin(googleCredential.idToken)
        } catch (e: GetCredentialCancellationException) {
            // User backed out of the account picker — not a real error, just
            // let them tap the button again instead of showing empty/blank
            // text (this exception's message is usually an empty string, not
            // null, so a plain `?:` fallback never kicked in here before).
            loading = false
        } catch (e: NoCredentialException) {
            // Nothing Credential Manager could offer natively — try the older
            // GoogleSignInClient picker (see this function's doc comment) before
            // surfacing an error. Stays in the loading state; legacyLauncher's
            // callback above resolves it either way.
            legacyLauncher.launch(legacyClient.signInIntent)
        } catch (e: GetCredentialException) {
            error = e.message?.takeIf { it.isNotBlank() } ?: "Google sign-in failed (${e.type})"
            loading = false
        }
    }

    if (pendingIdToken != null) {
        GoogleCompleteSignupForm(
            api = api,
            idToken = pendingIdToken!!,
            onComplete = onLoginSuccess,
            onCancel = { pendingIdToken = null; attempt++ },
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
                OutlinedButton(onClick = { attempt++ }, modifier = Modifier.fillMaxWidth()) {
                    Text("Try again")
                }
            }
            else -> {
                OutlinedButton(onClick = { attempt++ }, modifier = Modifier.fillMaxWidth()) {
                    Text("Sign in with Google")
                }
            }
        }
    }
}
