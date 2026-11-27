package com.example.ui.viewmodel

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.DecryptedMessageEntity
import com.example.data.model.ActiveCallUiState
import com.example.data.model.ActiveChatSession
import com.example.data.model.AiStreamState
import com.example.data.model.ChatListItem
import com.example.data.model.ChatThread
import com.example.data.model.KelMiTab
import com.example.data.model.MomentPost
import com.example.data.model.MomentTranslationState
import com.example.data.model.OnlineUser
import com.example.data.model.PublicPreKeyBundle
import com.example.data.model.ReelComment
import com.example.data.model.ReelPost
import com.example.data.model.StoryPost
import com.example.data.model.UiState
import com.example.data.model.UserContact
import com.example.data.model.UserProfile
import com.example.data.model.VetnyAgentMode
import com.example.data.model.VetnyChatModel
import com.example.data.model.VetnyGroundingPayload
import com.example.data.model.VetnyImageAspect
import com.example.data.model.VetnyImageModel
import com.example.data.model.VetnyLiveSessionState
import com.example.data.model.VetnyMessage
import com.example.data.model.VetnyTranslationResult
import com.example.data.remote.KelMiRepository
import com.example.data.remote.KelMiSocialRepository
import com.example.data.remote.VetnyAiService
import com.example.security.SignalProtocolManager
import com.example.util.KelMiMusicPlayer
import com.example.util.LocationTimeHelper
import com.example.util.MediaEncodeHelper
import com.google.firebase.Firebase
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.liveAudioConversationConfig
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

