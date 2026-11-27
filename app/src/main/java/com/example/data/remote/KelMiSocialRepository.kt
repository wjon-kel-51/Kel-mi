package com.example.data.remote

import com.example.data.model.CallSession
import com.example.data.model.OnlineUser
import com.example.data.model.ReelComment
import com.example.data.model.ReelPost
import com.example.data.model.StoryPost
import com.example.data.model.UserContact
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class KelMiSocialRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google.")
    }

    // --- ONLINE PEOPLE DISCOVERY & PRESENCE ---

    suspend fun syncOnlinePresence(
        profile: UserProfile,
        onlineStatus: String = "ONLINE",
        discoverable: Boolean = true
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            require(uid == requireUserId())
            val ref = db.collection("onlineUsers").document(uid)
            val existing = ref.get().await()
            val avatarColor = KelMiRepository.pickAvatarColor(uid)
            if (!existing.exists()) {
                ref.set(
                    mapOf(
                        "userId" to uid,
                        "kelMiHandle" to profile.kelMiHandle,
                        "qqNumber" to profile.qqNumber,
                        "displayName" to profile.displayName.take(60),
                        "avatarColorHex" to avatarColor,
                        "preferredLanguage" to profile.preferredLanguage.take(40),
                        "gender" to profile.gender.take(32),
                        "ageYears" to profile.ageYears,
                        "interests" to profile.interests.take(12),
                        "locationLabel" to profile.locationLabel.take(100),
                        "timeZoneId" to profile.timeZoneId.take(64),
                        "onlineStatus" to onlineStatus,
                        "discoverable" to discoverable,
                        "lastActiveAt" to FieldValue.serverTimestamp(),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                ref.update(
                    mapOf(
                        "kelMiHandle" to profile.kelMiHandle,
                        "displayName" to profile.displayName.take(60),
                        "avatarColorHex" to avatarColor,
                        "preferredLanguage" to profile.preferredLanguage.take(40),
                        "gender" to profile.gender.take(32),
                        "ageYears" to profile.ageYears,
                        "interests" to profile.interests.take(12),
                        "locationLabel" to profile.locationLabel.take(100),
                        "timeZoneId" to profile.timeZoneId.take(64),
                        "onlineStatus" to onlineStatus,
                        "discoverable" to discoverable,
                        "lastActiveAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "onlineUsers/$uid")
            Result.failure(e)
        }
    }

    fun observeOnlineUsers(userId: String): Flow<List<OnlineUser>> = flow {
        val uid = requireUserId()
        check(uid == userId)
        emitAll(
            db.collection("onlineUsers")
                .whereEqualTo("discoverable", true)
                .limit(50)
                .snapshots()
                .map { snap ->
                    snap.toObjects(OnlineUser::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .filter { it.userId != uid }
                        .sortedByDescending { it.lastActiveAt ?: it.updatedAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val fsErr = unwrapFirestoreException(error)
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, "onlineUsers")
                    }
                    throw error
                }
        )
    }

    suspend fun connectWithOnlineUser(onlineUser: OnlineUser): Result<UserContact> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            require(uid == requireUserId())
            val contactRef = db.collection("users").document(uid)
                .collection("contacts").document(onlineUser.userId)
            val existing = contactRef.get().await()
            if (!existing.exists()) {
                contactRef.set(
                    mapOf(
                        "contactUid" to onlineUser.userId,
                        "kelMiHandle" to onlineUser.kelMiHandle,
                        "qqNumber" to onlineUser.qqNumber,
                        "displayName" to onlineUser.displayName,
                        "avatarColorHex" to onlineUser.avatarColorHex,
                        "preferredLanguage" to onlineUser.preferredLanguage,
                        "gender" to onlineUser.gender,
                        "ageYears" to onlineUser.ageYears,
                        "interests" to onlineUser.interests,
                        "locationLabel" to onlineUser.locationLabel,
                        "timeZoneId" to onlineUser.timeZoneId,
                        "verifiedSafetyNumber" to false,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(
                UserContact(
                    id = onlineUser.userId,
                    contactUid = onlineUser.userId,
                    kelMiHandle = onlineUser.kelMiHandle,
                    qqNumber = onlineUser.qqNumber,
                    displayName = onlineUser.displayName,
                    avatarColorHex = onlineUser.avatarColorHex,
                    preferredLanguage = onlineUser.preferredLanguage,
                    gender = onlineUser.gender,
                    ageYears = onlineUser.ageYears,
                    interests = onlineUser.interests,
                    locationLabel = onlineUser.locationLabel,
                    timeZoneId = onlineUser.timeZoneId,
                    verifiedSafetyNumber = false
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$uid/contacts")
            Result.failure(e)
        }
    }

    // --- VOICE & VIDEO CALL SIGNALING ---

    suspend fun startCallSession(
        chatId: String,
        peerUid: String,
        callType: String,
        e2eeFingerprint: String
    ): Result<CallSession> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            require(uid == requireUserId())
            val members = if (uid == peerUid) listOf(uid) else listOf(uid, peerUid).sorted()
            val callRef = db.collection("chats").document(chatId).collection("calls").document()
            val initialStatus = if (uid == peerUid) "ACTIVE" else "RINGING"
            callRef.set(
                mapOf(
                    "callerId" to uid,
                    "calleeId" to peerUid,
                    "memberUids" to members,
                    "callType" to callType,
                    "status" to initialStatus,
                    "e2eeFingerprint" to e2eeFingerprint.take(128),
                    "durationSeconds" to 0L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(
                CallSession(
                    id = callRef.id,
                    callerId = uid,
                    calleeId = peerUid,
                    memberUids = members,
                    callType = callType,
                    status = initialStatus,
                    e2eeFingerprint = e2eeFingerprint
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "chats/$chatId/calls")
            Result.failure(e)
        }
    }

    suspend fun updateCallStatus(
        chatId: String,
        callId: String,
        status: String,
        durationSeconds: Long
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            require(uid == requireUserId())
            db.collection("chats").document(chatId).collection("calls").document(callId).update(
                mapOf(
                    "status" to status,
                    "durationSeconds" to durationSeconds.coerceIn(0L, 86400L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "chats/$chatId/calls/$callId")
            Result.failure(e)
        }
    }

    fun observeIncomingCallsForChat(userId: String, chatId: String): Flow<List<CallSession>> = flow {
        val uid = requireUserId()
        check(uid == userId)
        emitAll(
            db.collection("chats").document(chatId).collection("calls")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(CallSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val fsErr = unwrapFirestoreException(error)
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, "chats/$chatId/calls")
                    }
                    throw error
                }
        )
    }

    // --- REELS (PHOTOS & VIDEOS + IN-APP SONGS + INSTAGRAM FEATURES) ---

    fun observePublicReels(userId: String): Flow<List<ReelPost>> = flow {
        val uid = requireUserId()
        check(uid == userId)
        emitAll(
            db.collection("reels")
                .whereEqualTo("visibility", "PUBLIC")
                .snapshots()
                .map { snap ->
                    snap.toObjects(ReelPost::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val fsErr = unwrapFirestoreException(error)
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, "reels")
                    }
                    throw error
                }
        )
    }

    fun observeContactReels(userId: String): Flow<List<ReelPost>> = flow {
        val uid = requireUserId()
        check(uid == userId)
        emitAll(
            db.collection("reels")
                .whereArrayContains("visibleToUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(ReelPost::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val fsErr = unwrapFirestoreException(error)
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, "reels")
                    }
                    throw error
                }
        )
    }

    suspend fun postReel(
        profile: UserProfile,
        contactUids: List<String>,
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
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            require(uid == requireUserId())
            val cleanCaption = caption.trim().take(600)
            require(cleanCaption.isNotEmpty()) { "Caption is required" }
            val visible = (listOf(uid) + contactUids).distinct().take(50)
            val payload = mutableMapOf<String, Any?>(
                "authorId" to uid,
                "authorName" to profile.displayName.take(60),
                "authorHandle" to profile.kelMiHandle,
                "authorAvatarColor" to KelMiRepository.pickAvatarColor(uid),
                "caption" to cleanCaption,
                "mediaType" to mediaType,
                "mediaBase64" to mediaBase64?.take(255000)?.takeIf { it.isNotBlank() },
                "localMediaUri" to localMediaUri?.take(512)?.takeIf { it.isNotBlank() },
                "durationSeconds" to durationSeconds.coerceIn(0, 600).toLong(),
                "songId" to songId?.take(64)?.takeIf { it.isNotBlank() },
                "songTitle" to songTitle?.take(80)?.takeIf { it.isNotBlank() },
                "songArtist" to songArtist?.take(80)?.takeIf { it.isNotBlank() },
                "languageTag" to profile.preferredLanguage.take(40),
                "locationTag" to locationTag.take(100).takeIf { it.isNotBlank() },
                "filterName" to filterName.take(32),
                "visibility" to if (isPublic) "PUBLIC" else "CONTACTS",
                "visibleToUids" to visible,
                "likedByUids" to emptyList<String>(),
                "bookmarkedByUids" to emptyList<String>(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>

            db.collection("reels").document().set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "reels")
            Result.failure(e)
        }
    }

    suspend fun toggleReelLike(reel: ReelPost): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            require(uid == requireUserId())
            val updated = if (uid in reel.likedByUids) reel.likedByUids - uid
            else (reel.likedByUids + uid).distinct().take(50)
            db.collection("reels").document(reel.id).update(
                mapOf("likedByUids" to updated, "updatedAt" to FieldValue.serverTimestamp())
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "reels/${reel.id}")
            Result.failure(e)
        }
    }

    suspend fun toggleReelBookmark(reel: ReelPost): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            require(uid == requireUserId())
            val updated = if (uid in reel.bookmarkedByUids) reel.bookmarkedByUids - uid
            else (reel.bookmarkedByUids + uid).distinct().take(50)
            db.collection("reels").document(reel.id).update(
                mapOf("bookmarkedByUids" to updated, "updatedAt" to FieldValue.serverTimestamp())
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "reels/${reel.id}")
            Result.failure(e)
        }
    }

    fun observeReelComments(reelId: String): Flow<List<ReelComment>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection("reels").document(reelId).collection("comments")
                .snapshots()
                .map { snap ->
                    snap.toObjects(ReelComment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val fsErr = unwrapFirestoreException(error)
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, "reels/$reelId/comments")
                    }
                    throw error
                }
        )
    }

    suspend fun addReelComment(reelId: String, profile: UserProfile, text: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            require(uid == requireUserId())
            val clean = text.trim().take(500)
            require(clean.isNotEmpty())
            db.collection("reels").document(reelId).collection("comments").document().set(
                mapOf(
                    "authorId" to uid,
                    "authorName" to profile.displayName.take(60),
                    "authorHandle" to profile.kelMiHandle,
                    "authorAvatarColor" to KelMiRepository.pickAvatarColor(uid),
                    "text" to clean,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "reels/$reelId/comments")
            Result.failure(e)
        }
    }

    // --- INSTAGRAM STORIES ---

    fun observeStories(userId: String): Flow<List<StoryPost>> = flow {
        val uid = requireUserId()
        check(uid == userId)
        emitAll(
            db.collection("stories")
                .whereEqualTo("visibility", "PUBLIC")
                .snapshots()
                .map { snap ->
                    snap.toObjects(StoryPost::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val fsErr = unwrapFirestoreException(error)
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, "stories")
                    }
                    throw error
                }
        )
    }

    suspend fun postStory(
        profile: UserProfile,
        contactUids: List<String>,
        caption: String,
        mediaBase64: String?,
        songTitle: String?,
        locationTag: String?
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            require(uid == requireUserId())
            val cleanCaption = caption.trim().take(280)
            require(cleanCaption.isNotEmpty())
            val visible = (listOf(uid) + contactUids).distinct().take(50)
            val payload = mutableMapOf<String, Any?>(
                "authorId" to uid,
                "authorName" to profile.displayName.take(60),
                "authorHandle" to profile.kelMiHandle,
                "authorAvatarColor" to KelMiRepository.pickAvatarColor(uid),
                "caption" to cleanCaption,
                "mediaBase64" to mediaBase64?.take(255000)?.takeIf { it.isNotBlank() },
                "songTitle" to songTitle?.take(80)?.takeIf { it.isNotBlank() },
                "locationTag" to locationTag?.take(100)?.takeIf { it.isNotBlank() },
                "visibility" to "PUBLIC",
                "visibleToUids" to visible,
                "viewedByUids" to emptyList<String>(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>

            db.collection("stories").document().set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "stories")
            Result.failure(e)
        }
    }

    suspend fun markStoryViewed(story: StoryPost): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        if (uid in story.viewedByUids) return Result.success(Unit)
        return try {
            require(uid == requireUserId())
            val updated = (story.viewedByUids + uid).distinct().take(50)
            db.collection("stories").document(story.id).update(
                mapOf("viewedByUids" to updated, "updatedAt" to FieldValue.serverTimestamp())
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun unwrapFirestoreException(error: Throwable): FirebaseFirestoreException? {
        return generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
    }
}
