package com.example.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.MomentPost
import com.example.data.model.MomentTranslationState
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.ui.components.KelMiAvatar
import com.example.util.LocationTimeHelper
import com.example.util.ScreenshotHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MomentsTab(
    momentsState: UiState<List<MomentPost>>,
    userProfile: UserProfile?,
    currentUserId: String,
    momentTranslations: Map<String, MomentTranslationState>,
    onPostMoment: (content: String, languageTag: String, locationTag: String) -> Unit,
    onToggleLike: (MomentPost) -> Unit,
    onTranslateMoment: (MomentPost, String) -> Unit,
    onShowBanner: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showComposeDialog by remember { mutableStateOf(false) }
    var draftContent by remember { mutableStateOf("") }

    val preferredLanguage = userProfile?.preferredLanguage ?: "English"
    val defaultLocation = remember(userProfile?.locationLabel, userProfile?.timeZoneId) {
        val loc = userProfile?.locationLabel?.ifBlank { "Earth" } ?: "Earth"
        val clock = LocationTimeHelper.formatShortClock(userProfile?.timeZoneId)
        "$loc · $clock"
    }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showComposeDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("create_moment_fab")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Post Moment")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Moments",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "WeChat & QQ status circle · Vetny auto-ready",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = {
                        scope.launch {
                            ScreenshotHelper.captureAndShareScreenshot(context)
                                .onSuccess { onShowBanner("Moments screenshot captured") }
                                .onFailure { onShowBanner(it.localizedMessage ?: "Screenshot failed") }
                        }
                    }
                ) {
                    Icon(Icons.Rounded.PhotoCamera, contentDescription = "Screenshot Moments")
                }
            }

            when (momentsState) {
                is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> {
                    Text(
                        text = momentsState.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(20.dp)
                    )
                }
                is UiState.Success -> {
                    if (momentsState.data.isEmpty()) {
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
                                Text(
                                    text = "Share Your First Moment",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Post life updates with location & local time. Friends worldwide can translate them with Vetny.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(onClick = { showComposeDialog = true }) {
                                    Text("Write Moment")
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(momentsState.data, key = { it.id }) { post ->
                                MomentCard(
                                    post = post,
                                    currentUserId = currentUserId,
                                    targetLanguage = preferredLanguage,
                                    translationState = momentTranslations[post.id],
                                    onLike = { onToggleLike(post) },
                                    onTranslate = { onTranslateMoment(post, preferredLanguage) }
                                )
                            }
                            item { Spacer(modifier = Modifier.height(72.dp)) }
                        }
                    }
                }
            }
        }
    }

    if (showComposeDialog) {
        AlertDialog(
            onDismissRequest = { showComposeDialog = false },
            title = { Text("Post to Kel mi Moments") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = draftContent,
                        onValueChange = { draftContent = it },
                        label = { Text("What's on your mind?") },
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth().testTag("moment_content_input")
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(defaultLocation, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (draftContent.isNotBlank()) {
                            onPostMoment(draftContent, preferredLanguage, defaultLocation)
                            draftContent = ""
                            showComposeDialog = false
                        }
                    },
                    enabled = draftContent.isNotBlank(),
                    modifier = Modifier.testTag("publish_moment_button")
                ) { Text("Post") }
            },
            dismissButton = {
                TextButton(onClick = { showComposeDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun MomentCard(
    post: MomentPost,
    currentUserId: String,
    targetLanguage: String,
    translationState: MomentTranslationState?,
    onLike: () -> Unit,
    onTranslate: () -> Unit
) {
    val liked = currentUserId in post.likedByUids
    val timeLabel = remember(post.createdAt) {
        val date = post.createdAt?.toDate() ?: Date()
        SimpleDateFormat("MMM d · HH:mm", Locale.getDefault()).format(date)
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KelMiAvatar(name = post.authorName, colorHex = post.authorAvatarColor, size = 42.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(post.authorName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        text = "@${post.authorHandle} · $timeLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(text = post.content, style = MaterialTheme.typography.bodyLarge)

            if (!post.locationTag.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.LocationOn, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(post.locationTag, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }

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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onLike) {
                        Icon(
                            imageVector = if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = "Like Moment",
                            tint = if (liked) Color(0xFFE11D48) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text("${post.likedByUids.size}", style = MaterialTheme.typography.labelMedium)
                }
                TextButton(onClick = onTranslate) {
                    Icon(Icons.Rounded.Translate, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Translate with Vetny")
                }
            }
        }
    }
}
