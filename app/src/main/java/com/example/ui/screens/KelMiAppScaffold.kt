package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.KelMiTab
import com.example.data.model.UiState
import com.example.ui.components.MyQrCardDialog
import com.example.ui.components.NoxTunnelDialog
import com.example.ui.viewmodel.KelMiViewModel

@Composable
fun KelMiAppScaffold(
    viewModel: KelMiViewModel,
    currentUserId: String
) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val userProfileState by viewModel.userProfile.collectAsStateWithLifecycle()
    val contactsState by viewModel.contacts.collectAsStateWithLifecycle()
    val onlineUsersState by viewModel.onlineUsers.collectAsStateWithLifecycle()
    val chatListState by viewModel.chatListItems.collectAsStateWithLifecycle()
    val activeChat by viewModel.activeChat.collectAsStateWithLifecycle()
    val activeMessages by viewModel.activeChatMessages.collectAsStateWithLifecycle()
    val activeCall by viewModel.activeCall.collectAsStateWithLifecycle()
    val incomingCall by viewModel.incomingCall.collectAsStateWithLifecycle()
    val callTranslation by viewModel.callTranslation.collectAsStateWithLifecycle()
    val isVetnyOpen by viewModel.isVetnyOpen.collectAsStateWithLifecycle()
    val isQrScannerOpen by viewModel.isQrScannerOpen.collectAsStateWithLifecycle()
    val isProfileEditOpen by viewModel.isProfileSetupEditOpen.collectAsStateWithLifecycle()
    val vetnyMessagesState by viewModel.vetnyMessages.collectAsStateWithLifecycle()
    val vetnyStreamState by viewModel.vetnyStreamState.collectAsStateWithLifecycle()
    val vetnyAgentMode by viewModel.vetnyAgentMode.collectAsStateWithLifecycle()
    val vetnyChatModel by viewModel.vetnyChatModel.collectAsStateWithLifecycle()
    val vetnyImageModel by viewModel.vetnyImageModel.collectAsStateWithLifecycle()
    val vetnyImageAspect by viewModel.vetnyImageAspect.collectAsStateWithLifecycle()
    val vetnyLiveState by viewModel.vetnyLiveState.collectAsStateWithLifecycle()
    val draftTranslation by viewModel.draftTranslationState.collectAsStateWithLifecycle()
    val isDraftTranslating by viewModel.isDraftTranslating.collectAsStateWithLifecycle()
    val momentsState by viewModel.moments.collectAsStateWithLifecycle()
    val momentTranslations by viewModel.momentTranslations.collectAsStateWithLifecycle()
    val reelsState by viewModel.reels.collectAsStateWithLifecycle()
    val reelTranslations by viewModel.reelTranslations.collectAsStateWithLifecycle()
    val storiesState by viewModel.stories.collectAsStateWithLifecycle()
    val activeReelComments by viewModel.activeReelComments.collectAsStateWithLifecycle()
    val activeCommentReelId by viewModel.activeCommentReelId.collectAsStateWithLifecycle()
    val liveLocation by viewModel.liveLocationLabel.collectAsStateWithLifecycle()
    val liveCoordinates by viewModel.liveCoordinates.collectAsStateWithLifecycle()
    val keyDiagnostics by viewModel.keyDiagnostics.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

    var showMyQrDialog by remember { mutableStateOf(false) }
    var showNoxDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusBanner) {
        statusBanner?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusBanner()
        }
    }

    val userProfile = (userProfileState as? UiState.Success)?.data
    val contactsList = (contactsState as? UiState.Success)?.data.orEmpty()
    val reelsList = (reelsState as? UiState.Success)?.data.orEmpty()

    when {
        activeCall != null -> {
            ActiveCallScreen(
                callState = activeCall!!,
                myPreferredLanguage = userProfile?.preferredLanguage ?: "English",
                callTranslation = callTranslation,
                onEndCall = { durationSec -> viewModel.endActiveCall(durationSec) },
                onTranslatePhrase = { phrase, lang -> viewModel.translateCallPhrase(phrase, lang) },
                onClearTranslation = { viewModel.clearCallTranslation() },
                onShowBanner = { viewModel.showBanner(it) }
            )
        }
        isQrScannerOpen -> {
            QrScannerScreen(
                onBack = { viewModel.closeQrScanner() },
                onShowMyQr = { showMyQrDialog = true },
                onQrContactResolved = { payload ->
                    viewModel.addContactByHandle(payload, openChatImmediately = true)
                }
            )
        }
        isVetnyOpen -> {
            VetnyChatScreen(
                messagesState = vetnyMessagesState,
                streamState = vetnyStreamState,
                liveVoiceState = vetnyLiveState,
                activeMode = vetnyAgentMode,
                activeChatModel = vetnyChatModel,
                activeImageModel = vetnyImageModel,
                activeImageAspect = vetnyImageAspect,
                defaultTargetLanguage = userProfile?.preferredLanguage ?: "Spanish",
                userLocationLabel = liveLocation ?: userProfile?.locationLabel,
                currentCoordinates = liveCoordinates,
                onBack = { viewModel.closeVetnyAssistant() },
                onSelectMode = { viewModel.setVetnyAgentMode(it) },
                onSelectChatModel = { viewModel.setVetnyChatModel(it) },
                onSelectImageModel = { viewModel.setVetnyImageModel(it) },
                onSelectImageAspect = { viewModel.setVetnyImageAspect(it) },
                onSendPrompt = { prompt, lang, bmp ->
                    viewModel.sendVetnyAssistantPrompt(prompt, lang, bmp)
                },
                onStartLiveVoice = { targetLang, interpreterMode, voiceName ->
                    viewModel.startVetnyLiveVoice(targetLang, interpreterMode, voiceName)
                },
                onStopLiveVoice = { targetLang ->
                    viewModel.stopVetnyLiveVoice(saveTranscriptToHistory = true, targetLanguage = targetLang)
                },
                onCoordinatesUpdated = { lat, lng ->
                    viewModel.updateLiveCoordinates(lat, lng)
                },
                onClearConversation = { viewModel.clearVetnyConversation() },
                onPostPhotoToReels = { bmp, caption ->
                    viewModel.postGeneratedPhotoToReels(bmp, caption)
                },
                onShowBanner = { viewModel.showBanner(it) }
            )
        }
        activeChat != null -> {
            val session = activeChat!!
            EncryptedChatScreen(
                session = session,
                messages = activeMessages,
                userProfile = userProfile,
                draftTranslation = draftTranslation,
                isDraftTranslating = isDraftTranslating,
                onBack = { viewModel.closeActiveChat() },
                onSendMessage = { text, type -> viewModel.sendEncryptedMessage(text, type) },
                onTranslateMessage = { msg, lang -> viewModel.translateSingleMessage(msg, lang) },
                onTranslateDraft = { draft, lang -> viewModel.translateDraftBeforeSend(draft, lang) },
                onClearDraftTranslation = { viewModel.clearDraftTranslation() },
                onToggleAutoTranslate = { lang, enabled -> viewModel.updateTranslationPreferences(lang, enabled) },
                onToggleVerified = { viewModel.toggleSafetyNumberVerified() },
                onLocationFetched = { viewModel.updateLiveLocation(it) },
                onStartVoiceCall = {
                    viewModel.startVoiceOrVideoCall(
                        peerUid = session.peerUid,
                        peerName = session.peerName,
                        peerAvatarColorHex = session.peerAvatarColorHex,
                        peerPreferredLanguage = session.peerPreferredLanguage,
                        callType = "VOICE"
                    )
                },
                onStartVideoCall = {
                    viewModel.startVoiceOrVideoCall(
                        peerUid = session.peerUid,
                        peerName = session.peerName,
                        peerAvatarColorHex = session.peerAvatarColorHex,
                        peerPreferredLanguage = session.peerPreferredLanguage,
                        callType = "VIDEO"
                    )
                },
                onShowBanner = { viewModel.showBanner(it) }
            )
        }
        userProfile != null && (!userProfile.profileCompleted || isProfileEditOpen) -> {
            ProfileSetupScreen(
                profile = userProfile,
                liveLocationLabel = liveLocation,
                isEditMode = userProfile.profileCompleted && isProfileEditOpen,
                onBack = if (userProfile.profileCompleted) { { viewModel.closeProfileEditor() } } else null,
                onLocationFetched = { viewModel.updateLiveLocation(it) },
                onSave = { name, email, phone, handle, gender, dob, interests, loc, tz, lang, autoTrans ->
                    viewModel.saveProfileDetails(
                        displayName = name,
                        email = email,
                        phoneNumber = phone,
                        kelMiHandle = handle,
                        gender = gender,
                        dateOfBirth = dob,
                        interests = interests,
                        locationLabel = loc,
                        timeZoneId = tz,
                        preferredLanguage = lang,
                        autoTranslateEnabled = autoTrans
                    )
                }
            )
        }
        userProfileState is UiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        else -> {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useNavRail = maxWidth >= 840.dp
                Scaffold(
                    contentWindowInsets = WindowInsets.safeDrawing,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                        if (!useNavRail) {
                            NavigationBar(windowInsets = WindowInsets.navigationBars) {
                                KelMiTab.entries.forEach { tab ->
                                    NavigationBarItem(
                                        selected = selectedTab == tab,
                                        onClick = { viewModel.selectTab(tab) },
                                        icon = { TabIcon(tab) },
                                        label = { Text(tab.label, maxLines = 1) }
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (useNavRail) {
                            NavigationRail(modifier = Modifier.fillMaxHeight()) {
                                KelMiTab.entries.forEach { tab ->
                                    NavigationRailItem(
                                        selected = selectedTab == tab,
                                        onClick = { viewModel.selectTab(tab) },
                                        icon = { TabIcon(tab) },
                                        label = { Text(tab.label) }
                                    )
                                }
                            }
                        }

                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            when (selectedTab) {
                                KelMiTab.CHATS -> ChatsTab(
                                    chatsState = chatListState,
                                    userProfile = userProfile,
                                    liveLocationLabel = liveLocation,
                                    onOpenChat = { peerUid -> viewModel.openChatWithPeer(peerUid) },
                                    onOpenSelfVault = { viewModel.openChatWithPeer(currentUserId) },
                                    onOpenVetny = { viewModel.openVetnyAssistant() },
                                    onShowMyQr = { showMyQrDialog = true },
                                    onOpenQrScanner = { viewModel.openQrScanner() }
                                )
                                KelMiTab.CONTACTS -> ContactsTab(
                                    contactsState = contactsState,
                                    onlineUsersState = onlineUsersState,
                                    onAddByHandle = { handle -> viewModel.addContactByHandle(handle, openChatImmediately = false) },
                                    onConnectOnlineUser = { user, openChat -> viewModel.connectWithOnlineUser(user, openChat) },
                                    onOpenChat = { peerUid -> viewModel.openChatWithPeer(peerUid) },
                                    onStartVoiceCall = { uid, name, color, lang ->
                                        viewModel.startVoiceOrVideoCall(uid, name, color, lang, "VOICE")
                                    },
                                    onStartVideoCall = { uid, name, color, lang ->
                                        viewModel.startVoiceOrVideoCall(uid, name, color, lang, "VIDEO")
                                    },
                                    onShowMyQr = { showMyQrDialog = true },
                                    onOpenQrScanner = { viewModel.openQrScanner() },
                                    onOpenNoxDialog = { showNoxDialog = true }
                                )
                                KelMiTab.REELS -> ReelsTab(
                                    reelsState = reelsState,
                                    storiesState = storiesState,
                                    userProfile = userProfile,
                                    currentUserId = currentUserId,
                                    reelTranslations = reelTranslations,
                                    activeReelComments = activeReelComments,
                                    activeCommentReelId = activeCommentReelId,
                                    onPostReel = { caption, mType, mBase64, lUri, dur, sId, sTitle, sArtist, loc, filter, isPub ->
                                        viewModel.postReel(caption, mType, mBase64, lUri, dur, sId, sTitle, sArtist, loc, filter, isPub)
                                    },
                                    onToggleLike = { viewModel.toggleReelLike(it) },
                                    onToggleBookmark = { viewModel.toggleReelBookmark(it) },
                                    onTranslateReel = { reel, lang -> viewModel.translateReel(reel, lang) },
                                    onOpenComments = { viewModel.openReelComments(it) },
                                    onCloseComments = { viewModel.closeReelComments() },
                                    onAddComment = { rId, txt -> viewModel.addReelComment(rId, txt) },
                                    onPostStory = { cap, mBase64, sTitle, loc -> viewModel.postStory(cap, mBase64, sTitle, loc) },
                                    onViewStory = { viewModel.markStoryViewed(it) },
                                    onShowBanner = { viewModel.showBanner(it) }
                                )
                                KelMiTab.MOMENTS -> MomentsTab(
                                    momentsState = momentsState,
                                    userProfile = userProfile,
                                    currentUserId = currentUserId,
                                    momentTranslations = momentTranslations,
                                    onPostMoment = { c, l, loc -> viewModel.postMoment(c, l, loc) },
                                    onToggleLike = { viewModel.toggleMomentLike(it) },
                                    onTranslateMoment = { m, lang -> viewModel.translateMoment(m, lang) },
                                    onShowBanner = { viewModel.showBanner(it) }
                                )
                                KelMiTab.SECURITY -> SecurityProfileTab(
                                    userProfile = userProfile,
                                    keyDiagnostics = keyDiagnostics,
                                    contactCount = contactsList.size,
                                    myReels = reelsList.filter { it.authorId == currentUserId },
                                    savedReelsCount = reelsList.count { currentUserId in it.bookmarkedByUids },
                                    onEditProfile = { viewModel.openProfileEditor() },
                                    onShowMyQr = { showMyQrDialog = true },
                                    onOpenNoxDialog = { showNoxDialog = true },
                                    onUpdateTranslationPrefs = { lang, enabled -> viewModel.updateTranslationPreferences(lang, enabled) },
                                    onSetDiscoverableOnline = { viewModel.setDiscoverableOnline(it) },
                                    onRotateSignedPreKey = { viewModel.rotateSignedPreKey() },
                                    onReplenishOpks = { viewModel.replenishOneTimePreKeys() },
                                    onShowBanner = { viewModel.showBanner(it) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showMyQrDialog && userProfile != null) {
        MyQrCardDialog(
            profile = userProfile,
            safetyFingerprint = keyDiagnostics.safetyFingerprint,
            onOpenScanner = { viewModel.openQrScanner() },
            onShowBanner = { viewModel.showBanner(it) },
            onDismiss = { showMyQrDialog = false }
        )
    }

    if (showNoxDialog) {
        NoxTunnelDialog(onDismiss = { showNoxDialog = false })
    }

    incomingCall?.let { call ->
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Incoming Encrypted ${if (call.callType == "VIDEO") "Video" else "Voice"} Call") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${call.peerName} is calling you on Kel mi")
                    Text("Signal E2EE Fingerprint: ${call.e2eeFingerprint.take(8)}", style = MaterialTheme.typography.labelSmall)
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.acceptIncomingCall() }) {
                    Icon(Icons.Rounded.Call, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Accept")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { viewModel.declineIncomingCall() }) {
                    Icon(Icons.Rounded.CallEnd, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Decline")
                }
            }
        )
    }
}

@Composable
private fun TabIcon(tab: KelMiTab) {
    val vec = when (tab) {
        KelMiTab.CHATS -> Icons.AutoMirrored.Rounded.Chat
        KelMiTab.CONTACTS -> Icons.Rounded.Group
        KelMiTab.REELS -> Icons.Rounded.VideoLibrary
        KelMiTab.MOMENTS -> Icons.Rounded.Explore
        KelMiTab.SECURITY -> Icons.Rounded.Person
    }
    Icon(imageVector = vec, contentDescription = tab.label)
}
