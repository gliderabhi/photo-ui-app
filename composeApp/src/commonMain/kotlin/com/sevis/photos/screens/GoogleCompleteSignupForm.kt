package com.sevis.photos.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sevis.photos.data.PhotoApi
import kotlinx.coroutines.launch

/**
 * Shown in place of the Google button once googleLogin() reports 404 (no role yet for
 * this app on this identity — see PhotoApi.isNotFound()/completeGoogleSignup()). Google
 * already verified who they are; this is just the one missing piece (a display name)
 * before the account is actually usable in Photos specifically. Shared across
 * Android/TV/iOS so this only needs writing — and fixing — once.
 */
@Composable
fun GoogleCompleteSignupForm(
    api: PhotoApi,
    idToken: String,
    onComplete: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    fun submit() {
        if (name.isBlank()) return
        loading = true
        error = ""
        scope.launch {
            runCatching { api.completeGoogleSignup(idToken, name.trim()) }
                .onSuccess { onComplete(it.token) }
                .onFailure { e ->
                    loading = false
                    error = e.message?.takeIf { it.isNotBlank() } ?: "Could not complete signup"
                }
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("You're signed in with Google", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1E293B))
        Text(
            "Just one more thing — what should we call you?",
            fontSize = 13.sp,
            color = Color(0xFF64748B)
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Your name") },
            singleLine = true,
            enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        )

        if (error.isNotBlank()) {
            Surface(color = Color(0xFFFEF2F2), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                Text(error, modifier = Modifier.fillMaxWidth(), fontSize = 13.sp, color = Color(0xFFDC2626))
            }
        }

        Button(
            onClick = { submit() },
            enabled = !loading && name.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A84FF))
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Text("Continue", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        TextButton(onClick = onCancel, enabled = !loading) {
            Text("Cancel", color = Color(0xFF64748B))
        }
    }
}
