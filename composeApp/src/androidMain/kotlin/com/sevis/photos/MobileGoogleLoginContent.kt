package com.sevis.photos

import android.util.Log
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
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.sevis.photos.data.PhotoApi

/**
 * Native "Sign in with Google" for phones/tablets via Credential Manager — the
 * standard Gmail account picker, distinct from the TV app's QR/device-code flow
 * (which only exists because a D-pad-driven browser sign-in is impractical).
 */
@Composable
fun MobileGoogleLoginContent(api: PhotoApi, onLoginSuccess: (String) -> Unit) {
    val context = LocalContext.current
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }

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
            val auth = api.googleLogin(googleCredential.idToken, longLived = true)
            onLoginSuccess(auth.token)
        } catch (e: GetCredentialCancellationException) {
            // User backed out of the account picker — not a real error, just
            // let them tap the button again instead of showing empty/blank
            // text (this exception's message is usually an empty string, not
            // null, so a plain `?:` fallback never kicked in here before).
            loading = false
        } catch (e: GetCredentialException) {
            error = e.message?.takeIf { it.isNotBlank() } ?: "Google sign-in failed (${e.type})"
            loading = false
        } catch (e: Exception) {
            error = e.message?.takeIf { it.isNotBlank() } ?: "Google sign-in failed: ${e::class.simpleName}"
            loading = false
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
                OutlinedButton(onClick = { attempt++ }, modifier = Modifier.fillMaxWidth()) {
                    Text("Try again")
                }
            }
            else -> {
                Log.d("TAG", "MobileGoogleLoginContent: Instead of completing stuck here")
                OutlinedButton(onClick = { attempt++ }, modifier = Modifier.fillMaxWidth()) {
                    Text("Sign in with Google")
                }
            }
        }
    }
}
