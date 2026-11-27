package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.DecryptedMessageEntity
import com.example.data.model.ActiveChatSession
import com.example.data.model.UserProfile
import com.example.data.model.VetnyTranslationResult
import com.example.ui.components.KelMiAvatar
import com.example.ui.components.LanguagePickerDialog
import com.example.ui.components.ReadReceiptTicks
import com.example.ui.theme.IncomingBubbleDark
import com.example.ui.theme.IncomingBubbleLight
import com.example.ui.theme.OutgoingBubbleDark
import com.example.ui.theme.OutgoingBubbleLight
import com.example.util.DevicePermissionsHelper
import com.example.util.LocationTimeHelper
import com.example.util.ScreenshotHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EncryptedChatScreen(
    session: ActiveChatSession,
    messages: List<DecryptedMessageEntity>,
    userProfile: UserProfile?,
    draftTranslation: VetnyTranslationResult?,
    isDraftTranslating: Boolean,
    onBack: () -> Unit,
    onSendMessage: (String, String) -> Unit,
    onTranslateMessage: (DecryptedMessageEntity, String) -> Unit,
    onTranslateDraft: (String, String) -> Unit,
    onClearDraftTranslation: () -> Unit,
    onToggleAutoTranslate: (String, Boolean) -> Unit,
    onToggleVerified: () -> Unit,
    onLocationFetched: (String) -> Unit,
    onStartVoiceCall: () -> Unit,
    onStartVideoCall: () -> Unit,
    onShowBanner: (String) -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }
    var showSafetyDialog by remember { mutableStateOf(false) }
    var showTargetLanguagePicker by remember { mutableStateOf(false) }
    var draftTargetLang by remember(session.peerPreferredLanguage) {
        mutableStateOf(session.peerPreferredLanguage.ifBlank { "English" })
    }
    var showDraftLangPicker by remember { mutableStateOf(false) }

    val myTargetLang = userProfile?.preferredLanguage ?: "English"
    val autoTranslate = userProfile?.autoTranslateEnabled ?: true
    val listState = rememberLazyListState()

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms.values.any { it }
        val tzId = LocationTimeHelper.getCurrentTimeZoneId()
        val timeStr = LocationTimeHelper.formatLocalTime(tzId)
        if (granted) {
            scope.launch {
                val loc = LocationTimeHelper.fetchCurrentLocationLabel(context) ?: "Current Location"
                onLocationFetched(loc)
                onSendMessage("$loc · Local Time: $timeStr", "LOCATION_TIME")
            }
        } else {
            val fallbackLoc = userProfile?.locationLabel?.ifBlank { "Timezone $tzId" } ?: "Timezone $tzId"
            onSendMessage("$fallbackLoc · Local Time: $timeStr", "LOCATION_TIME")
        }
    }

    val voiceCallPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onStartVoiceCall()
        } else {
            onShowBanner("Microphone permission is needed for voice calls")
        }
    }

    val videoCallPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms[Manifest.permission.CAMERA] == true && perms[Manifest.permission.RECORD_AUDIO] == true) {
            onStartVideoCall()
        } else {
            onShowBanner("Camera & Microphone permissions are needed for video calls")
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("chat_back_button")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to Chats")
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { showSafetyDialog = true }
                    ) {
                        KelMiAvatar(
                            name = session.peerName,
                            colorHex = session.peerAvatarColorHex,
                            size = 38.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = session.peerName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (session.peerAgeYears > 0 && !session.isSelfVault) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${session.peerAgeYears}y",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (session.isVerified) Icons.Rounded.VerifiedUser else Icons.Rounded.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                val peerClock = if (session.peerTimeZoneId.isNotBlank()) {
                                    " · ${LocationTimeHelper.formatShortClock(session.peerTimeZoneId)}"
                                } else ""
                                Text(
                                    text = "Signal E2EE$peerClock",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (DevicePermissionsHelper.hasMicrophonePermission(context)) {
                                onStartVoiceCall()
                            } else {
                                voiceCallPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        modifier = Modifier.testTag("voice_call_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Call,
                            contentDescription = "Start encrypted voice call",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = {
                            if (DevicePermissionsHelper.hasCameraPermission(context) &&
                                DevicePermissionsHelper.hasMicrophonePermission(context)
                            ) {
                                onStartVideoCall()
                            } else {
                                videoCallPermLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.CAMERA,
                                        Manifest.permission.RECORD_AUDIO
                                    )
                                )
                            }
                        },
                        modifier = Modifier.testTag("video_call_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Videocam,
                            contentDescription = "Start encrypted video call",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { showSafetyDialog = true }) {
                        Icon(
                            imageVector = Icons.Rounded.VerifiedUser,
                            contentDescription = "Verify Safety Number",
                            tint = if (session.isVerified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            // Vetny Real-Time Translation Ribbon
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Vetny Live -> $myTargetLang",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.clickable { showTargetLanguagePicker = true }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = autoTranslate,
                            onClick = { onToggleAutoTranslate(myTargetLang, !autoTranslate) },
                            label = { Text(if (autoTranslate) "Auto ON" else "Auto OFF") }
                        )
                        TextButton(onClick = { showTargetLanguagePicker = true }) {
                            Text("Lang")
                        }
                    }
                }
            }

            // Messages List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "e2ee_banner") {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Messages are secured with Signal X3DH & Double Ratchet AES-256-GCM. Tap the header to verify your 60-digit Safety Number.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                items(messages, key = { it.compositeKey }) { msg ->
                    val alignRight = msg.senderId == msg.ownerUid
                    EncryptedMessageBubble(
                        message = msg,
                        isOutgoing = alignRight,
                        targetLanguage = myTargetLang,
                        onTranslate = { onTranslateMessage(msg, myTargetLang) }
                    )
                }
            }

            // Draft Pre-Send Translation Preview Card
            if (isDraftTranslating || draftTranslation != null) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Vetny Draft -> $draftTargetLang",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            TextButton(onClick = onClearDraftTranslation) {
                                Text("Dismiss")
                            }
                        }
                        if (isDraftTranslating) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Translating draft...", style = MaterialTheme.typography.bodySmall)
                            }
                        } else if (draftTranslation != null) {
                            Text(
                                text = draftTranslation.translatedText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            if (draftTranslation.culturalNote.isNotBlank()) {
                                Text(
                                    text = draftTranslation.culturalNote,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        inputText = draftTranslation.translatedText
                                        onClearDraftTranslation()
                                    }
                                ) {
                                    Text("Use in Message")
                                }
                                OutlinedButton(
                                    onClick = {
                                        onSendMessage(draftTranslation.translatedText, "TEXT")
                                        inputText = ""
                                    }
                                ) {
                                    Text("Encrypt & Send Now")
                                }
                            }
                        }
                    }
                }
            }

            // Composer Bar
            Surface(
                tonalElevation = 3.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                if (LocationTimeHelper.hasLocationPermission(context)) {
                                    scope.launch {
                                        val tzId = LocationTimeHelper.getCurrentTimeZoneId()
                                        val timeStr = LocationTimeHelper.formatLocalTime(tzId)
                                        val loc = LocationTimeHelper.fetchCurrentLocationLabel(context)
                                            ?: userProfile?.locationLabel?.ifBlank { "Location Active" }
                                            ?: "Location Active"
                                        onLocationFetched(loc)
                                        onSendMessage("$loc · Local Time: $timeStr", "LOCATION_TIME")
                                    }
                                } else {
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                }
                            },
                            modifier = Modifier.testTag("send_location_time_button")
                        ) {
                            Icon(Icons.Rounded.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Location & Time")
                        }

                        TextButton(
                            onClick = { showDraftLangPicker = true },
                            modifier = Modifier.testTag("draft_target_lang_button")
                        ) {
                            Icon(Icons.Rounded.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("To: $draftTargetLang", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        IconButton(
                            onClick = {
                                scope.launch {
                                    ScreenshotHelper.captureAndShareScreenshot(context)
                                        .onSuccess { res ->
                                            onShowBanner(
                                                if (res.savedToGallery) "Screenshot saved & ready to share"
                                                else "Screenshot ready to share"
                                            )
                                        }
                                        .onFailure { onShowBanner(it.localizedMessage ?: "Screenshot failed") }
                                }
                            },
                            modifier = Modifier.testTag("chat_screenshot_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PhotoCamera,
                                contentDescription = "Capture screenshot",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = { Text("Encrypted message...") },
                            maxLines = 4,
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chat_message_input")
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = { onTranslateDraft(inputText, draftTargetLang) },
                            enabled = inputText.isNotBlank() && !isDraftTranslating,
                            modifier = Modifier.testTag("vetny_translate_draft_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Translate,
                                contentDescription = "Translate draft with Vetny",
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    if (inputText.isNotBlank()) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable(enabled = inputText.isNotBlank()) {
                                    val text = inputText.trim()
                                    if (text.isNotEmpty()) {
                                        onSendMessage(text, "TEXT")
                                        inputText = ""
                                    }
                                }
                                .testTag("send_encrypted_message_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                contentDescription = "Encrypt and send",
                                tint = if (inputText.isNotBlank()) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSafetyDialog) {
        AlertDialog(
            onDismissRequest = { showSafetyDialog = false },
            title = { Text("Signal Safety Number & Peer Info") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!session.isSelfVault) {
                        val meta = buildList {
                            if (session.peerAgeYears > 0) add("Age ${session.peerAgeYears}")
                            if (session.peerGender.isNotBlank()) add(session.peerGender)
                            if (session.peerLocationLabel.isNotBlank()) add(session.peerLocationLabel)
                        }.joinToString(" · ")
                        if (meta.isNotBlank()) {
                            Text(text = meta, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                        if (session.peerInterests.isNotEmpty()) {
                            Text(
                                text = "Likes: ${session.peerInterests.joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (session.peerTimeZoneId.isNotBlank()) {
                            Text(
                                text = "Peer Local Time: ${LocationTimeHelper.formatLocalTime(session.peerTimeZoneId)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        HorizontalDivider()
                    }
                    Text(
                        text = "Compare this 60-digit Safety Number with ${session.peerName} to confirm zero man-in-the-middle interception:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = session.safetyNumber,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                if (!session.isSelfVault) {
                    Button(onClick = {
                        onToggleVerified()
                        showSafetyDialog = false
                    }) {
                        Text(if (session.isVerified) "Unverify" else "Mark Verified")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showSafetyDialog = false }) { Text("Close") }
            }
        )
    }

    if (showTargetLanguagePicker) {
        LanguagePickerDialog(
            title = "Translate Incoming Messages To",
            currentLanguage = myTargetLang,
            onSelectLanguage = { newLang -> onToggleAutoTranslate(newLang, autoTranslate) },
            onDismiss = { showTargetLanguagePicker = false }
        )
    }

    if (showDraftLangPicker) {
        LanguagePickerDialog(
            title = "Translate Outgoing Draft To",
            currentLanguage = draftTargetLang,
            onSelectLanguage = { draftTargetLang = it },
            onDismiss = { showDraftLangPicker = false }
        )
    }
}

@Composable
private fun EncryptedMessageBubble(
    message: DecryptedMessageEntity,
    isOutgoing: Boolean,
    targetLanguage: String,
    onTranslate: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val bubbleColor = when {
        isOutgoing && isDark -> OutgoingBubbleDark
        isOutgoing -> OutgoingBubbleLight
        isDark -> IncomingBubbleDark
        else -> IncomingBubbleLight
    }
    val timeText = remember(message.timestampMillis) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestampMillis))
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isOutgoing) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = bubbleColor),
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isOutgoing) 18.dp else 4.dp,
                bottomEnd = if (isOutgoing) 4.dp else 18.dp
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.widthIn(max = 310.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                if (message.messageType == "LOCATION_TIME") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Shared Location & Time",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                } else if (message.messageType == "CALL_LOG") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Call,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "End-to-End Encrypted Call",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Text(
                    text = message.plaintext,
                    style = MaterialTheme.typography.bodyLarge
                )

                if (!message.translatedText.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Vetny (${message.detectedSourceLanguage ?: "Auto"} -> ${message.targetLanguage ?: targetLanguage})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = message.translatedText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            if (!message.culturalNote.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = message.culturalNote,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (message.preKeyMessage) "X3DH" else "Ratchet #${message.messageNumber}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (message.translatedText.isNullOrBlank() && message.messageType == "TEXT") {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Vetny",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable(onClick = onTranslate)
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = timeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (isOutgoing) {
                            Spacer(modifier = Modifier.width(4.dp))
                            ReadReceiptTicks(
                                status = message.receiptStatus,
                                iconSize = 15.dp,
                                modifier = Modifier.testTag("receipt_tick_${message.messageId}")
                            )
                        }
                    }
                }
            }
        }
    }
}
