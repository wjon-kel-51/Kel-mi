package com.example.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.model.SupportedLanguages
import com.example.data.model.UserProfile
import com.example.data.remote.KelMiRepository
import com.example.util.QrCodeHelper
import com.example.util.ScreenshotHelper
import kotlinx.coroutines.launch

@Composable
fun KelMiAvatar(
    name: String,
    colorHex: String,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier
) {
    val bg = remember(colorHex) {
        try {
            Color(AndroidColor.parseColor(colorHex))
        } catch (_: Exception) {
            Color(0xFF07A96D)
        }
    }
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "K"
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun LanguagePickerDialog(
    title: String,
    currentLanguage: String,
    onSelectLanguage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var customLanguage by remember { mutableStateOf("") }

    val filtered = remember(searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isBlank()) SupportedLanguages.worldLanguages
        else SupportedLanguages.worldLanguages.filter {
            it.name.lowercase().contains(q) ||
                it.nativeLabel.lowercase().contains(q) ||
                it.region.lowercase().contains(q)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Language,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search all world languages (e.g. 中文, Swahili)") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filtered, key = { it.name }) { lang ->
                        val selected = lang.name.equals(currentLanguage, ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) MaterialTheme.colorScheme.tertiaryContainer else Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectLanguage(lang.name)
                                    onDismiss()
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${lang.name} · ${lang.nativeLabel}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = lang.region,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = customLanguage,
                        onValueChange = { customLanguage = it },
                        label = { Text("Or type any dialect/language") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            val clean = customLanguage.trim()
                            if (clean.isNotEmpty()) {
                                onSelectLanguage(clean)
                                onDismiss()
                            }
                        }
                    ) {
                        Text("Use")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MyQrCardDialog(
    profile: UserProfile,
    safetyFingerprint: String,
    onOpenScanner: () -> Unit = {},
    onShowBanner: (String) -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val qrUri = remember(profile.userId, profile.kelMiHandle, profile.qqNumber, profile.displayName) {
        QrCodeHelper.buildContactQrUri(
            uid = profile.userId,
            handle = profile.kelMiHandle,
            qqNumber = profile.qqNumber,
            displayName = profile.displayName
        )
    }
    val qrBitmap = remember(qrUri) { QrCodeHelper.generateQrBitmap(qrUri, 640) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "My Kel mi QR Card",
                    style = MaterialTheme.typography.titleLarge
                )
                TextButton(
                    onClick = {
                        onDismiss()
                        onOpenScanner()
                    },
                    modifier = Modifier.testTag("qr_card_open_scanner_button")
                ) {
                    Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Scan")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            KelMiAvatar(
                                name = profile.displayName.ifBlank { "K" },
                                colorHex = KelMiRepository.pickAvatarColor(profile.userId),
                                size = 40.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = profile.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "@${profile.kelMiHandle} · ID ${profile.qqNumber}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (profile.ageYears > 0 || profile.locationLabel.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            val meta = buildList {
                                if (profile.ageYears > 0) add("${profile.ageYears} yrs")
                                if (profile.gender.isNotBlank()) add(profile.gender)
                                if (profile.locationLabel.isNotBlank()) add(profile.locationLabel)
                            }.joinToString(" · ")
                            Text(
                                text = meta,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White,
                            shadowElevation = 2.dp
                        ) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "Kel mi contact QR code",
                                modifier = Modifier
                                    .size(210.dp)
                                    .padding(10.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Key: $safetyFingerprint",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                ScreenshotHelper.saveBitmapToGallery(
                                    context = context,
                                    bitmap = qrBitmap,
                                    filePrefix = "kelmi_qr_${profile.kelMiHandle}"
                                ).onSuccess { res ->
                                    onShowBanner(
                                        if (res.savedToGallery) "QR saved to Pictures/KelMi"
                                        else "Saved ${res.fileName}"
                                    )
                                }.onFailure { err ->
                                    onShowBanner(err.localizedMessage ?: "Failed to save QR")
                                }
                            }
                        },
                        modifier = Modifier.testTag("qr_card_save_button")
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save QR")
                    }
                    OutlinedButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(qrUri))
                            onShowBanner("Copied Kel mi QR link")
                        },
                        modifier = Modifier.testTag("qr_card_copy_link_button")
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy Link")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Friends worldwide can scan this QR code in WeChat/QQ style to add you and start a Signal E2EE chat.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    QrCodeHelper.shareQrCard(
                        context = context,
                        qrBitmap = qrBitmap,
                        displayName = profile.displayName,
                        handle = profile.kelMiHandle,
                        qqNumber = profile.qqNumber
                    )
                },
                modifier = Modifier.testTag("qr_card_share_button")
            ) {
                Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Share QR")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Done") }
        }
    )
}
