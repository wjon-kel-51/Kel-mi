package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MovieCreation
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AiStreamState
import com.example.data.model.UiState
import com.example.data.model.VetnyAgentMode
import com.example.data.model.VetnyChatModel
import com.example.data.model.VetnyGroundingPayload
import com.example.data.model.VetnyImageAspect
import com.example.data.model.VetnyImageModel
import com.example.data.model.VetnyLiveSessionState
import com.example.data.model.VetnyMessage
import com.example.data.remote.VetnyAiService
import com.example.ui.components.LanguagePickerDialog
import com.example.ui.components.ReadReceiptTicks
import com.example.ui.components.VetnyGoogleMapDialog
import com.example.ui.components.VetnyGroundingSection
import com.example.ui.components.VetnyLiveVoicePanel
import com.example.util.LocationTimeHelper
import com.example.util.MediaEncodeHelper
import com.example.util.ScreenshotHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VetnyChatScreen(
    messagesState: UiState<List<VetnyMessage>>,
    streamState: AiStreamState,
    liveVoiceState: VetnyLiveSessionState,
    activeMode: VetnyAgentMode,
    activeChatModel: VetnyChatModel,
    activeImageModel: VetnyImageModel,
    activeImageAspect: VetnyImageAspect,
    defaultTargetLanguage: String,
    userLocationLabel: String?,
    currentCoordinates: Pair<Double, Double>?,
    onBack: () -> Unit,
    onSelectMode: (VetnyAgentMode) -> Unit,
    onSelectChatModel: (VetnyChatModel) -> Unit,
    onSelectImageModel: (VetnyImageModel) -> Unit,
    onSelectImageAspect: (VetnyImageAspect) -> Unit,
    onSendPrompt: (prompt: String, targetLanguage: String, attachedBitmap: Bitmap?) -> Unit,
    onStartLiveVoice: (targetLanguage: String, interpreterMode: Boolean, voiceName: String) -> Unit,
    onStopLiveVoice: (targetLanguage: String) -> Unit,
    onCoordinatesUpdated: (Double, Double) -> Unit,
    onClearConversation: () -> Unit,
    onPostPhotoToReels: (Bitmap, String) -> Unit,
    onShowBanner: (String) -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var promptText by remember { mutableStateOf("") }
    var targetLanguage by remember(defaultTargetLanguage) { mutableStateOf(defaultTargetLanguage) }
    var showLanguagePicker by remember { mutableStateOf(false) }
    var showModelMenu by remember { mutableStateOf(false) }
    var showGoogleMap by remember { mutableStateOf(false) }
    var mapInitialQuery by remember { mutableStateOf<String?>(null) }
    var attachedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(Unit) {
        if (LocationTimeHelper.hasLocationPermission(context) && currentCoordinates == null) {
            LocationTimeHelper.fetchCurrentCoordinates(context)?.let { (lat, lng) ->
                onCoordinatesUpdated(lat, lng)
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val decoded = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            BitmapFactory.decodeStream(stream)
                        }
                    } catch (_: Exception) {
                        null
                    }
                }
                if (decoded != null) {
                    attachedBitmap = decoded
                } else {
                    onShowBanner("Could not load selected photo")
                }
            }
        }
    }

    val messageList = (messagesState as? UiState.Success)?.data.orEmpty()
    LaunchedEffect(messageList.size, streamState) {
        val hasStreamItem = streamState is AiStreamState.Loading ||
            streamState is AiStreamState.GeneratingImage ||
            streamState is AiStreamState.Streaming ||
            streamState is AiStreamState.Error
        if (hasStreamItem) {
            listState.animateScrollToItem(messageList.size)
        } else if (messageList.isNotEmpty()) {
            listState.animateScrollToItem(messageList.lastIndex)
        }
    }

    val quickPrompts = remember(activeMode, targetLanguage) {
        when (activeMode) {
            VetnyAgentMode.SUPPORT -> listOf(
                "What can you help me with in Kel mi?",
                "Summarize our conversation so far",
                "Plan a weekend itinerary in Shanghai",
                "Generate a photo of a neon rainy alley"
            )
            VetnyAgentMode.VOICE_LIVE -> listOf(
                "Translate everything I say into $targetLanguage",
                "Help me practice ordering dinner in Tokyo",
                "Plan my weekend trip out loud"
            )
            VetnyAgentMode.SEARCH -> listOf(
                "Latest global tech & AI headlines today",
                "Current weather & travel tips in Tokyo",
                "Major football matches and results this week",
                "Visa-free travel updates for China & East Asia"
            )
            VetnyAgentMode.MAPS -> listOf(
                "Best ramen & coffee spots near Shibuya Station",
                "Top boutique hotels near The Bund in Shanghai",
                "Quiet cafes with Wi-Fi near me",
                "Landmarks & night markets to visit tonight"
            )
            VetnyAgentMode.BOOKING -> listOf(
                "Book a 3-night boutique hotel in Tokyo for 2 guests",
                "Reserve dinner for 4 tonight at 7:30 PM",
                "Help me book a roundtrip flight to Beijing next month",
                "Confirm my booking summary & reference"
            )
            VetnyAgentMode.TRANSLATE -> listOf(
                "Nice to meet you! Where are you from?",
                "你好，很高兴认识你！你现在几点了？",
                "How do I ask for directions politely in $targetLanguage?",
                "Habari! Natumaini una siku njema."
            )
            VetnyAgentMode.IMAGE -> listOf(
                "Futuristic cyberpunk Shanghai skyline at twilight, emerald neon lights",
                "Cute 3D clay panda wearing studio headphones, soft lighting",
                "Luxury rooftop lounge overlooking Kyoto mountains at sunset",
                "Minimalist editorial travel poster of the Golden Gate Bridge"
            )
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("vetny_back_button")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.img_vetny_avatar),
                            contentDescription = "Vetny AI",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Vetny Concierge & Studio",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = when (activeMode) {
                                    VetnyAgentMode.IMAGE -> "${activeImageModel.displayName} · Photo Studio"
                                    VetnyAgentMode.VOICE_LIVE -> "Gemini 3.8 Live · Voice"
                                    VetnyAgentMode.SEARCH -> "Gemini 4 Hydrogen · Google Search"
                                    VetnyAgentMode.MAPS -> "Gemini 4 Hydrogen · Google Maps"
                                    else -> "${activeChatModel.displayName} · ${activeMode.label}"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    Box {
                        TextButton(
                            onClick = { showModelMenu = true },
                            modifier = Modifier.testTag("vetny_model_button")
                        ) {
                            Icon(Icons.Rounded.Psychology, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = when (activeMode) {
                                    VetnyAgentMode.IMAGE -> "Nano"
                                    VetnyAgentMode.VOICE_LIVE -> "Live"
                                    VetnyAgentMode.SEARCH, VetnyAgentMode.MAPS -> "G4H"
                                    else -> when (activeChatModel) {
                                        VetnyChatModel.GEMINI_4_ARGON -> "G4A"
                                        VetnyChatModel.GEMINI_4_HYDROGEN -> "G4H"
                                        VetnyChatModel.GEMINI_3_8_FLASH -> "3.8"
                                    }
                                },
                                maxLines = 1
                            )
                        }
                        DropdownMenu(
                            expanded = showModelMenu,
                            onDismissRequest = { showModelMenu = false }
                        ) {
                            Text(
                                text = "Chat & Booking Engine",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                            VetnyChatModel.entries.forEach { m ->
                                DropdownMenuItem(
                                    text = {
                                        Text("${m.displayName} (${m.badge})" + if (activeChatModel == m) " ✓" else "")
                                    },
                                    onClick = {
                                        onSelectChatModel(m)
                                        showModelMenu = false
                                    }
                                )
                            }
                            Text(
                                text = "Photo Studio Engine",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                            VetnyImageModel.entries.forEach { im ->
                                DropdownMenuItem(
                                    text = {
                                        Text(im.displayName + if (activeImageModel == im) " ✓" else "")
                                    },
                                    onClick = {
                                        onSelectImageModel(im)
                                        showModelMenu = false
                                    }
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = {
                            mapInitialQuery = null
                            showGoogleMap = true
                        },
                        modifier = Modifier.testTag("vetny_open_map_button")
                    ) {
                        Icon(Icons.Rounded.Map, contentDescription = "Open Google Maps Explorer")
                    }
                    IconButton(
                        onClick = { showLanguagePicker = true },
                        modifier = Modifier.testTag("vetny_language_button")
                    ) {
                        Icon(Icons.Rounded.Language, contentDescription = "Target language: $targetLanguage")
                    }
                    IconButton(
                        onClick = onClearConversation,
                        modifier = Modifier.testTag("vetny_clear_chat_button")
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Start new conversation")
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
            // Mode Switcher Bar
            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(VetnyAgentMode.entries) { mode ->
                    val icon = when (mode) {
                        VetnyAgentMode.SUPPORT -> Icons.Rounded.SupportAgent
                        VetnyAgentMode.VOICE_LIVE -> Icons.Rounded.GraphicEq
                        VetnyAgentMode.SEARCH -> Icons.Rounded.Public
                        VetnyAgentMode.MAPS -> Icons.Rounded.Place
                        VetnyAgentMode.BOOKING -> Icons.Rounded.CalendarMonth
                        VetnyAgentMode.TRANSLATE -> Icons.Rounded.Language
                        VetnyAgentMode.IMAGE -> Icons.Rounded.PhotoCamera
                    }
                    FilterChip(
                        selected = activeMode == mode,
                        onClick = { onSelectMode(mode) },
                        leadingIcon = {
                            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text(mode.label) },
                        modifier = Modifier.testTag("vetny_mode_${mode.wireValue.lowercase()}")
                    )
                }
            }

            // Aspect Ratio Selector when in Photo Studio mode
            if (activeMode == VetnyAgentMode.IMAGE) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(VetnyImageAspect.entries) { ratio ->
                        FilterChip(
                            selected = activeImageAspect == ratio,
                            onClick = { onSelectImageAspect(ratio) },
                            label = { Text("Aspect ${ratio.label}") }
                        )
                    }
                }
            }

            // Live Voice Panel (Gemini 3.8 Live)
            if (activeMode == VetnyAgentMode.VOICE_LIVE ||
                liveVoiceState.isActive ||
                liveVoiceState.isConnecting
            ) {
                VetnyLiveVoicePanel(
                    liveState = liveVoiceState,
                    targetLanguage = targetLanguage,
                    onStartLiveVoice = { interpreterMode, voiceName ->
                        onStartLiveVoice(targetLanguage, interpreterMode, voiceName)
                    },
                    onStopLiveVoice = { onStopLiveVoice(targetLanguage) },
                    onShowBanner = onShowBanner
                )
            }

            // Quick Prompt Starters
            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(quickPrompts) { starter ->
                    AssistChip(
                        onClick = {
                            onSendPrompt(starter, targetLanguage, attachedBitmap)
                            attachedBitmap = null
                        },
                        label = { Text(starter, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (messagesState) {
                    is UiState.Loading -> {
                        item(key = "loading") {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                    is UiState.Error -> {
                        item(key = "err") {
                            Text(messagesState.message, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    is UiState.Success -> {
                        val msgs = messagesState.data
                        items(msgs.indices.toList(), key = { idx -> msgs[idx].id }) { idx ->
                            val msg = msgs[idx]
                            val isUser = msg.senderRole == "USER"
                            val hasSubsequentReply = idx < msgs.lastIndex &&
                                msgs.subList(idx + 1, msgs.size).any { it.senderRole != "USER" }
                            val vetnyBusy = streamState is AiStreamState.Loading ||
                                streamState is AiStreamState.GeneratingImage ||
                                streamState is AiStreamState.Streaming
                            val vetnyReceiptStatus = when {
                                hasSubsequentReply -> "READ"
                                vetnyBusy && idx == msgs.lastIndex -> "SENT"
                                else -> "DELIVERED"
                            }
                            val decodedBitmap = remember(msg.mediaBase64) {
                                MediaEncodeHelper.decodeBase64Bitmap(msg.mediaBase64)
                            }
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
                            ) {
                                Surface(
                                    color = if (isUser) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surface,
                                    tonalElevation = 2.dp,
                                    shape = RoundedCornerShape(18.dp),
                                    modifier = Modifier.widthIn(max = 328.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val headerBadge = if (isUser) {
                                                "You · ${msg.agentMode ?: "CHAT"}"
                                            } else {
                                                val engineTag = when (msg.modelUsed) {
                                                    VetnyChatModel.GEMINI_4_ARGON.modelId -> "Gemini 4 Argon"
                                                    VetnyChatModel.GEMINI_4_HYDROGEN.modelId -> "Gemini 4 Hydrogen"
                                                    VetnyAiService.LIVE_VOICE_MODEL_ID -> "Gemini 3.8 Live"
                                                    VetnyImageModel.NANO_BANANA_2_1.modelId -> "Nano Banana 2.1"
                                                    VetnyImageModel.GEMINI_3_PRO_IMAGE.modelId -> "Nano Banana Pro"
                                                    else -> "Gemini 3.8 Flash"
                                                }
                                                "Vetny ($engineTag)"
                                            }
                                            Text(
                                                text = headerBadge,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.tertiary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            if (!isUser && !msg.agentMode.isNullOrBlank()) {
                                                Text(
                                                    text = msg.agentMode,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        if (decodedBitmap != null) {
                                            Image(
                                                bitmap = decodedBitmap.asImageBitmap(),
                                                contentDescription = msg.originalText,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(min = 160.dp, max = 280.dp)
                                                    .clip(RoundedCornerShape(14.dp))
                                            )
                                        }

                                        val bodyText = if (isUser) {
                                            msg.originalText
                                        } else {
                                            msg.translatedText ?: msg.originalText
                                        }
                                        Text(
                                            text = bodyText,
                                            style = MaterialTheme.typography.bodyMedium
                                        )

                                        val timeLabel = remember(msg.createdAt) {
                                            msg.createdAt?.toDate()?.let { d ->
                                                SimpleDateFormat("HH:mm", Locale.getDefault()).format(d)
                                            }.orEmpty()
                                        }
                                        if (isUser) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (timeLabel.isNotBlank()) {
                                                    Text(
                                                        text = timeLabel,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                }
                                                ReadReceiptTicks(
                                                    status = vetnyReceiptStatus,
                                                    iconSize = 14.dp,
                                                    modifier = Modifier.testTag("vetny_receipt_tick_${msg.id}")
                                                )
                                            }
                                        }

                                        val parsedGrounding = remember(msg.groundingJson) {
                                            VetnyGroundingPayload.fromJsonString(msg.groundingJson)
                                        }
                                        if (!isUser && parsedGrounding != null) {
                                            VetnyGroundingSection(
                                                grounding = parsedGrounding,
                                                onOpenMapExplorer = { q ->
                                                    mapInitialQuery = q
                                                    showGoogleMap = true
                                                },
                                                onShowBanner = onShowBanner
                                            )
                                        }

                                        // Action bar on Vetny-generated AI photos
                                        if (!isUser && msg.messageType == "IMAGE" && decodedBitmap != null) {
                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                OutlinedButton(
                                                    onClick = {
                                                        scope.launch {
                                                            ScreenshotHelper.saveBitmapToGallery(context, decodedBitmap)
                                                                .onSuccess { res ->
                                                                    onShowBanner(
                                                                        if (res.savedToGallery) "Saved to Pictures/KelMi"
                                                                        else "Saved ${res.fileName}"
                                                                    )
                                                                }
                                                                .onFailure { err ->
                                                                    onShowBanner(err.localizedMessage ?: "Save failed")
                                                                }
                                                        }
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                                ) {
                                                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Save", style = MaterialTheme.typography.labelSmall)
                                                }
                                                OutlinedButton(
                                                    onClick = {
                                                        scope.launch {
                                                            ScreenshotHelper.shareBitmap(context, decodedBitmap, msg.originalText)
                                                                .onFailure { err ->
                                                                    onShowBanner(err.localizedMessage ?: "Share failed")
                                                                }
                                                        }
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                                ) {
                                                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Share", style = MaterialTheme.typography.labelSmall)
                                                }
                                                OutlinedButton(
                                                    onClick = {
                                                        attachedBitmap = decodedBitmap
                                                        onSelectMode(VetnyAgentMode.IMAGE)
                                                        onShowBanner("Loaded photo for AI editing/remixing")
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                                ) {
                                                    Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, modifier = Modifier.size(15.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Remix", style = MaterialTheme.typography.labelSmall)
                                                }
                                                OutlinedButton(
                                                    onClick = {
                                                        onPostPhotoToReels(decodedBitmap, msg.originalText)
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                                ) {
                                                    Icon(Icons.Rounded.MovieCreation, contentDescription = null, modifier = Modifier.size(15.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Post Reel", style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                when (streamState) {
                    is AiStreamState.Loading -> {
                        item(key = "streaming_loading") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Vetny is thinking...", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    is AiStreamState.GeneratingImage -> {
                        item(key = "generating_image") {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.widthIn(max = 310.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Creating photo with ${streamState.modelLabel}...",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Typically takes 15–60s: \"${streamState.prompt.take(56)}\"",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                    is AiStreamState.Streaming -> {
                        item(key = "streaming_live") {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.widthIn(max = 328.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = streamState.text,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    streamState.grounding?.let { gm ->
                                        VetnyGroundingSection(
                                            grounding = gm,
                                            onOpenMapExplorer = { q ->
                                                mapInitialQuery = q
                                                showGoogleMap = true
                                            },
                                            onShowBanner = onShowBanner
                                        )
                                    }
                                }
                            }
                        }
                    }
                    is AiStreamState.Error -> {
                        item(key = "stream_error") {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = streamState.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                    else -> Unit
                }
            }

            // Composer & Photo Attach Bar
            Surface(
                tonalElevation = 3.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    attachedBitmap?.let { previewBmp ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                bitmap = previewBmp.asImageBitmap(),
                                contentDescription = "Attached reference image",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (activeMode == VetnyAgentMode.IMAGE) {
                                    "Reference image attached for AI editing"
                                } else {
                                    "Photo attached for Vetny vision analysis"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { attachedBitmap = null }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Remove attached photo")
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.testTag("vetny_attach_photo_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AddPhotoAlternate,
                                contentDescription = "Attach photo for analysis or AI editing",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        OutlinedTextField(
                            value = promptText,
                            onValueChange = { promptText = it },
                            placeholder = {
                                val hint = when (activeMode) {
                                    VetnyAgentMode.SUPPORT -> "Ask Vetny anything (remembers chat)..."
                                    VetnyAgentMode.VOICE_LIVE -> "Tap mic above or type to Live Voice..."
                                    VetnyAgentMode.SEARCH -> "Search the web with Google Search + Gemini 4..."
                                    VetnyAgentMode.MAPS -> "Ask about places, hotels, food on Google Maps..."
                                    VetnyAgentMode.BOOKING -> "Book a trip, hotel, dinner, or appointment..."
                                    VetnyAgentMode.TRANSLATE -> "Translate to $targetLanguage..."
                                    VetnyAgentMode.IMAGE -> if (attachedBitmap != null) {
                                        "Describe how to edit/remix this photo..."
                                    } else {
                                        "Describe the photo or art to generate..."
                                    }
                                }
                                Text(hint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            maxLines = 4,
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("vetny_prompt_input")
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        val isBusy = streamState is AiStreamState.Loading ||
                            streamState is AiStreamState.GeneratingImage ||
                            streamState is AiStreamState.Streaming
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    if (promptText.isNotBlank() && !isBusy) MaterialTheme.colorScheme.tertiary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable(enabled = promptText.isNotBlank() && !isBusy) {
                                    val text = promptText.trim()
                                    if (text.isNotEmpty()) {
                                        onSendPrompt(text, targetLanguage, attachedBitmap)
                                        promptText = ""
                                        attachedBitmap = null
                                    }
                                }
                                .testTag("vetny_send_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (activeMode == VetnyAgentMode.IMAGE) Icons.Rounded.AutoAwesome else Icons.AutoMirrored.Rounded.Send,
                                contentDescription = "Send to Vetny",
                                tint = if (promptText.isNotBlank() && !isBusy) MaterialTheme.colorScheme.onTertiary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (showLanguagePicker) {
        LanguagePickerDialog(
            title = "Vetny Target Language",
            currentLanguage = targetLanguage,
            onSelectLanguage = { targetLanguage = it },
            onDismiss = { showLanguagePicker = false }
        )
    }

    if (showGoogleMap) {
        val recentMapPlaces = remember(messageList) {
            messageList
                .mapNotNull { VetnyGroundingPayload.fromJsonString(it.groundingJson) }
                .flatMap { it.mapsSources }
                .distinctBy { it.title }
                .take(10)
        }
        VetnyGoogleMapDialog(
            initialQuery = mapInitialQuery,
            currentCoordinates = currentCoordinates,
            currentLocationLabel = userLocationLabel,
            recentMapPlaces = recentMapPlaces,
            onCoordinatesUpdated = onCoordinatesUpdated,
            onAskVetnyMaps = { prompt ->
                onSelectMode(VetnyAgentMode.MAPS)
                onSendPrompt(prompt, targetLanguage, null)
            },
            onShowBanner = onShowBanner,
            onDismiss = { showGoogleMap = false }
        )
    }
}
