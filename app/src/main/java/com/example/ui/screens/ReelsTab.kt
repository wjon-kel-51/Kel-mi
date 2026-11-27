package com.example.ui.screens

import android.net.Uri
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Comment
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.MomentTranslationState
import com.example.data.model.ReelComment
import com.example.data.model.ReelPost
import com.example.data.model.StoryPost
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.ui.components.CreateReelDialog
import com.example.ui.components.CreateStoryDialog
import com.example.ui.components.KelMiAvatar
import com.example.ui.components.ReelCommentsDialog
import com.example.ui.components.StoryViewerDialog
import com.example.ui.components.colorFilterForName
import com.example.util.KelMiMusicLibrary
import com.example.util.KelMiMusicPlayer
import com.example.util.LocationTimeHelper
import com.example.util.MediaEncodeHelper
import com.example.util.ScreenshotHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ReelsTab(
    reelsState: UiState<List<ReelPost>>,
    storiesState: UiState<List<StoryPost>>,
    userProfile: UserProfile?,
    currentUserId: String,
    reelTranslations: Map<String, MomentTranslationState>,
    activeReelComments: List<ReelComment>,
    activeCommentReelId: String?,
    onPostReel: (
        caption: String,
        mediaType: String,
        mediaBase64: String?,
        localMediaUri: String?,
        durationSeconds: Int,
        songId: String?,
        songTitle: String?,
        songArtist: String?,
        locationTag: String,
        filterName: String,
        isPublic: Boolean
    ) -> Unit,
    onToggleLike: (ReelPost) -> Unit,
    onToggleBookmark: (ReelPost) -> Unit,
    onTranslateReel: (ReelPost, String) -> Unit,
    onOpenComments: (String) -> Unit,
    onCloseComments: () -> Unit,
    onAddComment: (String, String) -> Unit,
    onPostStory: (caption: String, mediaBase64: String?, songTitle: String?, locationTag: String?) -> Unit,
    onViewStory: (StoryPost) -> Unit,
    onShowBanner: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showCreateReel by remember { mutableStateOf(false) }
    var showCreateStory by remember { mutableStateOf(false) }
    var viewingStory by remember { mutableStateOf<StoryPost?>(null) }

    val preferredLanguage = userProfile?.preferredLanguage ?: "English"
    val defaultLocation = remember(userProfile?.locationLabel, userProfile?.timeZoneId) {
        val loc = userProfile?.locationLabel?.ifBlank { "Global" } ?: "Global"
        val clock = LocationTimeHelper.formatShortClock(userProfile?.timeZoneId)
        "$loc · $clock"
    }

    DisposableEffect(Unit) {
        onDispose { KelMiMusicPlayer.stop() }
    }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateReel = true },
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("create_reel_fab")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Post Reel or Photo")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 84.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "reels_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Reels & Stories",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Videos · Photos · In-App Music · Vetny Translation",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                ScreenshotHelper.captureAndShareScreenshot(context)
                                    .onSuccess { onShowBanner("Reels screenshot captured") }
                                    .onFailure { onShowBanner(it.localizedMessage ?: "Screenshot failed") }
                            }
                        }
                    ) {
                        Icon(Icons.Rounded.PhotoCamera, contentDescription = "Screenshot Reels")
                    }
                }
            }

            // Instagram-Style Stories Strip
            item(key = "stories_strip") {
                val stories = (storiesState as? UiState.Success)?.data.orEmpty()
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "add_story") {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable { showCreateStory = true }
                                .testTag("add_story_button")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = "Add Story",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Your Story", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    items(stories, key = { it.id }) { story ->
                        val seen = currentUserId in story.viewedByUids
                        val ringBrush = if (seen) {
                            Brush.linearGradient(listOf(Color.Gray, Color.LightGray))
                        } else {
                            Brush.linearGradient(listOf(Color(0xFF07A96D), Color(0xFF4F46E5), Color(0xFFEC4899)))
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable {
                                viewingStory = story
                                onViewStory(story)
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(66.dp)
                                    .clip(CircleShape)
                                    .border(2.5.dp, ringBrush, CircleShape)
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                KelMiAvatar(
                                    name = story.authorName,
                                    colorHex = story.authorAvatarColor,
                                    size = 54.dp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = story.authorName.take(10),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            when (reelsState) {
                is UiState.Loading -> {
                    item(key = "loading_reels") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator() }
                    }
                }
                is UiState.Error -> {
                    item(key = "err_reels") {
                        Text(
                            text = reelsState.message,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }
                is UiState.Success -> {
                    if (reelsState.data.isEmpty()) {
                        item(key = "empty_reels") {
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.VideoLibrary,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(44.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Post the First Kel mi Reel",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Share videos or photos with filters and built-in songs from Kel mi Studio.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(onClick = { showCreateReel = true }) {
                                        Text("Create Reel")
                                    }
                                }
                            }
                        }
                    } else {
                        items(reelsState.data, key = { it.id }) { reel ->
                            ReelFeedCard(
                                reel = reel,
                                currentUserId = currentUserId,
                                targetLanguage = preferredLanguage,
                                translationState = reelTranslations[reel.id],
                                onLike = { onToggleLike(reel) },
                                onBookmark = { onToggleBookmark(reel) },
                                onTranslate = { onTranslateReel(reel, preferredLanguage) },
                                onOpenComments = { onOpenComments(reel.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateReel) {
        CreateReelDialog(
            defaultLocationTag = defaultLocation,
            onDismiss = { showCreateReel = false },
            onPublish = { caption, mediaType, mediaBase64, localMediaUri, durationSec, song, filterName, isPublic ->
                onPostReel(
                    caption,
                    mediaType,
                    mediaBase64,
                    localMediaUri,
                    durationSec,
                    song?.id,
                    song?.title,
                    song?.artist,
                    defaultLocation,
                    filterName,
                    isPublic
                )
            }
        )
    }

    if (showCreateStory) {
        CreateStoryDialog(
            defaultLocation = defaultLocation,
            onDismiss = { showCreateStory = false },
            onPublish = { caption, mediaBase64, songTitle ->
                onPostStory(caption, mediaBase64, songTitle, defaultLocation)
            }
        )
    }

    viewingStory?.let { story ->
        StoryViewerDialog(story = story, onDismiss = { viewingStory = null })
    }

    activeCommentReelId?.let { reelId ->
        ReelCommentsDialog(
            comments = activeReelComments,
            onPostComment = { text -> onAddComment(reelId, text) },
            onDismiss = onCloseComments
        )
    }
}

@Composable
private fun ReelFeedCard(
    reel: ReelPost,
    currentUserId: String,
    targetLanguage: String,
    translationState: MomentTranslationState?,
    onLike: () -> Unit,
    onBookmark: () -> Unit,
    onTranslate: () -> Unit,
    onOpenComments: () -> Unit
) {
    val bmp = remember(reel.mediaBase64) { MediaEncodeHelper.decodeBase64Bitmap(reel.mediaBase64) }
    val liked = currentUserId in reel.likedByUids
    val saved = currentUserId in reel.bookmarkedByUids
    val currentSongId by KelMiMusicPlayer.currentSongId.collectAsState()
    val isPlayingSong by KelMiMusicPlayer.isPlaying.collectAsState()
    val song = remember(reel.songId) { KelMiMusicLibrary.findById(reel.songId) }
    val playingThisReelSong = song != null && isPlayingSong && currentSongId == song.id

    var playVideoInline by remember(reel.id) { mutableStateOf(false) }
    var showHeartPop by remember { mutableStateOf(false) }

    LaunchedEffect(showHeartPop) {
        if (showHeartPop) {
            delay(750L)
            showHeartPop = false
        }
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Author Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                KelMiAvatar(name = reel.authorName, colorHex = reel.authorAvatarColor, size = 42.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(reel.authorName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("@${reel.authorHandle}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!reel.locationTag.isNullOrBlank()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.Rounded.LocationOn, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = reel.locationTag,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = if (reel.visibility == "PUBLIC") "Global" else "Friends",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // Media Canvas (VideoView or Filtered Photo + Double-Tap Like)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(270.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(listOf(Color(0xFF064E3B), Color(0xFF312E81)))
                    )
                    .pointerInput(reel.id) {
                        detectTapGestures(
                            onDoubleTap = {
                                showHeartPop = true
                                if (!liked) onLike()
                            },
                            onTap = {
                                if (reel.mediaType == "VIDEO" && !reel.localMediaUri.isNullOrBlank()) {
                                    playVideoInline = !playVideoInline
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (playVideoInline && !reel.localMediaUri.isNullOrBlank()) {
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                setVideoURI(Uri.parse(reel.localMediaUri))
                                setOnPreparedListener { mp ->
                                    mp.isLooping = true
                                    start()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = reel.caption,
                        contentScale = ContentScale.Crop,
                        colorFilter = colorFilterForName(reel.filterName),
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(18.dp)) {
                        Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(44.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = reel.caption,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (reel.mediaType == "VIDEO" && !playVideoInline) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.55f),
                        modifier = Modifier.size(54.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = "Play video", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                    }
                }

                if (!reel.filterName.isNullOrBlank() && reel.filterName != "Original") {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.55f),
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                    ) {
                        Text(reel.filterName, style = MaterialTheme.typography.labelSmall, color = Color.White, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = showHeartPop,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Favorite,
                        contentDescription = null,
                        tint = Color(0xFFFB7185),
                        modifier = Modifier.size(72.dp)
                    )
                }
            }

            // Attached In-App Song Pill
            if (!reel.songTitle.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { song?.let { KelMiMusicPlayer.toggleSong(it) } }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (playingThisReelSong) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = "Play or pause song",
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${reel.songTitle} · ${reel.songArtist ?: "Kel mi Studio"}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (playingThisReelSong) {
                            Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                }
            }

            // Caption
            Text(text = reel.caption, style = MaterialTheme.typography.bodyLarge)

            // Vetny AI Translation Result
            if (translationState != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.tertiary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Vetny -> $targetLanguage", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        when {
                            translationState.isLoading -> CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            translationState.result != null -> Text(translationState.result.translatedText, style = MaterialTheme.typography.bodyMedium)
                            translationState.error != null -> Text(translationState.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            // Instagram-Style Action Row (Like, Comment, Bookmark, Vetny Translate)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onLike) {
                        Icon(
                            imageVector = if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = "Like Reel",
                            tint = if (liked) Color(0xFFE11D48) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text("${reel.likedByUids.size}", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onOpenComments) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Comment,
                            contentDescription = "Comments",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = onTranslate) {
                        Icon(Icons.Rounded.Translate, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Translate")
                    }
                }
                IconButton(onClick = onBookmark) {
                    Icon(
                        imageVector = if (saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                        contentDescription = "Bookmark Reel",
                        tint = if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
