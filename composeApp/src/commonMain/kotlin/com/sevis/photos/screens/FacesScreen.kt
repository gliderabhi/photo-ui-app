package com.sevis.photos.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Face
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
import com.sevis.photos.data.PersonResponse
import com.sevis.photos.data.PhotoApi
import com.sevis.photos.data.PhotoResponse
import kotlinx.coroutines.launch

/**
 * Shared pane header (back button + title/subtitle) for the local-library
 * sub-panes reached from ShellScreen's FAB — Albums, People, Album/Person
 * detail. Previously lived in the (now server-backed) FacesScreen; kept here
 * since People still owns this file.
 */
@Composable
fun PaneHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(16.dp)
    ) {
        IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
        Spacer(Modifier.width(4.dp))
        Column {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Normal, color = Color(0xFF202124))
            Text(subtitle, fontSize = 13.sp, color = Color(0xFF80868B))
        }
    }
}

/**
 * People, grouped from server-side face detection (photo-service's
 * face-service pipeline — see PhotoApi's listPeople/getPersonPhotos). Used to
 * be backed by an on-device ML Kit/SFace pipeline and local Room tables; that
 * moved server-side, so this is now just a thin network-backed grid like
 * GalleryScreen/AlbumsScreen, not a local-only screen anymore.
 */
@Composable
fun PeopleScreen(api: PhotoApi, baseUrl: String, onBack: () -> Unit, onPersonClick: (Long, String?) -> Unit) {
    val scope = rememberCoroutineScope()
    var people by remember { mutableStateOf<List<PersonResponse>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        scope.launch {
            runCatching { api.listPeople() }.onSuccess { people = it }
            loading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(com.sevis.photos.ui.GlassPageBackground)) {
        PaneHeader(title = "People", subtitle = "${people.size} ${if (people.size == 1) "person" else "people"}", onBack = onBack)

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            people.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Filled.Face, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                    Text("No people found yet", fontSize = 15.sp, color = Color(0xFF5F6368))
                    Text(
                        "Faces are detected server-side as photos are uploaded — check back after uploading a few.",
                        fontSize = 13.sp, color = Color(0xFF80868B)
                    )
                }
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 100.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(people, key = { it.id }) { person ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { onPersonClick(person.id, person.label) }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE8EAED)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (person.coverPhotoId != null) {
                                // Approximation, not a precise crop to the face box: cropping to
                                // person.coverBoxTop/Right/Bottom/Left would need decoding the
                                // full image client-side just to crop it, which AsyncImage/Coil
                                // doesn't support out of the box. Center-crop reads fine for a
                                // person avatar in the vast majority of photos.
                                AsyncImage(
                                    model = "$baseUrl/photo-service/api/photos/${person.coverPhotoId}/content?maxDimension=400",
                                    contentDescription = person.label ?: "Unnamed person",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(Icons.Filled.Face, contentDescription = null, tint = Color(0xFF9AA0A6))
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            person.label ?: "Person ${person.id}",
                            fontSize = 13.sp,
                            color = Color(0xFF3C4043)
                        )
                        Text("${person.faceCount} photos", fontSize = 11.sp, color = Color(0xFF80868B))
                    }
                }
            }
        }
    }
}

@Composable
fun PersonPhotosScreen(
    api: PhotoApi,
    baseUrl: String,
    personId: Long,
    displayName: String?,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var photos by remember { mutableStateOf<List<PhotoResponse>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(personId) {
        loading = true
        scope.launch {
            runCatching { api.getPersonPhotos(personId) }.onSuccess { photos = it }
            loading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(com.sevis.photos.ui.GlassPageBackground)) {
        PaneHeader(
            title = displayName ?: "Person $personId",
            subtitle = "${photos.size} photo${if (photos.size != 1) "s" else ""}",
            onBack = onBack
        )

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            photos.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No photos found for this person", fontSize = 14.sp, color = Color(0xFF5F6368))
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(4.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(photos, key = { it.id }) { photo ->
                    AsyncImage(
                        model = "$baseUrl/photo-service/api/photos/${photo.id}/content?maxDimension=400",
                        contentDescription = photo.originalFilename,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
            }
        }
    }
}
