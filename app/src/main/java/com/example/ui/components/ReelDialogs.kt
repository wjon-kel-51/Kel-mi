package com.example.ui.components

import android.Manifest
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.ReelComment
import com.example.data.model.ReelFilters
import com.example.data.model.StoryPost
import com.example.util.DevicePermissionsHelper
import com.example.util.KelMiMusicLibrary
import com.example.util.KelMiMusicPlayer
import com.example.util.KelMiSong
import com.example.util.MediaEncodeHelper
import kotlinx.coroutines.launch

fun colorFilterForName(filterName: String?): ColorFilter? {
    return when (filterName) {
        "Noir B&W" -> ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
        "Warm Gold" -> ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    1.15f, 0.05f, 0f, 0f, 12f,
                    0f, 1.05f, 0f, 0f, 4f,
                    0f, 0f, 0.85f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
        "Emerald Film" -> ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    0.92f, 0f, 0f, 0f, 0f,
                    0f, 1.12f, 0.04f, 0f, 6f,
                    0f, 0.03f, 0.98f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
        "Kyoto Cool" -> ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    0.92f, 0f, 0.04f, 0f, 0f,
                    0f, 1.0f, 0.05f, 0f, 0f,
                    0.04f, 0.04f, 1.16f, 0f, 8f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
        else -> null
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateReelDialog(
    defaultLocationTag: String,
    onDismiss: () -> Unit,
    onPublish: (
        caption: String,
        mediaType: String,
        mediaBase64: String?,
        localMediaUri: String?,
        durationSeconds: Int,
        song: KelMiSong?,
        filterName: String,
        isPublic: Boolean
    ) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var caption by remember { mutableStateOf("") }
    var mediaType by remember { mutableStateOf("IMAGE") }
    var mediaBase64 by remember { mutableStateOf<String?>(null) }
    var localMediaUri by remember { mutableStateOf<String?>(null) }
    var durationSeconds by remember { mutableIntStateOf(0) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var selectedSong by remember { mutableStateOf<KelMiSong?>(KelMiMusicLibrary.songs.first()) }
    var selectedFilter by remember { mutableStateOf("Original") }
    var isPublic by remember { mutableStateOf(true) }
    var showSongPicker by remember { mutableStateOf(false) }

    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val processed = MediaEncodeHelper.processPickedMedia(context, uri)
                if (processed != null) {
                    mediaType = processed.mediaType
                    mediaBase64 = processed.base64Preview
                    localMediaUri = uri.toString()
                    durationSeconds = processed.durationSeconds
                    previewBitmap = MediaEncodeHelper.decodeBase64Bitmap(processed.base64Preview)
                }
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bmp: Bitmap? ->
        if (bmp != null) {
            mediaType = "IMAGE"
            mediaBase64 = MediaEncodeHelper.compressBitmapToBase64(bmp, 640, 74)
            localMediaUri = null
            durationSeconds = 0
            previewBitmap = bmp
        }
    }

    val cameraPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) cameraLauncher.launch(null)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Reel / Photo Post") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            mediaPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        },
                        modifier = Modifier.weight(1f).testTag("pick_reel_media_button")
                    ) {
                        Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Video / Photo")
                    }
                    OutlinedButton(
                        onClick = {
                            if (DevicePermissionsHelper.hasCameraPermission(context)) {
                                cameraLauncher.launch(null)
                            } else {
                                cameraPermLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        modifier = Modifier.weight(1f).testTag("capture_reel_camera_button")
                    ) {
                        Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Camera")
                    }
                }

                if (previewBitmap != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(14.dp))
                    ) {
                        Image(
                            bitmap = previewBitmap!!.asImageBitmap(),
                            contentDescription = "Selected Reel media",
                            contentScale = ContentScale.Crop,
                            colorFilter = colorFilterForName(selectedFilter),
                            modifier = Modifier.fillMaxWidth().height(160.dp)
                        )
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color.Black.copy(alpha = 0.65f),
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                        ) {
                            Text(
                                text = if (mediaType == "VIDEO") "VIDEO · ${durationSeconds}s" else "PHOTO · $selectedFilter",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Instagram Photo Filters
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ReelFilters.options.forEach { filter ->
                            FilterChip(
                                selected = selectedFilter == filter,
                                onClick = { selectedFilter = filter },
                                label = { Text(filter) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    label = { Text("Caption (#hashtags, thoughts...)") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth().testTag("reel_caption_input")
                )

                // App Song Picker Row
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showSongPicker = true }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedSong?.title ?: "No Song Attached",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = selectedSong?.let { "${it.artist} · ${it.genre}" } ?: "Tap to choose song from Kel mi",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        TextButton(onClick = { showSongPicker = true }) { Text("Songs") }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Public Worldwide Reel", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = if (isPublic) "Visible to Kel mi community globally" else "Contacts only",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = isPublic, onCheckedChange = { isPublic = it })
                }

                if (defaultLocationTag.isNotBlank()) {
                    Text(
                        text = "Tag: $defaultLocationTag",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (caption.isNotBlank()) {
                        KelMiMusicPlayer.stop()
                        onPublish(
                            caption,
                            mediaType,
                            mediaBase64,
                            localMediaUri,
                            durationSeconds,
                            selectedSong,
                            selectedFilter,
                            isPublic
                        )
                        onDismiss()
                    }
                },
                enabled = caption.isNotBlank(),
                modifier = Modifier.testTag("publish_reel_button")
            ) { Text("Post Reel") }
        },
        dismissButton = {
            TextButton(onClick = {
                KelMiMusicPlayer.stop()
                onDismiss()
            }) { Text("Cancel") }
        }
    )

    if (showSongPicker) {
        SongPickerDialog(
            selectedSong = selectedSong,
            onSelectSong = {
                selectedSong = it
                showSongPicker = false
            },
            onDismiss = { showSongPicker = false }
        )
    }
}

@Composable
fun SongPickerDialog(
    selectedSong: KelMiSong?,
    onSelectSong: (KelMiSong?) -> Unit,
    onDismiss: () -> Unit
) {
    val currentId by KelMiMusicPlayer.currentSongId.collectAsState()
    val isPlaying by KelMiMusicPlayer.isPlaying.collectAsState()

    AlertDialog(
        onDismissRequest = {
            KelMiMusicPlayer.stop()
            onDismiss()
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Kel mi Sound Library")
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(KelMiMusicLibrary.songs, key = { it.id }) { song ->
                    val playingThis = isPlaying && currentId == song.id
                    val isSelected = selectedSong?.id == song.id
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                KelMiMusicPlayer.stop()
                                onSelectSong(song)
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { KelMiMusicPlayer.toggleSong(song) }) {
                                Icon(
                                    imageVector = if (playingThis) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                                    contentDescription = if (playingThis) "Stop preview" else "Preview song",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(song.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "${song.artist} · ${song.genre} · ${song.durationLabel}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (playingThis) {
                                Icon(Icons.Rounded.GraphicEq, contentDescription = "Playing", tint = MaterialTheme.colorScheme.primary)
                            } else if (isSelected) {
                                Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                KelMiMusicPlayer.stop()
                onSelectSong(null)
            }) { Text("No Song") }
        },
        dismissButton = {
            TextButton(onClick = {
                KelMiMusicPlayer.stop()
                onDismiss()
            }) { Text("Close") }
        }
    )
}

@Composable
fun ReelCommentsDialog(
    comments: List<ReelComment>,
    onPostComment: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var input by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Comments (${comments.size})") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (comments.isEmpty()) {
                        item {
                            Text(
                                text = "Be the first to leave an Instagram-style comment!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(comments, key = { it.id }) { comment ->
                            Row(verticalAlignment = Alignment.Top) {
                                KelMiAvatar(
                                    name = comment.authorName,
                                    colorHex = comment.authorAvatarColor,
                                    size = 30.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "${comment.authorName} · @${comment.authorHandle}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(text = comment.text, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text("Add a comment...") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            if (input.isNotBlank()) {
                                onPostComment(input)
                                input = ""
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send comment")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun CreateStoryDialog(
    defaultLocation: String,
    onDismiss: () -> Unit,
    onPublish: (caption: String, mediaBase64: String?, songTitle: String?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var caption by remember { mutableStateOf("") }
    var mediaBase64 by remember { mutableStateOf<String?>(null) }
    var selectedSong by remember { mutableStateOf<KelMiSong?>(KelMiMusicLibrary.songs.first()) }
    var showSongPicker by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val res = MediaEncodeHelper.processPickedMedia(context, uri)
                mediaBase64 = res?.base64Preview
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share Instagram Story") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.PhotoLibrary, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (mediaBase64 != null) "Photo Attached ✓" else "Add Story Photo (Optional)")
                }
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    label = { Text("Story text...") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(onClick = { showSongPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.MusicNote, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(selectedSong?.title ?: "Attach In-App Song")
                }
                if (defaultLocation.isNotBlank()) {
                    Text(
                        text = "Location tag: $defaultLocation",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (caption.isNotBlank()) {
                        onPublish(caption, mediaBase64, selectedSong?.title)
                        onDismiss()
                    }
                },
                enabled = caption.isNotBlank()
            ) { Text("Share Story") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showSongPicker) {
        SongPickerDialog(
            selectedSong = selectedSong,
            onSelectSong = {
                selectedSong = it
                showSongPicker = false
            },
            onDismiss = { showSongPicker = false }
        )
    }
}

@Composable
fun StoryViewerDialog(
    story: StoryPost,
    onDismiss: () -> Unit
) {
    val bmp = remember(story.mediaBase64) { MediaEncodeHelper.decodeBase64Bitmap(story.mediaBase64) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KelMiAvatar(name = story.authorName, colorHex = story.authorAvatarColor, size = 34.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(story.authorName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("@${story.authorHandle}", style = MaterialTheme.typography.labelSmall)
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close story")
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF065F46), Color(0xFF312E81))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = story.caption,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(240.dp)
                        )
                    }
                    Surface(
                        color = Color.Black.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp)
                    ) {
                        Text(
                            text = story.caption,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
                if (!story.songTitle.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.MusicNote, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(story.songTitle, style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (!story.locationTag.isNullOrBlank()) {
                    Text("Location: ${story.locationTag}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                Text("Seen by ${story.viewedByUids.size} people", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}
