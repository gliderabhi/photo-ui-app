package com.sevis.photos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.PlayerView

/** One selectable video quality rung, keyed by the Media3 track group + index within it. */
private data class QualityOption(val group: Tracks.Group, val trackIndexInGroup: Int, val height: Int)

/**
 * Plays an HLS video (stream-service's master.m3u8) full-screen. Started with
 * an "url" Intent extra pointing at the gateway-relative, already-resolved
 * stream-service URL, and a "token" extra so segment/playlist requests carry
 * the JWT the gateway needs to authorize the PHOTOS-scoped video.
 */
@UnstableApi
class VideoPlayerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val url = intent.getStringExtra("url") ?: run { finish(); return }
        val token = intent.getStringExtra("token")

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    val player = remember {
                        val dataSourceFactory = DefaultHttpDataSource.Factory().apply {
                            if (token != null) {
                                setDefaultRequestProperties(mapOf("Authorization" to "Bearer $token"))
                            }
                        }
                        val mediaSourceFactory = HlsMediaSource.Factory(dataSourceFactory)
                        ExoPlayer.Builder(this)
                            .setMediaSourceFactory(mediaSourceFactory)
                            .build()
                            .apply {
                                setMediaItem(MediaItem.fromUri(url))
                                prepare()
                                playWhenReady = true
                            }
                    }

                    var qualities by remember { mutableStateOf<List<QualityOption>>(emptyList()) }
                    var currentQualityLabel by remember { mutableStateOf("Auto") }
                    var showQualityMenu by remember { mutableStateOf(false) }

                    DisposableEffect(Unit) {
                        val listener = object : Player.Listener {
                            override fun onTracksChanged(tracks: Tracks) {
                                val videoGroup = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_VIDEO }
                                qualities = videoGroup?.let { group ->
                                    (0 until group.length)
                                        .mapNotNull { i ->
                                            val height = group.getTrackFormat(i).height
                                            if (height > 0) QualityOption(group, i, height) else null
                                        }
                                        .distinctBy { it.height }
                                        .sortedByDescending { it.height }
                                } ?: emptyList()
                            }
                        }
                        player.addListener(listener)
                        onDispose {
                            player.removeListener(listener)
                            player.release()
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply { this.player = player }
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        if (qualities.isNotEmpty()) {
                            Box(modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                                IconButton(onClick = { showQualityMenu = true }) {
                                    Icon(Icons.Default.Settings, contentDescription = "Quality: $currentQualityLabel", tint = Color.White)
                                }
                                DropdownMenu(
                                    expanded = showQualityMenu,
                                    onDismissRequest = { showQualityMenu = false },
                                    modifier = Modifier.background(Color(0xFF1F1F1F), RoundedCornerShape(10.dp))
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(if (currentQualityLabel == "Auto") "✓ Auto" else "Auto", color = Color.White) },
                                        onClick = {
                                            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                                                .build()
                                            currentQualityLabel = "Auto"
                                            showQualityMenu = false
                                        }
                                    )
                                    qualities.forEach { q ->
                                        val label = "${q.height}p"
                                        DropdownMenuItem(
                                            text = { Text(if (currentQualityLabel == label) "✓ $label" else label, color = Color.White) },
                                            onClick = {
                                                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                                    .setOverrideForType(
                                                        TrackSelectionOverride(q.group.mediaTrackGroup, q.trackIndexInGroup)
                                                    )
                                                    .build()
                                                currentQualityLabel = label
                                                showQualityMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
