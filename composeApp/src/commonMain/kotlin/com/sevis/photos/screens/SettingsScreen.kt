package com.sevis.photos.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sevis.photos.data.PhotoApi
import kotlinx.coroutines.launch

private sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState
    data object Checking : UpdateCheckState
    data class UpToDate(val versionName: String) : UpdateCheckState
    data class Available(val versionName: String, val notes: String?) : UpdateCheckState
    data class Failed(val message: String) : UpdateCheckState
}

/**
 * App-wide settings, currently: the installed version plus a manual "Check for Updates" ->
 * "Update Now" flow (the actual download/install still goes through UpdateManager via
 * onUpdateApp, same as before — this just adds a version check in front of it so updates
 * aren't unconditionally re-downloaded).
 */
@Composable
fun SettingsScreen(
    versionName: String,
    versionCode: Int,
    api: PhotoApi,
    autoUploadEnabled: Boolean,
    onAutoUploadToggle: (Boolean) -> Unit,
    updateProgress: Int?,
    onUpdateApp: () -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var checkState by remember { mutableStateOf<UpdateCheckState>(UpdateCheckState.Idle) }

    fun checkForUpdates() {
        checkState = UpdateCheckState.Checking
        scope.launch {
            runCatching { api.getAppVersion() }
                .onSuccess { remote ->
                    checkState = if (remote.versionCode > versionCode) {
                        UpdateCheckState.Available(remote.versionName, remote.releaseNotes)
                    } else {
                        UpdateCheckState.UpToDate(remote.versionName)
                    }
                }
                .onFailure { e -> checkState = UpdateCheckState.Failed(e.message ?: "Couldn't check for updates") }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF4F7FC)) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Settings", fontSize = 20.sp, fontWeight = FontWeight.Medium)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // App version / update card
                    SettingsCard {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.SystemUpdateAlt, contentDescription = null, tint = Color(0xFF2563EB))
                            Column {
                                Text("App version", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                                Text("v$versionName ($versionCode)", fontSize = 13.sp, color = Color(0xFF5F6368))
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        when (val state = checkState) {
                            is UpdateCheckState.Idle -> {}
                            is UpdateCheckState.Checking -> {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Text("Checking…", fontSize = 13.sp, color = Color(0xFF5F6368))
                                }
                            }
                            is UpdateCheckState.UpToDate -> {
                                Text("You're up to date (v${state.versionName})", fontSize = 13.sp, color = Color(0xFF1E8E3E))
                            }
                            is UpdateCheckState.Available -> {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Update available: v${state.versionName}", fontSize = 13.sp, color = Color(0xFF2563EB), fontWeight = FontWeight.Medium)
                                    state.notes?.let { Text(it, fontSize = 12.sp, color = Color(0xFF5F6368)) }
                                }
                            }
                            is UpdateCheckState.Failed -> {
                                Text(state.message, fontSize = 13.sp, color = Color(0xFFD93025))
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { checkForUpdates() },
                                enabled = checkState !is UpdateCheckState.Checking && updateProgress == null
                            ) { Text("Check for Updates") }

                            if (checkState is UpdateCheckState.Available) {
                                Button(
                                    onClick = onUpdateApp,
                                    enabled = updateProgress == null,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                                ) { Text(if (updateProgress != null) "Updating… ${updateProgress}%" else "Update Now") }
                            }
                        }
                    }

                    // Auto-upload card
                    SettingsCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color(0xFF2563EB))
                                Column {
                                    Text("Auto-upload to server", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                                    Text(
                                        "Off by default — your library stays on this device unless you turn this on",
                                        fontSize = 12.sp, color = Color(0xFF5F6368)
                                    )
                                }
                            }
                            Switch(checked = autoUploadEnabled, onCheckedChange = onAutoUploadToggle)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(12.dp))
            .padding(16.dp),
        content = content
    )
}
