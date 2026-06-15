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
fun FolderUnlockScreen(
    api: PhotoApi,
    onUnlockSuccess: (String) -> Unit,
    onChangePassword: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    fun submit() {
        if (password.isBlank()) return
        loading = true
        error = ""
        scope.launch {
            runCatching { api.verifyFolder(password) }
                .onSuccess { onUnlockSuccess(password) }
                .onFailure { e ->
                    error = e.message?.substringAfter(": ") ?: "Incorrect password"
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
                    Text("Unlock Folder", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                    Text("Enter your folder password to continue", fontSize = 13.sp, color = Color(0xFF64748B))
                }

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Folder Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); submit() }),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                if (error.isNotBlank()) {
                    Surface(color = Color(0xFFFEF2F2), shape = RoundedCornerShape(8.dp)) {
                        Text(error, modifier = Modifier.padding(12.dp), fontSize = 13.sp, color = Color(0xFFDC2626))
                    }
                }

                Button(
                    onClick = { focusManager.clearFocus(); submit() },
                    enabled = !loading && password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                ) {
                    if (loading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text("Unlock", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }

                TextButton(
                    onClick = onChangePassword,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Change password", fontSize = 13.sp, color = Color(0xFF64748B))
                }
            }
        }
    }
}
