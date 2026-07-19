package com.sevis.photos.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sevis.photos.data.PhotoApi
import com.sevis.photos.data.isUnauthorized
import com.sevis.photos.tvFocusRing
import photosapp.composeapp.generated.resources.Res
import photosapp.composeapp.generated.resources.app_icon
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

@Composable
fun LoginScreen(
    api: PhotoApi,
    onLoginSuccess: (String) -> Unit,
    // Google sign-in slot (see MainActivity): TV's QR/device-code flow or
    // mobile's native Credential Manager picker, depending on flavor. Receives
    // the same onLoginSuccess so a successful Google login navigates onward
    // exactly like the form above does, instead of just silently setting
    // AppState.token with no navigation follow-through.
    extraLoginContent: (@Composable ((String) -> Unit) -> Unit)? = null,
    // False on TV: typing an email/password via a D-pad-driven on-screen
    // keyboard is painful, and TV's Google QR/device-code flow is always
    // available, so the form is just dead weight there.
    showCredentialsForm: Boolean = true
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var showGoogleSignIn by remember { mutableStateOf(false) }

    fun submit() {
        if (email.isBlank() || password.isBlank()) return
        loading = true
        error = ""
        scope.launch {
            runCatching { api.login(email.trim(), password) }
                .onSuccess { onLoginSuccess(it.token) }
                .onFailure { e ->
                    error = if (e.isUnauthorized()) "Invalid email or password" else "Couldn't reach the server. Please try again."
                    loading = false
                }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF1F5F9)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.width(380.dp).padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Image(
                    painter = painterResource(Res.drawable.app_icon),
                    contentDescription = "Photos logo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(64.dp).clip(CircleShape)
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Sign In", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                    Text(
                        if (showCredentialsForm) "Enter your credentials to continue" else "Sign in with your Google account",
                        fontSize = 13.sp,
                        color = Color(0xFF64748B)
                    )
                }

                if (showCredentialsForm) {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email Address") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); submit() }),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    if (error.isNotBlank()) {
                        Surface(
                            color = Color(0xFFFEF2F2),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                error,
                                modifier = Modifier.padding(12.dp),
                                fontSize = 13.sp,
                                color = Color(0xFFDC2626)
                            )
                        }
                    }

                    Button(
                        onClick = { focusManager.clearFocus(); submit() },
                        enabled = !loading && email.isNotBlank() && password.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(50.dp).tvFocusRing(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A84FF))
                    ) {
                        if (loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Sign In", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                if (extraLoginContent != null) {
                    if (showCredentialsForm) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            HorizontalDivider(modifier = Modifier.weight(1f))
                            Text("  OR  ", fontSize = 12.sp, color = Color(0xFF94A3B8))
                            HorizontalDivider(modifier = Modifier.weight(1f))
                        }
                    }

                    if (!showCredentialsForm || showGoogleSignIn) {
                        extraLoginContent(onLoginSuccess)
                    } else {
                        OutlinedButton(
                            onClick = { showGoogleSignIn = true },
                            modifier = Modifier.fillMaxWidth().height(50.dp).tvFocusRing(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Sign in with Google", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1E293B))
                        }
                    }
                }
            }
        }
    }
}
