package com.sevis.photos.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sevis.photos.data.PhotoApi
import kotlinx.coroutines.launch

@Composable
fun FolderSetupScreen(
    api: PhotoApi,
    onSetupComplete: (String) -> Unit,
    isChanging: Boolean = false
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var success by remember { mutableStateOf("") }
    var hasFolder by remember { mutableStateOf(isChanging) }

    LaunchedEffect(Unit) {
        if (!isChanging) {
            runCatching { api.getFolderStatus() }
                .onSuccess { hasFolder = it.hasFolder }
        }
    }

    val passwordsMatch = newPassword == confirmPassword
    val canSubmit = !loading && newPassword.length >= 4 && confirmPassword.isNotBlank() &&
        passwordsMatch && (!hasFolder || currentPassword.isNotBlank())

    fun submit() {
        if (!canSubmit) return
        loading = true
        error = ""
        scope.launch {
            runCatching {
                api.setupFolder(newPassword, if (hasFolder) currentPassword else null)
            }
                .onSuccess {
                    success = "Password set! Redirecting..."
                    kotlinx.coroutines.delay(800)
                    onSetupComplete(newPassword)
                }
                .onFailure { e ->
                    error = e.message?.substringAfter(": ") ?: "Failed to set password"
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
            elevation = CardDefaults.cardElevation(6.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(40.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        if (hasFolder) "Update Password" else "Set Folder Password",
                        fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B)
                    )
                    Text(
                        if (hasFolder) "Enter your current password then choose a new one"
                        else "Choose a password to protect your private folder",
                        fontSize = 13.sp, color = Color(0xFF64748B)
                    )
                }

                if (hasFolder) {
                    OutlinedTextField(
                        value = currentPassword,
                        onValueChange = { currentPassword = it },
                        label = { Text("Current Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("New Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    isError = newPassword.isNotBlank() && newPassword.length < 4,
                    supportingText = if (newPassword.isNotBlank() && newPassword.length < 4) {
                        { Text("At least 4 characters", color = MaterialTheme.colorScheme.error) }
                    } else null
                )

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirm Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); submit() }),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    isError = confirmPassword.isNotBlank() && !passwordsMatch,
                    supportingText = if (confirmPassword.isNotBlank() && !passwordsMatch) {
                        { Text("Passwords do not match", color = MaterialTheme.colorScheme.error) }
                    } else null
                )

                if (error.isNotBlank()) {
                    Surface(color = Color(0xFFFEF2F2), shape = RoundedCornerShape(8.dp)) {
                        Text(error, modifier = Modifier.padding(12.dp), fontSize = 13.sp, color = Color(0xFFDC2626))
                    }
                }
                if (success.isNotBlank()) {
                    Surface(color = Color(0xFFF0FDF4), shape = RoundedCornerShape(8.dp)) {
                        Text(success, modifier = Modifier.padding(12.dp), fontSize = 13.sp, color = Color(0xFF16A34A))
                    }
                }

                Button(
                    onClick = { focusManager.clearFocus(); submit() },
                    enabled = canSubmit,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                ) {
                    if (loading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text(if (hasFolder) "Update Password" else "Set Password", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