@OptIn(ExperimentalCoroutinesApi::class, PublicPreviewAPI::class)
class KelMiViewModel(
    private val repository: KelMiRepository,
    private val socialRepository: KelMiSocialRepository,
    private val vetnyAiService: VetnyAiService,
    private val signalManager: SignalProtocolManager,
    private val currentUserId: String
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(KelMiTab.CHATS)
    val selectedTab: StateFlow<KelMiTab> = _selectedTab.asStateFlow()

    private val _activeChat = MutableStateFlow<ActiveChatSession?>(null)
    val activeChat: StateFlow<ActiveChatSession?> = _activeChat.asStateFlow()

    private val _isVetnyOpen = MutableStateFlow(false)
    val isVetnyOpen: StateFlow<Boolean> = _isVetnyOpen.asStateFlow()

    private val _isQrScannerOpen = MutableStateFlow(false)
    val isQrScannerOpen: StateFlow<Boolean> = _isQrScannerOpen.asStateFlow()

    private val _isProfileSetupEditOpen = MutableStateFlow(false)
    val isProfileSetupEditOpen: StateFlow<Boolean> = _isProfileSetupEditOpen.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _liveLocationLabel = MutableStateFlow<String?>(null)
    val liveLocationLabel: StateFlow<String?> = _liveLocationLabel.asStateFlow()

    private val _liveCoordinates = MutableStateFlow<Pair<Double, Double>?>(null)
    val liveCoordinates: StateFlow<Pair<Double, Double>?> = _liveCoordinates.asStateFlow()

    private val _keyDiagnostics = MutableStateFlow(signalManager.getDiagnostics())
    val keyDiagnostics: StateFlow<SignalProtocolManager.LocalKeyDiagnostics> = _keyDiagnostics.asStateFlow()

    private val _vetnyStreamState = MutableStateFlow<AiStreamState>(AiStreamState.Idle)
    val vetnyStreamState: StateFlow<AiStreamState> = _vetnyStreamState.asStateFlow()

    private val _vetnyAgentMode = MutableStateFlow(VetnyAgentMode.SUPPORT)
    val vetnyAgentMode: StateFlow<VetnyAgentMode> = _vetnyAgentMode.asStateFlow()

    private val _vetnyChatModel = MutableStateFlow(VetnyChatModel.GEMINI_3_8_FLASH)
    val vetnyChatModel: StateFlow<VetnyChatModel> = _vetnyChatModel.asStateFlow()

    private val _vetnyImageModel = MutableStateFlow(VetnyImageModel.NANO_BANANA_2_1)
    val vetnyImageModel: StateFlow<VetnyImageModel> = _vetnyImageModel.asStateFlow()

    private val _vetnyImageAspect = MutableStateFlow(VetnyImageAspect.SQUARE)
    val vetnyImageAspect: StateFlow<VetnyImageAspect> = _vetnyImageAspect.asStateFlow()

    private val _vetnyLiveState = MutableStateFlow(VetnyLiveSessionState())
    val vetnyLiveState: StateFlow<VetnyLiveSessionState> = _vetnyLiveState.asStateFlow()

    private var activeLiveSession: LiveSession? = null
    private var liveVoiceJob: Job? = null

    private val _draftTranslationState = MutableStateFlow<VetnyTranslationResult?>(null)
    val draftTranslationState: StateFlow<VetnyTranslationResult?> = _draftTranslationState.asStateFlow()

    private val _isDraftTranslating = MutableStateFlow(false)
    val isDraftTranslating: StateFlow<Boolean> = _isDraftTranslating.asStateFlow()

    private val _momentTranslations = MutableStateFlow<Map<String, MomentTranslationState>>(emptyMap())
    val momentTranslations: StateFlow<Map<String, MomentTranslationState>> = _momentTranslations.asStateFlow()

    private val _reelTranslations = MutableStateFlow<Map<String, MomentTranslationState>>(emptyMap())
    val reelTranslations: StateFlow<Map<String, MomentTranslationState>> = _reelTranslations.asStateFlow()

    private val _activeCall = MutableStateFlow<ActiveCallUiState?>(null)
    val activeCall: StateFlow<ActiveCallUiState?> = _activeCall.asStateFlow()

    private val _incomingCall = MutableStateFlow<ActiveCallUiState?>(null)
    val incomingCall: StateFlow<ActiveCallUiState?> = _incomingCall.asStateFlow()

    private val _callTranslation = MutableStateFlow<VetnyTranslationResult?>(null)
    val callTranslation: StateFlow<VetnyTranslationResult?> = _callTranslation.asStateFlow()

    private val _activeCommentReelId = MutableStateFlow<String?>(null)
    val activeCommentReelId: StateFlow<String?> = _activeCommentReelId.asStateFlow()

    private val peerBundleCache = ConcurrentHashMap<String, PublicPreKeyBundle>()
    private val autoTranslatedMessageIds = ConcurrentHashMap.newKeySet<String>()
    private var chatDecryptJob: Job? = null
    private var callListenerJob: Job? = null

    val userProfile: StateFlow<UiState<UserProfile?>> = repository.observeUserProfile(currentUserId)
        .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
        .catch { error -> emit(handleStreamError("userProfile", error)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    val contacts: StateFlow<UiState<List<UserContact>>> = repository.observeContacts(currentUserId)
        .map<List<UserContact>, UiState<List<UserContact>>> { UiState.Success(it) }
        .catch { error -> emit(handleStreamError("contacts", error)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    private val chatThreads: StateFlow<UiState<List<ChatThread>>> = repository.observeChats(currentUserId)
        .map<List<ChatThread>, UiState<List<ChatThread>>> { UiState.Success(it) }
        .catch { error -> emit(handleStreamError("chats", error)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    private val allDecryptedMessages: StateFlow<List<DecryptedMessageEntity>> =
        repository.observeAllDecryptedMessages(currentUserId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val chatListItems: StateFlow<UiState<List<ChatListItem>>> = combine(
        chatThreads,
        contacts,
        allDecryptedMessages,
        userProfile
    ) { chatsState, contactsState, decryptedList, profileState ->
        when (chatsState) {
            is UiState.Loading -> UiState.Loading
            is UiState.Error -> UiState.Error(chatsState.message)
            is UiState.Success -> {
                val contactMap = (contactsState as? UiState.Success)?.data?.associateBy { it.contactUid }.orEmpty()
                val myProfile = (profileState as? UiState.Success)?.data
                val latestByChat = decryptedList.groupBy { it.chatId }
                    .mapValues { (_, msgs) -> msgs.maxByOrNull { it.timestampMillis } }

                val items = chatsState.data.map { thread ->
                    val otherUid = thread.participantUids.firstOrNull { it != currentUserId } ?: currentUserId
                    val isSelf = otherUid == currentUserId
                    val contact = contactMap[otherUid]
                    val cachedBundle = peerBundleCache[otherUid]
                    val latestMsg = latestByChat[thread.id]
                    val snippet = latestMsg?.plaintext
                        ?: thread.lastMessagePreviewTag
                        ?: "End-to-end encrypted"
                    val title = when {
                        isSelf -> "Saved Encrypted Vault"
                        contact != null -> contact.displayName
                        cachedBundle != null -> cachedBundle.displayName
                        else -> "Peer (${otherUid.take(6)})"
                    }
                    val hasRealMessage = latestMsg != null ||
                        (thread.lastMessagePreviewTag != null && thread.lastMessagePreviewTag != "Signal session ready")
                    val latestIsMe = hasRealMessage && when {
                        latestMsg != null -> latestMsg.senderId == currentUserId
                        else -> thread.lastSenderId == currentUserId
                    }
                    val latestReceipt = if (!latestIsMe) {
                        null
                    } else {
                        val localStatus = latestMsg?.receiptStatus
                        val threadStatus = thread.lastMessageReceiptStatus
                        when {
                            localStatus == "READ" || threadStatus == "READ" -> "READ"
                            localStatus == "DELIVERED" || threadStatus == "DELIVERED" -> "DELIVERED"
                            localStatus == "SENDING" -> "SENDING"
                            localStatus == "FAILED" -> "FAILED"
                            else -> threadStatus ?: localStatus ?: "SENT"
                        }
                    }
                    ChatListItem(
                        chatId = thread.id,
                        peerUid = otherUid,
                        title = title,
                        handle = if (isSelf) (myProfile?.kelMiHandle ?: "vault") else (contact?.kelMiHandle ?: cachedBundle?.kelMiHandle ?: "kelmi"),
                        qqNumber = if (isSelf) (myProfile?.qqNumber ?: "") else (contact?.qqNumber ?: cachedBundle?.qqNumber ?: ""),
                        avatarColorHex = contact?.avatarColorHex ?: cachedBundle?.avatarColorHex ?: KelMiRepository.pickAvatarColor(otherUid),
                        peerPreferredLanguage = contact?.preferredLanguage ?: cachedBundle?.preferredLanguage ?: "English",
                        peerAgeYears = (contact?.ageYears ?: cachedBundle?.ageYears ?: 0L).toInt(),
                        peerGender = contact?.gender ?: cachedBundle?.gender ?: "Prefer not to say",
                        peerLocationLabel = contact?.locationLabel ?: cachedBundle?.locationLabel ?: "",
                        peerTimeZoneId = contact?.timeZoneId ?: cachedBundle?.timeZoneId ?: "",
                        isSelfVault = isSelf,
                        lastSnippet = snippet,
                        lastTimestampMillis = latestMsg?.timestampMillis
                            ?: thread.lastMessageAt?.toDate()?.time
                            ?: thread.updatedAt?.toDate()?.time
                            ?: System.currentTimeMillis(),
                        isVerified = isSelf || (contact?.verifiedSafetyNumber == true),
                        lastSenderIsMe = latestIsMe,
                        lastMessageReceiptStatus = latestReceipt
                    )
                }
                UiState.Success(items)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    val activeChatMessages: StateFlow<List<DecryptedMessageEntity>> = _activeChat
        .flatMapLatest { session ->
            if (session == null) flowOf(emptyList())
            else repository.observeDecryptedChatMessages(currentUserId, session.chatId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val vetnyMessages: StateFlow<UiState<List<VetnyMessage>>> = repository.observeVetnyMessages(currentUserId)
        .map<List<VetnyMessage>, UiState<List<VetnyMessage>>> { UiState.Success(it) }
        .catch { error -> emit(handleStreamError("vetnyMessages", error)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    val moments: StateFlow<UiState<List<MomentPost>>> = repository.observeMoments(currentUserId)
        .map<List<MomentPost>, UiState<List<MomentPost>>> { UiState.Success(it) }
        .catch { error -> emit(handleStreamError("moments", error)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    val onlineUsers: StateFlow<UiState<List<OnlineUser>>> = socialRepository.observeOnlineUsers(currentUserId)
        .map<List<OnlineUser>, UiState<List<OnlineUser>>> { UiState.Success(it) }
        .catch { error -> emit(handleStreamError("onlineUsers", error)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    val reels: StateFlow<UiState<List<ReelPost>>> = combine(
        socialRepository.observePublicReels(currentUserId)
            .map<List<ReelPost>, UiState<List<ReelPost>>> { UiState.Success(it) }
            .catch { error -> emit(handleStreamError("publicReels", error)) },
        socialRepository.observeContactReels(currentUserId)
            .map<List<ReelPost>, UiState<List<ReelPost>>> { UiState.Success(it) }
            .catch { error -> emit(handleStreamError("contactReels", error)) }
    ) { pubState, contactState ->
        when {
            pubState is UiState.Loading && contactState is UiState.Loading -> UiState.Loading
            pubState is UiState.Error && contactState is UiState.Error -> UiState.Error(pubState.message)
            else -> {
                val merged = ((pubState as? UiState.Success)?.data.orEmpty() +
                    (contactState as? UiState.Success)?.data.orEmpty())
                    .distinctBy { it.id }
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
                UiState.Success(merged)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    val stories: StateFlow<UiState<List<StoryPost>>> = socialRepository.observeStories(currentUserId)
        .map<List<StoryPost>, UiState<List<StoryPost>>> { UiState.Success(it) }
        .catch { error -> emit(handleStreamError("stories", error)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    val activeReelComments: StateFlow<List<ReelComment>> = _activeCommentReelId
        .flatMapLatest { reelId ->
            if (reelId == null) flowOf(emptyList())
            else socialRepository.observeReelComments(reelId)
                .catch { emit(emptyList()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    init {
        bootstrapAccount()
        observeAutoTranslationPipeline()
        observeDeliveryAcknowledgements()
    }

    private fun observeDeliveryAcknowledgements() {
        viewModelScope.launch {
            chatThreads.collect { state ->
                val threads = (state as? UiState.Success)?.data ?: return@collect
                val activeId = _activeChat.value?.chatId
                threads.filter { thread ->
                    thread.id != activeId &&
                        thread.lastSenderId != null &&
                        thread.lastSenderId != currentUserId &&
                        thread.lastMessageReceiptStatus == "SENT"
                }.forEach { thread ->
                    launch {
                        repository.markChatMessagesDelivered(thread.id)
                    }
                }
            }
        }
    }

    private fun bootstrapAccount() {
        viewModelScope.launch {
            val user = Firebase.auth.currentUser ?: return@launch
            val res = repository.bootstrapUserAndKeys(user, _liveLocationLabel.value)
            res.onSuccess { profile ->
                _keyDiagnostics.value = signalManager.getDiagnostics()
                socialRepository.syncOnlinePresence(profile, onlineStatus = "ONLINE", discoverable = true)
            }.onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Account setup failed"
            }
        }
    }

    private fun observeAutoTranslationPipeline() {
        viewModelScope.launch {
            combine(activeChatMessages, userProfile) { msgs, profileState ->
                Pair(msgs, (profileState as? UiState.Success)?.data)
            }.collect { (msgs, profile) ->
                if (profile != null && profile.autoTranslateEnabled) {
                    val targetLang = profile.preferredLanguage
                    val pending = msgs.filter { msg ->
                        msg.translatedText == null &&
                            msg.senderId != currentUserId &&
                            msg.messageType == "TEXT" &&
                            !msg.plaintext.startsWith("[") &&
                            autoTranslatedMessageIds.add("${msg.compositeKey}:$targetLang")
                    }
                    pending.forEach { msg ->
                        launch {
                            translateSingleMessage(msg, targetLang)
                        }
                    }
                }
            }
        }
    }

    fun updateLiveLocation(locationLabel: String) {
        _liveLocationLabel.value = locationLabel
    }

    fun updateLiveCoordinates(lat: Double, lng: Double) {
        _liveCoordinates.value = lat to lng
    }

    fun selectTab(tab: KelMiTab) {
        _selectedTab.value = tab
    }

    fun openVetnyAssistant() {
        _isVetnyOpen.value = true
    }

    fun closeVetnyAssistant() {
        stopVetnyLiveVoice(saveTranscriptToHistory = false)
        _isVetnyOpen.value = false
    }

    fun openQrScanner() {
        _isQrScannerOpen.value = true
    }

    fun closeQrScanner() {
        _isQrScannerOpen.value = false
    }

    fun openProfileEditor() {
        _isProfileSetupEditOpen.value = true
    }

    fun closeProfileEditor() {
        _isProfileSetupEditOpen.value = false
    }

    fun clearStatusBanner() {
        _statusBanner.value = null
    }

    fun saveProfileDetails(
        displayName: String,
        email: String,
        phoneNumber: String,
        kelMiHandle: String,
        gender: String,
        dateOfBirth: String,
        interests: List<String>,
        locationLabel: String,
        timeZoneId: String,
        preferredLanguage: String,
        autoTranslateEnabled: Boolean
    ) {
        val current = (userProfile.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            val res = repository.saveProfileDetails(
                currentProfile = current,
                displayName = displayName,
                email = email,
                phoneNumber = phoneNumber,
                kelMiHandle = kelMiHandle,
                gender = gender,
                dateOfBirth = dateOfBirth,
                interests = interests,
                locationLabel = locationLabel,
                timeZoneId = timeZoneId,
                preferredLanguage = preferredLanguage,
                autoTranslateEnabled = autoTranslateEnabled
            )
            res.onSuccess {
                _isProfileSetupEditOpen.value = false
                _statusBanner.value = "Profile & encryption identity saved"
                val updated = current.copy(
                    displayName = displayName.trim(),
                    kelMiHandle = kelMiHandle.trim().lowercase(),
                    gender = gender,
                    interests = interests,
                    locationLabel = locationLabel,
                    timeZoneId = timeZoneId,
                    preferredLanguage = preferredLanguage
                )
                socialRepository.syncOnlinePresence(updated, onlineStatus = "ONLINE", discoverable = true)
            }.onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Failed to save profile"
            }
        }
    }

    fun openChatWithPeer(peerUid: String) {
        viewModelScope.launch {
            if (!peerBundleCache.containsKey(peerUid)) {
                repository.getPublicBundle(peerUid)?.let { peerBundleCache[peerUid] = it }
            }
            val result = repository.openOrCreateChat(peerUid)
            result.onSuccess { session ->
                _activeChat.value = session
                _draftTranslationState.value = null
                startDecryptListener(session.chatId)
                startCallListener(session)
            }.onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Failed to open encrypted chat"
            }
        }
    }

    fun closeActiveChat() {
        chatDecryptJob?.cancel()
        chatDecryptJob = null
        callListenerJob?.cancel()
        callListenerJob = null
        _activeChat.value = null
        _draftTranslationState.value = null
    }

    private fun startDecryptListener(chatId: String) {
        chatDecryptJob?.cancel()
        chatDecryptJob = viewModelScope.launch {
            repository.observeAndDecryptIncomingMessages(currentUserId, chatId)
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    Log.w(TAG, "Chat decrypt listener error", fsErr ?: error)
                    _statusBanner.value = (fsErr ?: error).localizedMessage ?: "Sync error"
                }
                .collect { }
        }
    }

    private fun startCallListener(session: ActiveChatSession) {
        callListenerJob?.cancel()
        callListenerJob = viewModelScope.launch {
            socialRepository.observeIncomingCallsForChat(currentUserId, session.chatId)
                .catch { }
                .collect { calls ->
                    val ringingForMe = calls.firstOrNull {
                        it.status == "RINGING" && it.calleeId == currentUserId && it.callerId != currentUserId
                    }
                    if (ringingForMe != null && _activeCall.value == null) {
                        _incomingCall.value = ActiveCallUiState(
                            chatId = session.chatId,
                            callId = ringingForMe.id,
                            peerUid = session.peerUid,
                            peerName = session.peerName,
                            peerAvatarColorHex = session.peerAvatarColorHex,
                            peerPreferredLanguage = session.peerPreferredLanguage,
                            callType = ringingForMe.callType,
                            e2eeFingerprint = ringingForMe.e2eeFingerprint,
                            incoming = true,
                            status = "RINGING"
                        )
                    } else if (ringingForMe == null) {
                        _incomingCall.value = null
                    }
                }
        }
    }

    fun sendEncryptedMessage(text: String, messageType: String = "TEXT") {
        val session = _activeChat.value ?: return
        viewModelScope.launch {
            val res = repository.sendEncryptedMessage(
                chatId = session.chatId,
                peerUid = session.peerUid,
                plaintext = text,
                messageType = messageType
            )
            res.onSuccess {
                _draftTranslationState.value = null
                _keyDiagnostics.value = signalManager.getDiagnostics()
            }.onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Failed to encrypt and send message"
            }
        }
    }

    fun translateSingleMessage(msg: DecryptedMessageEntity, targetLanguage: String) {
        viewModelScope.launch {
            val result = vetnyAiService.translateMessage(msg.plaintext, targetLanguage)
            result.onSuccess { translation ->
                repository.saveMessageTranslation(msg.compositeKey, translation, targetLanguage)
            }.onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Vetny translation failed"
            }
        }
    }

    fun translateDraftBeforeSend(draft: String, targetLanguage: String) {
        if (draft.isBlank()) return
        viewModelScope.launch {
            _isDraftTranslating.value = true
            val res = vetnyAiService.translateMessage(
                text = draft,
                targetLanguage = targetLanguage,
                contextHint = "Outgoing chat message"
            )
            _isDraftTranslating.value = false
            res.onSuccess { _draftTranslationState.value = it }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Vetny translation failed" }
        }
    }

    fun clearDraftTranslation() {
        _draftTranslationState.value = null
    }

    fun addContactByHandle(handleOrQr: String, openChatImmediately: Boolean = false) {
        viewModelScope.launch {
            val res = repository.addContactByHandle(handleOrQr)
            res.onSuccess { contact ->
                _statusBanner.value = "Added ${contact.displayName} (@${contact.kelMiHandle})"
                if (openChatImmediately) {
                    openChatWithPeer(contact.contactUid)
                }
            }.onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Could not add contact"
            }
        }
    }

    fun toggleSafetyNumberVerified() {
        val session = _activeChat.value ?: return
        val newVerified = !session.isVerified
        viewModelScope.launch {
            repository.toggleContactVerified(session.peerUid, newVerified)
                .onSuccess {
                    _activeChat.value = session.copy(isVerified = newVerified)
                    _statusBanner.value = if (newVerified) {
                        "Safety Number marked as verified"
                    } else {
                        "Verification cleared"
                    }
                }
        }
    }

    fun updateTranslationPreferences(preferredLanguage: String, autoTranslateEnabled: Boolean) {
        viewModelScope.launch {
            repository.updateTranslationPreferences(preferredLanguage, autoTranslateEnabled)
                .onFailure { err ->
                    _statusBanner.value = err.localizedMessage ?: "Could not update preferences"
                }
        }
    }

    fun rotateSignedPreKey() {
        viewModelScope.launch {
            repository.rotateSignedPreKeyAndSync()
                .onSuccess { diag ->
                    _keyDiagnostics.value = diag
                    _statusBanner.value = "Rotated Signed PreKey #${diag.signedPreKeyId}"
                }
                .onFailure { err ->
                    _statusBanner.value = err.localizedMessage ?: "Key rotation failed"
                }
        }
    }

    fun replenishOneTimePreKeys() {
        viewModelScope.launch {
            repository.replenishOneTimePreKeys()
                .onSuccess { diag ->
                    _keyDiagnostics.value = diag
                    _statusBanner.value = "Replenished ${diag.localOpkCount} One-Time PreKeys"
                }
                .onFailure { err ->
                    _statusBanner.value = err.localizedMessage ?: "OPK replenishment failed"
                }
        }
    }

    fun setVetnyAgentMode(mode: VetnyAgentMode) {
        _vetnyAgentMode.value = mode
        if (_vetnyStreamState.value is AiStreamState.Error) {
            _vetnyStreamState.value = AiStreamState.Idle
        }
    }

    fun setVetnyChatModel(model: VetnyChatModel) {
        _vetnyChatModel.value = model
    }

    fun setVetnyImageModel(model: VetnyImageModel) {
        _vetnyImageModel.value = model
    }

    fun setVetnyImageAspect(aspect: VetnyImageAspect) {
        _vetnyImageAspect.value = aspect
    }

    @OptIn(PublicPreviewAPI::class)
    @SuppressLint("MissingPermission")
    fun startVetnyLiveVoice(
        targetLanguage: String,
        interpreterMode: Boolean,
        voiceName: String = "AOEDE"
    ) {
        if (_vetnyLiveState.value.isConnecting || _vetnyLiveState.value.isActive) return
        liveVoiceJob?.cancel()
        liveVoiceJob = viewModelScope.launch {
            _vetnyLiveState.value = VetnyLiveSessionState(
                isConnecting = true,
                isActive = false,
                voiceName = voiceName,
                interpreterMode = interpreterMode
            )
            try {
                val profile = (userProfile.value as? UiState.Success)?.data
                val coords = _liveCoordinates.value
                val userContext = VetnyAiService.UserContextSnapshot(
                    displayName = profile?.displayName?.ifBlank { "Friend" } ?: "Friend",
                    handle = profile?.kelMiHandle ?: "kelmi",
                    locationLabel = _liveLocationLabel.value ?: profile?.locationLabel.orEmpty(),
                    localTime = LocationTimeHelper.formatLocalTime(profile?.timeZoneId),
                    preferredLanguage = profile?.preferredLanguage ?: targetLanguage,
                    latitude = coords?.first,
                    longitude = coords?.second
                )
                val session = vetnyAiService.connectLiveVoiceSession(
                    voiceName = voiceName,
                    targetLanguage = targetLanguage,
                    interpreterMode = interpreterMode,
                    userContext = userContext
                )
                activeLiveSession = session
                _vetnyLiveState.value = _vetnyLiveState.value.copy(
                    isConnecting = false,
                    isActive = true,
                    error = null
                )
                val config = liveAudioConversationConfig {
                    enableInterruptions = true
                    transcriptHandler = { inputTx, outputTx ->
                        val inText = inputTx?.text.orEmpty().trim()
                        val outText = outputTx?.text.orEmpty().trim()
                        if (inText.isNotEmpty() || outText.isNotEmpty()) {
                            _vetnyLiveState.value = _vetnyLiveState.value.copy(
                                userTranscript = if (inText.isNotEmpty()) inText else _vetnyLiveState.value.userTranscript,
                                modelTranscript = if (outText.isNotEmpty()) outText else _vetnyLiveState.value.modelTranscript
                            )
                        }
                    }
                    goAwayHandler = {
                        _vetnyLiveState.value = _vetnyLiveState.value.copy(
                            isActive = false,
                            error = "Live voice session time limit reached."
                        )
                    }
                }
                session.startAudioConversation(config)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Vetny Live Voice failed", e)
                _vetnyLiveState.value = _vetnyLiveState.value.copy(
                    isConnecting = false,
                    isActive = false,
                    error = e.localizedMessage ?: "Live voice connection failed."
                )
            }
        }
    }

    @OptIn(PublicPreviewAPI::class)
    fun sendLiveVoiceTextPrompt(text: String) {
        val clean = text.trim()
        val session = activeLiveSession ?: return
        if (clean.isEmpty()) return
        viewModelScope.launch {
            try {
                _vetnyLiveState.value = _vetnyLiveState.value.copy(userTranscript = clean)
                session.send(clean)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed sending text to live session", e)
            }
        }
    }

    @OptIn(PublicPreviewAPI::class)
    fun stopVetnyLiveVoice(
        saveTranscriptToHistory: Boolean = true,
        targetLanguage: String = "English"
    ) {
        val snapshot = _vetnyLiveState.value
        val session = activeLiveSession
        activeLiveSession = null
        liveVoiceJob?.cancel()
        liveVoiceJob = null
        _vetnyLiveState.value = VetnyLiveSessionState(
            voiceName = snapshot.voiceName,
            interpreterMode = snapshot.interpreterMode
        )

        viewModelScope.launch {
            try {
                session?.stopAudioConversation()
                session?.close()
            } catch (_: Exception) {
            }
            if (saveTranscriptToHistory &&
                (snapshot.userTranscript.isNotBlank() || snapshot.modelTranscript.isNotBlank())
            ) {
                val promptLabel = snapshot.userTranscript.ifBlank { "Live Voice Session" }
                val replyLabel = snapshot.modelTranscript.ifBlank { "Completed live voice conversation with Vetny." }
                repository.saveVetnyMessage(
                    senderRole = "USER",
                    originalText = promptLabel,
                    translatedText = null,
                    sourceLanguage = null,
                    targetLanguage = targetLanguage,
                    culturalNote = null,
                    messageType = "TEXT",
                    agentMode = VetnyAgentMode.VOICE_LIVE.wireValue,
                    modelUsed = VetnyAiService.LIVE_VOICE_MODEL_ID
                )
                repository.saveVetnyMessage(
                    senderRole = "VETNY",
                    originalText = promptLabel,
                    translatedText = replyLabel,
                    sourceLanguage = "Auto",
                    targetLanguage = targetLanguage,
                    culturalNote = "Gemini 3.8 Live Voice",
                    messageType = "TEXT",
                    agentMode = VetnyAgentMode.VOICE_LIVE.wireValue,
                    modelUsed = VetnyAiService.LIVE_VOICE_MODEL_ID
                )
            }
        }
    }

    fun clearVetnyConversation() {
        stopVetnyLiveVoice(saveTranscriptToHistory = false)
        viewModelScope.launch {
            _vetnyStreamState.value = AiStreamState.Idle
            repository.clearVetnyMessages()
                .onSuccess { _statusBanner.value = "Started fresh Vetny conversation" }
                .onFailure { err -> _statusBanner.value = err.localizedMessage ?: "Failed to clear chat" }
        }
    }

    fun sendVetnyAssistantPrompt(
        prompt: String,
        targetLanguage: String,
        attachedBitmap: Bitmap? = null
    ) {
        val clean = prompt.trim()
        if (clean.isEmpty()) return

        val activeMode = _vetnyAgentMode.value
        val looksLikePhotoRequest = activeMode == VetnyAgentMode.IMAGE || isExplicitPhotoPrompt(clean)
        if (looksLikePhotoRequest) {
            if (activeMode != VetnyAgentMode.IMAGE) {
                _vetnyAgentMode.value = VetnyAgentMode.IMAGE
            }
            generateVetnyPhoto(clean, targetLanguage, attachedBitmap)
            return
        }

        if (activeMode == VetnyAgentMode.VOICE_LIVE && _vetnyLiveState.value.isActive) {
            sendLiveVoiceTextPrompt(clean)
            return
        }

        viewModelScope.launch {
            val chatModel = _vetnyChatModel.value
            val effectiveModelId = when (activeMode) {
                VetnyAgentMode.SEARCH, VetnyAgentMode.MAPS -> VetnyChatModel.GEMINI_4_HYDROGEN.modelId
                else -> chatModel.modelId
            }
            val attachedBase64 = attachedBitmap?.let {
                withContext(Dispatchers.Default) {
                    MediaEncodeHelper.compressBitmapToBase64(it, maxDim = 560, quality = 74)
                }
            }
            val historyMsgs = (vetnyMessages.value as? UiState.Success)?.data.orEmpty().takeLast(30)
            val history = historyMsgs.map { m ->
                val bodyText = if (m.senderRole == "USER") {
                    m.originalText
                } else {
                    if (m.messageType == "IMAGE") {
                        "[Previously generated AI photo for prompt: \"${m.originalText}\"] ${m.translatedText.orEmpty()}"
                    } else {
                        m.translatedText ?: m.originalText
                    }
                }
                content(if (m.senderRole == "USER") "user" else "model") {
                    text(bodyText)
                }
            }

            repository.saveVetnyMessage(
                senderRole = "USER",
                originalText = clean,
                translatedText = null,
                sourceLanguage = null,
                targetLanguage = targetLanguage,
                culturalNote = null,
                messageType = if (attachedBase64 != null) "IMAGE" else "TEXT",
                agentMode = activeMode.wireValue,
                modelUsed = effectiveModelId,
                mediaBase64 = attachedBase64
            )

            val profile = (userProfile.value as? UiState.Success)?.data
            val coords = _liveCoordinates.value
            val userContext = VetnyAiService.UserContextSnapshot(
                displayName = profile?.displayName?.ifBlank { "Friend" } ?: "Friend",
                handle = profile?.kelMiHandle ?: "kelmi",
                locationLabel = _liveLocationLabel.value ?: profile?.locationLabel.orEmpty(),
                localTime = LocationTimeHelper.formatLocalTime(profile?.timeZoneId),
                preferredLanguage = profile?.preferredLanguage ?: targetLanguage,
                latitude = coords?.first,
                longitude = coords?.second
            )

            _vetnyStreamState.value = AiStreamState.Loading
            val accumulated = StringBuilder()
            var latestGrounding: VetnyGroundingPayload? = null
            var actualModelUsed = effectiveModelId
            try {
                vetnyAiService.streamSupportChatReply(
                    userPrompt = clean,
                    mode = activeMode,
                    chatModel = chatModel,
                    targetLanguage = targetLanguage,
                    userContext = userContext,
                    conversationHistory = history,
                    attachedBitmap = attachedBitmap
                ).collect { chunk ->
                    accumulated.clear()
                    accumulated.append(chunk.partialText)
                    if (chunk.grounding != null) {
                        latestGrounding = chunk.grounding
                    }
                    actualModelUsed = chunk.modelIdUsed
                    _vetnyStreamState.value = AiStreamState.Streaming(chunk.partialText, latestGrounding)
                }
                val finalReply = accumulated.toString()
                _vetnyStreamState.value = AiStreamState.Success(finalReply)
                repository.saveVetnyMessage(
                    senderRole = "VETNY",
                    originalText = clean,
                    translatedText = finalReply,
                    sourceLanguage = "Auto",
                    targetLanguage = targetLanguage,
                    culturalNote = null,
                    messageType = "TEXT",
                    agentMode = activeMode.wireValue,
                    modelUsed = actualModelUsed,
                    groundingJson = latestGrounding?.toJsonString()
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Vetny assistant streaming failed", e)
                _vetnyStreamState.value = AiStreamState.Error(
                    e.localizedMessage ?: "Vetny could not complete the request."
                )
            }
        }
    }

    fun generateVetnyPhoto(
        prompt: String,
        targetLanguage: String,
        referenceBitmap: Bitmap? = null
    ) {
        val clean = prompt.trim()
        if (clean.isEmpty()) return
        val imageModel = _vetnyImageModel.value
        val aspect = _vetnyImageAspect.value

        viewModelScope.launch {
            val refBase64 = referenceBitmap?.let {
                withContext(Dispatchers.Default) {
                    MediaEncodeHelper.compressBitmapToBase64(it, maxDim = 560, quality = 74)
                }
            }
            val recentSummary = (vetnyMessages.value as? UiState.Success)?.data.orEmpty()
                .takeLast(6)
                .joinToString(" | ") { "${it.senderRole}: ${(it.translatedText ?: it.originalText).take(120)}" }

            repository.saveVetnyMessage(
                senderRole = "USER",
                originalText = clean,
                translatedText = null,
                sourceLanguage = null,
                targetLanguage = targetLanguage,
                culturalNote = null,
                messageType = if (refBase64 != null) "IMAGE" else "TEXT",
                agentMode = VetnyAgentMode.IMAGE.wireValue,
                modelUsed = imageModel.modelId,
                mediaBase64 = refBase64
            )

            _vetnyStreamState.value = AiStreamState.GeneratingImage(clean, imageModel.displayName)
            val result = vetnyAiService.generateOrEditPhoto(
                prompt = clean,
                imageModel = imageModel,
                aspect = aspect,
                referenceBitmap = referenceBitmap,
                recentContextSummary = recentSummary
            )
            result.onSuccess { photoResult ->
                val encodedImage = withContext(Dispatchers.Default) {
                    MediaEncodeHelper.compressBitmapToBase64(photoResult.bitmap, maxDim = 768, quality = 80)
                }
                repository.saveVetnyMessage(
                    senderRole = "VETNY",
                    originalText = clean,
                    translatedText = photoResult.caption,
                    sourceLanguage = "Auto",
                    targetLanguage = targetLanguage,
                    culturalNote = aspect.label,
                    messageType = "IMAGE",
                    agentMode = VetnyAgentMode.IMAGE.wireValue,
                    modelUsed = photoResult.modelUsed,
                    mediaBase64 = encodedImage
                )
                _vetnyStreamState.value = AiStreamState.Idle
            }.onFailure { err ->
                Log.e(TAG, "Vetny image generation failed", err)
                _vetnyStreamState.value = AiStreamState.Error(
                    err.localizedMessage ?: "Photo generation failed."
                )
            }
        }
    }

    fun postGeneratedPhotoToReels(bitmap: Bitmap, caption: String) {
        viewModelScope.launch {
            val base64 = withContext(Dispatchers.Default) {
                MediaEncodeHelper.compressBitmapToBase64(bitmap, maxDim = 720, quality = 78)
            }
            val profile = (userProfile.value as? UiState.Success)?.data ?: return@launch
            val loc = _liveLocationLabel.value ?: profile.locationLabel
            postReel(
                caption = caption.ifBlank { "Created with Vetny AI Photo Studio in Kel mi" },
                mediaType = "IMAGE",
                mediaBase64 = base64,
                localMediaUri = null,
                durationSeconds = 0,
                songId = null,
                songTitle = null,
                songArtist = null,
                locationTag = loc,
                filterName = "Original",
                isPublic = true
            )
        }
    }

    private fun isExplicitPhotoPrompt(prompt: String): Boolean {
        val lower = prompt.lowercase().trim()
        if (lower.startsWith("/image ") || lower.startsWith("/photo ")) return true
        val triggers = listOf(
            "generate a photo",
            "generate photo",
            "generate an image",
            "generate image",
            "create a photo",
            "create photo",
            "create an image",
            "create image",
            "make a photo",
            "make an image",
            "draw a picture",
            "draw an image"
        )
        return triggers.any { lower.contains(it) }
    }

    fun postMoment(contentText: String, languageTag: String, locationTag: String) {
        val profile = (userProfile.value as? UiState.Success)?.data ?: return
        val contactUids = (contacts.value as? UiState.Success)?.data?.map { it.contactUid }.orEmpty()
        viewModelScope.launch {
            repository.postMoment(profile, contactUids, contentText, languageTag, locationTag)
                .onFailure { err ->
                    _statusBanner.value = err.localizedMessage ?: "Could not post Moment"
                }
        }
    }

    fun toggleMomentLike(moment: MomentPost) {
        viewModelScope.launch {
            repository.toggleMomentLike(moment)
        }
    }

    fun translateMoment(moment: MomentPost, targetLanguage: String) {
        viewModelScope.launch {
            _momentTranslations.value = _momentTranslations.value + (
                moment.id to MomentTranslationState(isLoading = true)
            )
            val res = vetnyAiService.translateMessage(moment.content, targetLanguage)
            res.onSuccess { translation ->
                _momentTranslations.value = _momentTranslations.value + (
                    moment.id to MomentTranslationState(isLoading = false, result = translation)
                )
            }.onFailure { err ->
                _momentTranslations.value = _momentTranslations.value + (
                    moment.id to MomentTranslationState(
                        isLoading = false,
                        error = err.localizedMessage ?: "Translation failed"
                    )
                )
            }
        }
    }

    // --- ONLINE PEOPLE CONNECTION ---

    fun connectWithOnlineUser(user: OnlineUser, openChatImmediately: Boolean = false) {
        viewModelScope.launch {
            socialRepository.connectWithOnlineUser(user)
                .onSuccess { contact ->
                    _statusBanner.value = "Connected with ${contact.displayName} (@${contact.kelMiHandle})"
                    if (openChatImmediately) {
                        openChatWithPeer(contact.contactUid)
                    }
                }
                .onFailure { err ->
                    _statusBanner.value = err.localizedMessage ?: "Could not connect with user"
                }
        }
    }

    fun setDiscoverableOnline(discoverable: Boolean) {
        val profile = (userProfile.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            socialRepository.syncOnlinePresence(
                profile = profile,
                onlineStatus = if (discoverable) "ONLINE" else "AWAY",
                discoverable = discoverable
            ).onSuccess {
                _statusBanner.value = if (discoverable) {
                    "Visible to people online worldwide"
                } else {
                    "Hidden from global online discovery"
                }
            }
        }
    }

    // --- VOICE & VIDEO CALLS ---

    fun startVoiceOrVideoCall(
        peerUid: String,
        peerName: String,
        peerAvatarColorHex: String,
        peerPreferredLanguage: String,
        callType: String
    ) {
        viewModelScope.launch {
            val session = _activeChat.value?.takeIf { it.peerUid == peerUid }
                ?: repository.openOrCreateChat(peerUid).getOrNull()
            if (session == null) {
                _statusBanner.value = "Unable to initialize encrypted call channel"
                return@launch
            }
            val fingerprint = session.safetyNumber.replace(" ", "").take(16)
            socialRepository.startCallSession(
                chatId = session.chatId,
                peerUid = peerUid,
                callType = callType,
                e2eeFingerprint = fingerprint
            ).onSuccess { call ->
                _callTranslation.value = null
                _activeCall.value = ActiveCallUiState(
                    chatId = session.chatId,
                    callId = call.id,
                    peerUid = peerUid,
                    peerName = peerName,
                    peerAvatarColorHex = peerAvatarColorHex,
                    peerPreferredLanguage = peerPreferredLanguage,
                    callType = callType,
                    e2eeFingerprint = fingerprint,
                    incoming = false,
                    status = call.status
                )
            }.onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Failed to start call"
            }
        }
    }

    fun acceptIncomingCall() {
        val incoming = _incomingCall.value ?: return
        _incomingCall.value = null
        _callTranslation.value = null
        _activeCall.value = incoming.copy(status = "ACTIVE")
        viewModelScope.launch {
            socialRepository.updateCallStatus(incoming.chatId, incoming.callId, "ACTIVE", 0L)
        }
    }

    fun declineIncomingCall() {
        val incoming = _incomingCall.value ?: return
        _incomingCall.value = null
        viewModelScope.launch {
            socialRepository.updateCallStatus(incoming.chatId, incoming.callId, "DECLINED", 0L)
        }
    }

    fun endActiveCall(durationSeconds: Long) {
        val call = _activeCall.value ?: return
        _activeCall.value = null
        _callTranslation.value = null
        viewModelScope.launch {
            socialRepository.updateCallStatus(call.chatId, call.callId, "ENDED", durationSeconds)
            val mins = durationSeconds / 60
            val secs = durationSeconds % 60
            val label = if (call.callType == "VIDEO") "Encrypted Video Call" else "Encrypted Voice Call"
            val summary = String.format("%s · %02d:%02d", label, mins, secs)
            repository.sendEncryptedMessage(
                chatId = call.chatId,
                peerUid = call.peerUid,
                plaintext = summary,
                messageType = "CALL_LOG"
            )
        }
    }

    fun translateCallPhrase(phrase: String, targetLanguage: String) {
        val clean = phrase.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            vetnyAiService.translateMessage(clean, targetLanguage, "Live voice/video call caption")
                .onSuccess { _callTranslation.value = it }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Vetny call translation failed" }
        }
    }

    fun clearCallTranslation() {
        _callTranslation.value = null
    }

    // --- REELS, SONGS, COMMENTS & STORIES (INSTAGRAM FEATURES) ---

    fun postReel(
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
    ) {
        val profile = (userProfile.value as? UiState.Success)?.data ?: return
        val contactUids = (contacts.value as? UiState.Success)?.data?.map { it.contactUid }.orEmpty()
        viewModelScope.launch {
            socialRepository.postReel(
                profile = profile,
                contactUids = contactUids,
                caption = caption,
                mediaType = mediaType,
                mediaBase64 = mediaBase64,
                localMediaUri = localMediaUri,
                durationSeconds = durationSeconds,
                songId = songId,
                songTitle = songTitle,
                songArtist = songArtist,
                locationTag = locationTag,
                filterName = filterName,
                isPublic = isPublic
            ).onSuccess {
                _statusBanner.value = "Reel posted!"
            }.onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Could not publish Reel"
            }
        }
    }

    fun toggleReelLike(reel: ReelPost) {
        viewModelScope.launch {
            socialRepository.toggleReelLike(reel)
        }
    }

    fun toggleReelBookmark(reel: ReelPost) {
        viewModelScope.launch {
            socialRepository.toggleReelBookmark(reel)
                .onSuccess {
                    val saved = currentUserId !in reel.bookmarkedByUids
                    _statusBanner.value = if (saved) "Saved to your Instagram Bookmarks" else "Removed from Bookmarks"
                }
        }
    }

    fun translateReel(reel: ReelPost, targetLanguage: String) {
        viewModelScope.launch {
            _reelTranslations.value = _reelTranslations.value + (
                reel.id to MomentTranslationState(isLoading = true)
            )
            vetnyAiService.translateMessage(reel.caption, targetLanguage)
                .onSuccess { translation ->
                    _reelTranslations.value = _reelTranslations.value + (
                        reel.id to MomentTranslationState(isLoading = false, result = translation)
                    )
                }
                .onFailure { err ->
                    _reelTranslations.value = _reelTranslations.value + (
                        reel.id to MomentTranslationState(isLoading = false, error = err.localizedMessage)
                    )
                }
        }
    }

    fun openReelComments(reelId: String) {
        _activeCommentReelId.value = reelId
    }

    fun closeReelComments() {
        _activeCommentReelId.value = null
    }

    fun addReelComment(reelId: String, text: String) {
        val profile = (userProfile.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            socialRepository.addReelComment(reelId, profile, text)
                .onFailure { err ->
                    _statusBanner.value = err.localizedMessage ?: "Failed to post comment"
                }
        }
    }

    fun postStory(
        caption: String,
        mediaBase64: String?,
        songTitle: String?,
        locationTag: String?
    ) {
        val profile = (userProfile.value as? UiState.Success)?.data ?: return
        val contactUids = (contacts.value as? UiState.Success)?.data?.map { it.contactUid }.orEmpty()
        viewModelScope.launch {
            socialRepository.postStory(profile, contactUids, caption, mediaBase64, songTitle, locationTag)
                .onSuccess { _statusBanner.value = "Story shared!" }
                .onFailure { err -> _statusBanner.value = err.localizedMessage ?: "Could not post story" }
        }
    }

    fun markStoryViewed(story: StoryPost) {
        viewModelScope.launch {
            socialRepository.markStoryViewed(story)
        }
    }

    fun showBanner(message: String) {
        _statusBanner.value = message
    }

    @OptIn(PublicPreviewAPI::class)
    override fun onCleared() {
        super.onCleared()
        try {
            activeLiveSession?.stopAudioConversation()
        } catch (_: Exception) {
        }
        KelMiMusicPlayer.stop()
    }

    private fun <T> handleStreamError(label: String, error: Throwable): UiState<T> {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing $label", rootError)
        return UiState.Error(rootError.message ?: "Failed to load $label")
    }

    private companion object {
        const val TAG = "KelMiViewModel"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
