package com.sevis.photos.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.sevis.photos.data.local.LocalMediaEntity
import com.sevis.photos.data.local.PersonEntity
import com.sevis.photos.data.local.PhotosDatabase
import com.sevis.photos.ui.GlassColors
import java.io.File

/**
 * Grid of clustered "people" — see FaceEmbedder for why this is an approximate,
 * on-device pixel-pattern clustering rather than verified face recognition.
 */
@Composable
fun FacesScreen(onBack: () -> Unit, onPersonClick: (Long, String?) -> Unit) {
    val context = LocalContext.current
    val db = remember { PhotosDatabase.get(context) }
    val people by db.personDao().observeAll().collectAsState(initial = emptyList())

    Column(modifier = Modifier.fillMaxSize().background(com.sevis.photos.ui.GlassPageBackground)) {
        PaneHeader(title = "People", subtitle = "${people.size} clustered from your photos", onBack = onBack)

        if (people.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = GlassColors.AccentBlue)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 92.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(people, key = { it.id }) { person ->
                    PersonCell(person = person, onClick = { onPersonClick(person.id, person.displayName) })
                }
            }
        }
    }
}

@Composable
private fun PersonCell(person: PersonEntity, onClick: () -> Unit) {
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(GlassColors.AccentPurple.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            if (person.coverFacePath != null) {
                AsyncImage(
                    model = File(person.coverFacePath),
                    contentDescription = person.displayName ?: "Person",
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
                Icon(Icons.Default.Person, contentDescription = null, tint = GlassColors.AccentPurple)
            }
        }
        Text(
            person.displayName ?: "Person",
            fontSize = 12.sp,
            color = GlassColors.TextPrimary,
            maxLines = 1
        )
    }
}

/** All photos containing a given clustered person, reusing the Library grid look. */
@Composable
fun PersonPhotosScreen(personId: Long, displayName: String?, onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { PhotosDatabase.get(context) }
    var photos by remember { mutableStateOf<List<LocalMediaEntity>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var lightboxPhoto by remember { mutableStateOf<LocalMediaEntity?>(null) }

    LaunchedEffect(personId) {
        loading = true
        val mediaIds = db.faceDao().mediaIdsForPerson(personId)
        photos = db.localMediaDao().byIds(mediaIds)
        loading = false
    }

    if (lightboxPhoto != null) {
        LocalPhotoLightbox(photos = photos, initialPhoto = lightboxPhoto!!, onDismiss = { lightboxPhoto = null })
    }

    Column(modifier = Modifier.fillMaxSize().background(com.sevis.photos.ui.GlassPageBackground)) {
        PaneHeader(
            title = displayName ?: "Person",
            subtitle = if (loading) "Loading…" else "${photos.size} photo${if (photos.size != 1) "s" else ""}",
            onBack = onBack
        )

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = GlassColors.AccentBlue)
            }
        } else if (photos.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No photos found for this person", color = GlassColors.TextSecondary, fontSize = 13.sp)
            }
        } else {
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(1.5.dp),
                verticalArrangement = Arrangement.spacedBy(1.5.dp)
            ) {
                photoGridItems(photos, onPhotoClick = { lightboxPhoto = it })
            }
        }
    }
}

@Composable
internal fun PaneHeader(title: String, subtitle: String, onBack: () -> Unit) {
    // Matches GlassTopBar's frosted treatment (translucent white + hairline seam)
    // so every pane reads as one consistent glass chrome, not a plain Material bar.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(com.sevis.photos.ui.GlassBarBackground)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.7f))
                    .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.ArrowBackIosNew, contentDescription = "Back", tint = GlassColors.TextPrimary, modifier = Modifier.size(16.dp))
            }
            Column {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = GlassColors.TextPrimary)
                Text(subtitle, fontSize = 12.sp, color = GlassColors.TextSecondary)
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(0.6.dp)
                .background(GlassColors.Hairline)
        )
    }
}
