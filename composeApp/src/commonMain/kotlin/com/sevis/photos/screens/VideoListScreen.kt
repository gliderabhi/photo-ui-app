package com.sevis.photos.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.sevis.photos.data.VideoApi
import com.sevis.photos.data.VideoResponse
import kotlinx.coroutines.delay

@Composable
fun VideoListScreen(
    videoApi: VideoApi,
    onPlayVideo: (String) -> Unit
) {
    var videos by remember { mutableStateOf<List<VideoResponse>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    suspend fun refresh() {
        runCatching { videoApi.listVideos() }.onSuccess { videos = it }
        loading = false
    }

    LaunchedEffect(Unit) { refresh() }

    // Poll while any video is still transcoding, so progress/thumbnails update live.
    LaunchedEffect(videos) {
        if (videos.any { it.status == "PENDING" || it.status == "PROCESSING" }) {
            delay(4000)
            refresh()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("Videos", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF202124))

        if (loading) {
            Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (videos.isEmpty()) {
            Text("No videos yet", fontSize = 13.sp, color = Color(0xFF9AA0A6))
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                modifier = Modifier.height(400.dp),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(videos, key = { it.id }) { video ->
                    VideoTile(video = video, videoApi = videoApi, onPlayVideo = onPlayVideo)
                }
            }
        }
    }
}

@Composable
private fun VideoTile(video: VideoResponse, videoApi: VideoApi, onPlayVideo: (String) -> Unit) {
    // Prefer the finished HLS ladder once ready; while still encoding (or before
    // encoding starts), fall back to direct-playing the raw source so playback
    // isn't blocked on the whole transcode pipeline finishing.
    val playableUrl = video.masterPlaylistUrl ?: video.rawStreamUrl
    val isPlayable = playableUrl != null

    Box(
        modifier = Modifier
            .aspectRatio(16f / 10f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF1F3F4))
            .clickable(enabled = isPlayable) {
                playableUrl?.let { onPlayVideo(videoApi.resolveUrl(it)) }
            }
    ) {
        video.thumbnailUrl?.let {
            AsyncImage(
                model = videoApi.resolveUrl(it),
                contentDescription = video.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        if (isPlayable) {
            Box(
                modifier = Modifier.align(Alignment.Center).size(40.dp).clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White)
            }
        } else if (video.status == "FAILED") {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Error, contentDescription = "Failed", tint = Color(0xFFEA4335))
            }
        } else {
            // No raw file yet and no HLS yet (e.g. still downloading) — nothing
            // playable, but we don't surface encoding/processing state in the UI.
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
        }

        Text(
            video.title,
            fontSize = 11.sp,
            color = Color.White,
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.5f)).padding(4.dp)
        )
    }
}
