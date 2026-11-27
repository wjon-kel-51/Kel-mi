package com.example.data.remote

import android.util.Log
import com.example.data.local.DecryptedMessageDao
import com.example.data.local.DecryptedMessageEntity
import com.example.data.model.ActiveChatSession
import com.example.data.model.ChatThread
import com.example.data.model.EncryptedMessage
import com.example.data.model.HandleIndex
import com.example.data.model.MomentPost
import com.example.data.model.PublicPreKeyBundle
import com.example.data.model.UserContact
import com.example.data.model.UserProfile
import com.example.data.model.VetnyMessage
import com.example.data.model.VetnyTranslationResult
import com.example.security.SignalProtocolManager
import com.example.util.LocationTimeHelper
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.math.abs

class KelMiRepository(
    private val db: FirebaseFirestore,
    private val signalManager: SignalProtocolManager,
    private val decryptedDao: DecryptedMessageDao,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    suspend fun bootstrapUserAndKeys(user: FirebaseUser, locationLabel: String?): Result<UserProfile> {
        val uid = user.uid
        return try {
            require(uid == requireUserId()) { "Auth state mismatch during bootstrap" }
            val keyBundle = withContext(Dispatchers.Default) {
                signalManager.ensureKeysInitialized(initialOpkTarget = 15)
            }
            val userRef = db.collection("users").document(uid)
            val existingSnap = userRef.get().await()
            val tzId = LocationTimeHelper.getCurrentTimeZoneId()

            if (existingSnap.exists()) {
                val existingProfile = existingSnap.toObject(
                    UserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: UserProfile(userId = uid)

                // Ensure public bundle & OPKs are synced
                syncPublicBundleAndOpks(existingProfile, keyBundle)
                return Result.success(existingProfile)
            }

            val defaultName = user.displayName?.trim()?.takeIf { it.isNotEmpty() } ?: "Kel mi User"
            val email = user.email?.trim().orEmpty()
            val rawPrefix = email.substringBefore("@").lowercase().replace(Regex("[^a-z0-9_]"), "")
            val suffix = uid.take(4).lowercase().replace(Regex("[^a-z0-9]"), "k")
            val defaultHandle = ((rawPrefix.take(14).ifBlank { "kelmi" }) + "_" + suffix)
                .take(32)
                .padEnd(4, '0')
            val qqNum = (10000000 + (abs(uid.hashCode()) % 89999999)).toString()
            val loc = locationLabel?.take(100) ?: "Earth"

            val profilePayload = mapOf(
                "userId" to uid,
                "email" to email,
                "phoneNumber" to "",
                "displayName" to defaultName.take(60),
                "kelMiHandle" to defaultHandle,
                "qqNumber" to qqNum,
                "gender" to "Prefer not to say",
                "dateOfBirth" to "",
                "ageYears" to 0L,
                "interests" to listOf("Languages", "Privacy"),
                "locationLabel" to loc,
                "timeZoneId" to tzId.take(64),
                "preferredLanguage" to "English",
                "autoTranslateEnabled" to true,
                "profileCompleted" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            userRef.set(profilePayload).await()

            val createdProfile = UserProfile(
                id = uid,
                userId = uid,
                email = email,
                phoneNumber = "",
                displayName = defaultName.take(60),
                kelMiHandle = defaultHandle,
                qqNumber = qqNum,
                gender = "Prefer not to say",
                dateOfBirth = "",
                ageYears = 0L,
                interests = listOf("Languages", "Privacy"),
                locationLabel = loc,
                timeZoneId = tzId.take(64),
                preferredLanguage = "English",
                autoTranslateEnabled = true,
                profileCompleted = false
            )

            syncPublicBundleAndOpks(createdProfile, keyBundle)
            Result.success(createdProfile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$uid")
            Result.failure(e)
        }
    }

    private suspend fun syncPublicBundleAndOpks(
        profile: UserProfile,
        keyBundle: SignalProtocolManager.PublicKeyRegistrationBundle
    ) {
        val uid = profile.userId
        val avatarColor = pickAvatarColor(uid)
        val handleRef = db.collection("handles").document(profile.kelMiHandle)
        val handleSnap = handleRef.get().await()
        if (!handleSnap.exists()) {
            handleRef.set(
                mapOf(
                    "userId" to uid,
                    "kelMiHandle" to profile.kelMiHandle,
                    "qqNumber" to profile.qqNumber,
                    "displayName" to profile.displayName,
                    "avatarColorHex" to avatarColor,
                    "preferredLanguage" to profile.preferredLanguage,
                    "gender" to profile.gender,
                    "ageYears" to profile.ageYears,
                    "interests" to profile.interests.take(12),
                    "locationLabel" to profile.locationLabel,
                    "timeZoneId" to profile.timeZoneId,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }

        val dirRef = db.collection("directory").document(uid)
        val dirSnap = dirRef.get().await()
        if (!dirSnap.exists()) {
            dirRef.set(
                mapOf(
                    "userId" to uid,
                    "kelMiHandle" to profile.kelMiHandle,
                    "qqNumber" to profile.qqNumber,
                    "displayName" to profile.displayName,
                    "avatarColorHex" to avatarColor,
                    "preferredLanguage" to profile.preferredLanguage,
                    "gender" to profile.gender,
                    "ageYears" to profile.ageYears,
                    "interests" to profile.interests.take(12),
                    "locationLabel" to profile.locationLabel,
                    "timeZoneId" to profile.timeZoneId,
                    "identityPublicKey" to keyBundle.identityPublicKey,
                    "signedPreKeyId" to keyBundle.signedPreKeyId.toLong(),
                    "signedPreKeyPublic" to keyBundle.signedPreKeyPublic,
                    "signedPreKeySignature" to keyBundle.signedPreKeySignature,
                    "safetyFingerprint" to keyBundle.safetyFingerprint,
                    "opkCount" to keyBundle.oneTimePreKeys.size.toLong(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }

        val existingOpks = dirRef.collection("oneTimePreKeys").get().await()
        if (existingOpks.isEmpty) {
            keyBundle.oneTimePreKeys.forEach { opk ->
                dirRef.collection("oneTimePreKeys").document("opk_${opk.keyId}")
                    .set(
                        mapOf(
                            "keyId" to opk.keyId.toLong(),
                            "publicKey" to opk.publicKey,
                            "ownerUid" to uid,
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
            }
        }
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val uid = requireUserId()
        check(uid == userId)
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { error ->
                    val fsErr = unwrapFirestoreException(error)
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveProfileDetails(
        currentProfile: UserProfile,
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
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            require(uid == requireUserId())
            val cleanHandle = kelMiHandle.trim().lowercase().replace(Regex("[^a-z0-9_]"), "")
            require(cleanHandle.length in 3..32) { "Handle must be 3-32 lowercase letters, digits, or underscores." }
            val cleanName = displayName.trim().take(60)
            require(cleanName.isNotEmpty()) { "Display name is required." }

            val computedAge = if (dateOfBirth.isNotBlank()) {
                LocationTimeHelper.calculateAgeFromDob(dateOfBirth).toLong()
            } else {
                currentProfile.ageYears
            }
            val cleanInterests = interests.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(12)
            val cleanLoc = locationLabel.trim().take(100)
            val cleanTz = timeZoneId.trim().take(64)

            // Check if handle changed and claim new handle safely
            if (cleanHandle != currentProfile.kelMiHandle) {
                val targetHandleSnap = db.collection("handles").document(cleanHandle).get().await()
                if (targetHandleSnap.exists() && targetHandleSnap.getString("userId") != uid) {
                    return Result.failure(IllegalStateException("Handle @$cleanHandle is already taken."))
                }
            }

            db.collection("users").document(uid).update(
                mapOf(
                    "email" to email.trim().take(160),
                    "phoneNumber" to phoneNumber.trim().take(30),
                    "displayName" to cleanName,
                    "kelMiHandle" to cleanHandle,
                    "gender" to gender.take(32),
                    "dateOfBirth" to dateOfBirth.trim().take(20),
                    "ageYears" to computedAge,
                    "interests" to cleanInterests,
                    "locationLabel" to cleanLoc,
                    "timeZoneId" to cleanTz,
                    "preferredLanguage" to preferredLanguage.take(40),
                    "autoTranslateEnabled" to autoTranslateEnabled,
                    "profileCompleted" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            val avatarColor = pickAvatarColor(uid)
            if (cleanHandle != currentProfile.kelMiHandle) {
                db.collection("handles").document(cleanHandle).set(
                    mapOf(
                        "userId" to uid,
                        "kelMiHandle" to cleanHandle,
                        "qqNumber" to currentProfile.qqNumber,
                        "displayName" to cleanName,
                        "avatarColorHex" to avatarColor,
                        "preferredLanguage" to preferredLanguage.take(40),
                        "gender" to gender.take(32),
                        "ageYears" to computedAge,
                        "interests" to cleanInterests,
                        "locationLabel" to cleanLoc,
                        "timeZoneId" to cleanTz,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                if (currentProfile.kelMiHandle.isNotBlank()) {
                    try {
                        db.collection("handles").document(currentProfile.kelMiHandle).delete().await()
                    } catch (_: Exception) { }
                }
            } else {
                db.collection("handles").document(cleanHandle).update(
                    mapOf(
                        "displayName" to cleanName,
                        "preferredLanguage" to preferredLanguage.take(40),
                        "gender" to gender.take(32),
                        "ageYears" to computedAge,
                        "interests" to cleanInterests,
                        "locationLabel" to cleanLoc,
                        "timeZoneId" to cleanTz,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            db.collection("directory").document(uid).update(
                mapOf(
                    "kelMiHandle" to cleanHandle,
                    "displayName" to cleanName,
                    "preferredLanguage" to preferredLanguage.take(40),
                    "gender" to gender.take(32),
                    "ageYears" to computedAge,
                    "interests" to cleanInterests,
                    "locationLabel" to cleanLoc,
                    "timeZoneId" to cleanTz,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$uid")
            Result.failure(e)
        }
    }

    suspend fun updateTranslationPreferences(
        preferredLanguage: String,
        autoTranslateEnabled: Boolean
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            require(uid == requireUserId())
            db.collection("users").document(uid).update(
                mapOf(
                    "preferredLanguage" to preferredLanguage.take(40),
                    "autoTranslateEnabled" to autoTranslateEnabled,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            db.collection("directory").document(uid).update(
                mapOf(
                    "preferredLanguage" to preferredLanguage.take(40),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$uid")
            Result.failure(e)
        }
    }

    fun observeContacts(userId: String): Flow<List<UserContact>> = flow {
        val uid = requireUserId()
        check(uid == userId)
        val path = "users/$uid/contacts"
        emitAll(
            db.collection("users").document(uid).collection("contacts")
                .snapshots()
                .map { snap ->
                    snap.toObjects(UserContact::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.displayName.lowercase() }
                }
                .catch { error ->
                    val fsErr = unwrapFirestoreException(error)
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun addContactByHandle(rawHandleOrUri: String): Result<UserContact> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            require(uid == requireUserId())
            val parsedQr = com.example.util.QrCodeHelper.parseContactQrPayload(rawHandleOrUri)
                ?: return Result.failure(
                    IllegalArgumentException("Enter a valid @handle (3-32 chars) or scan a Kel mi QR link.")
                )

            val cleanHandle = parsedQr.handle.trim().lowercase().removePrefix("@")
            var resolvedIndex: HandleIndex? = null

            if (cleanHandle.matches(Regex("^[a-z0-9_]{3,32}$"))) {
                val handleSnap = db.collection("handles").document(cleanHandle).get().await()
                if (handleSnap.exists()) {
                    resolvedIndex = handleSnap.toObject(HandleIndex::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
            }

            if (resolvedIndex == null && !parsedQr.uid.isNullOrBlank()) {
                val dirSnap = db.collection("directory").document(parsedQr.uid).get().await()
                if (dirSnap.exists()) {
                    val bundle = dirSnap.toObject(PublicPreKeyBundle::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    if (bundle != null) {
                        resolvedIndex = HandleIndex(
                            id = bundle.kelMiHandle,
                            userId = bundle.userId,
                            kelMiHandle = bundle.kelMiHandle,
                            qqNumber = bundle.qqNumber,
                            displayName = bundle.displayName,
                            avatarColorHex = bundle.avatarColorHex,
                            preferredLanguage = bundle.preferredLanguage,
                            gender = bundle.gender,
                            ageYears = bundle.ageYears,
                            interests = bundle.interests,
                            locationLabel = bundle.locationLabel,
                            timeZoneId = bundle.timeZoneId
                        )
                    }
                }
            }

            val index = resolvedIndex
                ?: return Result.failure(
                    IllegalStateException(
                        if (cleanHandle.isNotBlank()) "No Kel mi user found for @$cleanHandle"
                        else "No Kel mi user found for that QR code."
                    )
                )

            if (index.userId == uid) {
                return Result.failure(IllegalArgumentException("That is your own Kel mi QR code."))
            }

            val contactRef = db.collection("users").document(uid)
                .collection("contacts").document(index.userId)
            val existing = contactRef.get().await()
            if (!existing.exists()) {
                contactRef.set(
                    mapOf(
                        "contactUid" to index.userId,
                        "kelMiHandle" to index.kelMiHandle,
                        "qqNumber" to index.qqNumber,
                        "displayName" to index.displayName,
                        "avatarColorHex" to index.avatarColorHex,
                        "preferredLanguage" to index.preferredLanguage,
                        "gender" to index.gender,
                        "ageYears" to index.ageYears,
                        "interests" to index.interests,
                        "locationLabel" to index.locationLabel,
                        "timeZoneId" to index.timeZoneId,
                        "verifiedSafetyNumber" to false,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(
                UserContact(
                    id = index.userId,
                    contactUid = index.userId,
                    kelMiHandle = index.kelMiHandle,
                    qqNumber = index.qqNumber,
                    displayName = index.displayName,
                    avatarColorHex = index.avatarColorHex,
                    preferredLanguage = index.preferredLanguage,
                    gender = index.gender,
                    ageYears = index.ageYears,
                    interests = index.interests,
                    locationLabel = index.locationLabel,
                    timeZoneId = index.timeZoneId,
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

    suspend fun toggleContactVerified(contactUid: String, verified: Boolean): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            require(uid == requireUserId())
            val ref = db.collection("users").document(uid).collection("contacts").document(contactUid)
            if (ref.get().await().exists()) {
                ref.update(
                    mapOf(
                        "verifiedSafetyNumber" to verified,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$uid/contacts/$contactUid")
            Result.failure(e)
        }
    }

    fun observeChats(userId: String): Flow<List<ChatThread>> = flow {
        val uid = requireUserId()
        check(uid == userId)
        emitAll(
            db.collection("chats")
                .whereArrayContains("participantUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(ChatThread::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.lastMessageAt ?: it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val fsErr = unwrapFirestoreException(error)
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, "chats")
                    }
                    throw error
                }
        )
    }

    suspend fun getPublicBundle(userId: String): PublicPreKeyBundle? {
        return try {
            val snap = db.collection("directory").document(userId).get().await()
            if (snap.exists()) {
                snap.toObject(PublicPreKeyBundle::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "directory/$userId")
            null
        }
    }

    suspend fun openOrCreateChat(peerUid: String): Result<ActiveChatSession> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            require(uid == requireUserId())
            val isSelf = uid == peerUid
            val participants = if (isSelf) listOf(uid) else listOf(uid, peerUid).sorted()
            val chatId = if (isSelf) "${uid}__${uid}" else "${participants[0]}__${participants[1]}"

            val chatRef = db.collection("chats").document(chatId)
            val chatSnap = chatRef.get().await()
            if (!chatSnap.exists()) {
                chatRef.set(
                    mapOf(
                        "participantUids" to participants,
                        "createdBy" to uid,
                        "lastSenderId" to uid,
                        "lastMessagePreviewTag" to "Signal session ready",
                        "lastMessageAt" to FieldValue.serverTimestamp(),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            val peerBundle = getPublicBundle(peerUid)
            val myIk = signalManager.getMyIdentityPublicKey()
            val peerIk = peerBundle?.identityPublicKey ?: myIk
            val safetyNumber = SignalProtocolManager.computeSafetyNumber(myIk, peerIk)
            val contactSnap = db.collection("users").document(uid)
                .collection("contacts").document(peerUid).get().await()
            val isVerified = isSelf || (contactSnap.getBoolean("verifiedSafetyNumber") == true)

            Result.success(
                ActiveChatSession(
                    chatId = chatId,
                    peerUid = peerUid,
                    peerName = if (isSelf) "Saved Encrypted Vault" else (peerBundle?.displayName ?: "Kel mi Peer"),
                    peerHandle = peerBundle?.kelMiHandle ?: "vault",
                    peerQqNumber = peerBundle?.qqNumber ?: "",
                    peerAvatarColorHex = peerBundle?.avatarColorHex ?: "#07A96D",
                    peerPreferredLanguage = peerBundle?.preferredLanguage ?: "English",
                    peerAgeYears = (peerBundle?.ageYears ?: 0L).toInt(),
                    peerGender = peerBundle?.gender ?: "Prefer not to say",
                    peerInterests = peerBundle?.interests ?: emptyList(),
                    peerLocationLabel = peerBundle?.locationLabel ?: "",
                    peerTimeZoneId = peerBundle?.timeZoneId ?: "",
                    isSelfVault = isSelf,
                    safetyNumber = safetyNumber,
                    peerIdentityKey = peerIk,
                    isVerified = isVerified
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "chats")
            Result.failure(e)
        }
    }

    fun observeDecryptedChatMessages(userId: String, chatId: String): Flow<List<DecryptedMessageEntity>> {
        return decryptedDao.observeMessagesForChat(userId, chatId)
    }

    fun observeAllDecryptedMessages(userId: String): Flow<List<DecryptedMessageEntity>> {
        return decryptedDao.observeAllForOwner(userId)
    }

    private fun receiptRank(status: String?): Int = when (status?.uppercase()) {
        "SENDING" -> 0
        "SENT" -> 1
        "DELIVERED" -> 2
        "READ" -> 3
        else -> 1
    }

    /**
     * Subscribes to encrypted envelopes in Firestore for [chatId], decrypts each unseen
     * message locally using the Signal Double Ratchet, persists plaintext to the on-device Room vault,
     * syncs receipt updates back to Room, and marks incoming messages READ in Firestore.
     */
    fun observeAndDecryptIncomingMessages(userId: String, chatId: String): Flow<Unit> = flow {
        val uid = requireUserId()
        check(uid == userId)
        val path = "chats/$chatId/messages"
        emitAll(
            db.collection("chats").document(chatId).collection("messages")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snap ->
                    val envelopes = snap.toObjects(
                        EncryptedMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedWith(
                        compareBy<EncryptedMessage>(
                            { it.createdAt ?: Timestamp(0, 0) },
                            { it.messageNumber }
                        )
                    )
                    val unreadForMe = mutableListOf<EncryptedMessage>()

                    for (msg in envelopes) {
                        val compositeKey = "${uid}_${chatId}_${msg.id}"
                        val serverReceipt = msg.receiptStatus.ifBlank { "SENT" }
                        val cached = decryptedDao.getByCompositeKey(compositeKey)

                        if (cached != null) {
                            if (receiptRank(serverReceipt) > receiptRank(cached.receiptStatus)) {
                                decryptedDao.updateReceiptStatus(compositeKey, serverReceipt)
                            }
                            if (msg.recipientId == uid && serverReceipt != "READ") {
                                unreadForMe.add(msg)
                            }
                            continue
                        }

                        // Sender's own message if not in cache (e.g., fresh install): show encrypted notice
                        if (msg.recipientId != uid && msg.senderId == uid) {
                            decryptedDao.insertIfAbsent(
                                DecryptedMessageEntity(
                                    compositeKey = compositeKey,
                                    messageId = msg.id,
                                    chatId = chatId,
                                    ownerUid = uid,
                                    senderId = msg.senderId,
                                    recipientId = msg.recipientId,
                                    plaintext = "[Sent from another session · Forward Secrecy protected]",
                                    messageNumber = msg.messageNumber.toInt(),
                                    preKeyMessage = msg.preKeyMessage,
                                    messageType = msg.messageType,
                                    receiptStatus = serverReceipt,
                                    timestampMillis = msg.createdAt?.toDate()?.time ?: System.currentTimeMillis()
                                )
                            )
                            continue
                        }

                        val decrypted = try {
                            withContext(Dispatchers.Default) {
                                signalManager.decryptMessage(
                                    chatId = chatId,
                                    senderUid = msg.senderId,
                                    recipientUid = msg.recipientId,
                                    envelope = SignalProtocolManager.EncryptedEnvelope(
                                        ciphertextBase64 = msg.ciphertext,
                                        ivBase64 = msg.iv,
                                        senderIdentityKey = msg.senderIdentityKey,
                                        ephemeralKey = msg.ephemeralKey,
                                        ratchetKey = msg.ratchetKey,
                                        usedOpkId = msg.usedOpkId,
                                        messageNumber = msg.messageNumber.toInt(),
                                        previousChainLength = msg.previousChainLength.toInt(),
                                        preKeyMessage = msg.preKeyMessage
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Signal decrypt error for ${msg.id}: ${e.message}")
                            "[Signal Decryption Pending / Key Rotated]"
                        }

                        val isAddressedToMe = msg.recipientId == uid
                        if (isAddressedToMe && serverReceipt != "READ") {
                            unreadForMe.add(msg)
                        }

                        decryptedDao.insertIfAbsent(
                            DecryptedMessageEntity(
                                compositeKey = compositeKey,
                                messageId = msg.id,
                                chatId = chatId,
                                ownerUid = uid,
                                senderId = msg.senderId,
                                recipientId = msg.recipientId,
                                plaintext = decrypted,
                                messageNumber = msg.messageNumber.toInt(),
                                preKeyMessage = msg.preKeyMessage,
                                messageType = msg.messageType,
                                receiptStatus = if (isAddressedToMe) "READ" else serverReceipt,
                                timestampMillis = msg.createdAt?.toDate()?.time ?: System.currentTimeMillis()
                            )
                        )
                    }

                    if (unreadForMe.isNotEmpty()) {
                        markMessagesReadInFirestore(chatId, uid, unreadForMe)
                    }
                }
                .catch { error ->
                    val fsErr = unwrapFirestoreException(error)
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    private suspend fun markMessagesReadInFirestore(
        chatId: String,
        uid: String,
        unreadMessages: List<EncryptedMessage>
    ) {
        val colRef = db.collection("chats").document(chatId).collection("messages")
        unreadMessages.forEach { msg ->
            try {
                val updates = mutableMapOf<String, Any>(
                    "receiptStatus" to "READ",
                    "readAt" to FieldValue.serverTimestamp()
                )
                if (msg.deliveredAt == null) {
                    updates["deliveredAt"] = FieldValue.serverTimestamp()
                }
                colRef.document(msg.id).update(updates).await()
                val compositeKey = "${uid}_${chatId}_${msg.id}"
                decryptedDao.updateReceiptStatus(compositeKey, "READ")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to update READ receipt for ${msg.id}: ${e.message}")
            }
        }
        try {
            val chatRef = db.collection("chats").document(chatId)
            val chatSnap = chatRef.get().await()
            val lastSender = chatSnap.getString("lastSenderId")
            val lastStatus = chatSnap.getString("lastMessageReceiptStatus")
            val isSelfVault = chatId == "${uid}__${uid}"
            if ((isSelfVault || (lastSender != null && lastSender != uid)) && lastStatus != "READ") {
                chatRef.update(
                    mapOf(
                        "lastMessageReceiptStatus" to "READ",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update thread READ status for $chatId: ${e.message}")
        }
    }

    suspend fun markChatMessagesDelivered(chatId: String) {
        val uid = auth.currentUser?.uid ?: return
        try {
            val snap = db.collection("chats").document(chatId).collection("messages")
                .whereArrayContains("memberUids", uid)
                .get()
                .await()
            val toDeliver = snap.toObjects(
                EncryptedMessage::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ).filter { it.recipientId == uid && it.receiptStatus == "SENT" }

            val colRef = db.collection("chats").document(chatId).collection("messages")
            toDeliver.forEach { msg ->
                try {
                    colRef.document(msg.id).update(
                        mapOf(
                            "receiptStatus" to "DELIVERED",
                            "deliveredAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) { }
            }

            val chatRef = db.collection("chats").document(chatId)
            val chatSnap = chatRef.get().await()
            val lastSender = chatSnap.getString("lastSenderId")
            val lastStatus = chatSnap.getString("lastMessageReceiptStatus")
            if (lastSender != null && lastSender != uid && lastStatus == "SENT") {
                chatRef.update(
                    mapOf(
                        "lastMessageReceiptStatus" to "DELIVERED",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "markChatMessagesDelivered failed for $chatId: ${e.message}")
        }
    }

    suspend fun sendEncryptedMessage(
        chatId: String,
        peerUid: String,
        plaintext: String,
        messageType: String = "TEXT"
    ): Result<DecryptedMessageEntity> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        var optimisticKey: String? = null
        return try {
            require(uid == requireUserId())
            val cleanText = plaintext.trim()
            require(cleanText.isNotEmpty()) { "Message cannot be empty" }

            // Pre-fetch peer PreKeyBundle + claim 1 OPK if needed for X3DH
            val peerBundleDoc = db.collection("directory").document(peerUid).get().await()
                .toObject(PublicPreKeyBundle::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            var claimedOpkId: Int? = null
            var claimedOpkPub: String? = null
            var opkDocIdToDelete: String? = null

            val opkQuery = db.collection("directory").document(peerUid)
                .collection("oneTimePreKeys").limit(1).get().await()
            val firstOpk = opkQuery.documents.firstOrNull()
            if (firstOpk != null) {
                claimedOpkId = (firstOpk.getLong("keyId") ?: 0L).toInt().takeIf { it > 0 }
                claimedOpkPub = firstOpk.getString("publicKey")
                opkDocIdToDelete = firstOpk.id
            }

            val envelope = withContext(Dispatchers.Default) {
                signalManager.encryptMessage(
                    chatId = chatId,
                    peerUid = peerUid,
                    plaintext = cleanText
                ) {
                    peerBundleDoc?.let { b ->
                        SignalProtocolManager.PeerPreKeyBundle(
                            userId = b.userId,
                            identityPublicKey = b.identityPublicKey,
                            signedPreKeyId = b.signedPreKeyId.toInt(),
                            signedPreKeyPublic = b.signedPreKeyPublic,
                            signedPreKeySignature = b.signedPreKeySignature,
                            oneTimePreKeyId = claimedOpkId,
                            oneTimePreKeyPublic = claimedOpkPub
                        )
                    }
                }
            }

            if (envelope.preKeyMessage && opkDocIdToDelete != null && envelope.usedOpkId != null) {
                try {
                    db.collection("directory").document(peerUid)
                        .collection("oneTimePreKeys").document(opkDocIdToDelete).delete().await()
                } catch (_: Exception) { }
            }

            val members = if (uid == peerUid) listOf(uid) else listOf(uid, peerUid).sorted()
            val msgRef = db.collection("chats").document(chatId).collection("messages").document()
            val entity = DecryptedMessageEntity(
                compositeKey = "${uid}_${chatId}_${msgRef.id}",
                messageId = msgRef.id,
                chatId = chatId,
                ownerUid = uid,
                senderId = uid,
                recipientId = peerUid,
                plaintext = cleanText,
                messageNumber = envelope.messageNumber,
                preKeyMessage = envelope.preKeyMessage,
                messageType = messageType,
                receiptStatus = "SENDING",
                timestampMillis = System.currentTimeMillis()
            )

            // Cache sender's plaintext locally immediately with SENDING status (loopback uses observeAndDecrypt)
            if (uid != peerUid) {
                optimisticKey = entity.compositeKey
                decryptedDao.insertIfAbsent(entity)
            }

            val payload = mutableMapOf<String, Any?>(
                "senderId" to uid,
                "recipientId" to peerUid,
                "memberUids" to members,
                "ciphertext" to envelope.ciphertextBase64,
                "iv" to envelope.ivBase64,
                "senderIdentityKey" to envelope.senderIdentityKey,
                "ephemeralKey" to envelope.ephemeralKey,
                "ratchetKey" to envelope.ratchetKey,
                "usedOpkId" to envelope.usedOpkId,
                "messageNumber" to envelope.messageNumber.toLong(),
                "previousChainLength" to envelope.previousChainLength.toLong(),
                "preKeyMessage" to envelope.preKeyMessage,
                "messageType" to messageType,
                "receiptStatus" to "SENT",
                "createdAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>

            msgRef.set(payload).await()

            if (uid != peerUid) {
                decryptedDao.updateReceiptStatus(entity.compositeKey, "SENT")
            }

            val previewTag = when {
                messageType == "LOCATION_TIME" -> "Encrypted location & time"
                messageType == "CALL_LOG" -> "Encrypted voice/video call"
                envelope.preKeyMessage -> "Encrypted PreKey message"
                else -> "Encrypted message"
            }

            db.collection("chats").document(chatId).update(
                mapOf(
                    "lastSenderId" to uid,
                    "lastMessagePreviewTag" to previewTag,
                    "lastMessageReceiptStatus" to "SENT",
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(entity.copy(receiptStatus = "SENT"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            optimisticKey?.let { key ->
                try {
                    decryptedDao.updateReceiptStatus(key, "FAILED")
                } catch (_: Exception) { }
            }
            handleFirestoreError(e, OperationType.CREATE, "chats/$chatId/messages")
            Result.failure(e)
        }
    }

    suspend fun saveMessageTranslation(
        compositeKey: String,
        translation: VetnyTranslationResult,
        targetLanguage: String
    ) {
        decryptedDao.updateTranslation(
            compositeKey = compositeKey,
            translatedText = translation.translatedText,
            sourceLang = translation.detectedSourceLanguage,
            targetLang = targetLanguage,
            culturalNote = translation.culturalNote
        )
    }

    suspend fun rotateSignedPreKeyAndSync(): Result<SignalProtocolManager.LocalKeyDiagnostics> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            require(uid == requireUserId())
            val bundle = withContext(Dispatchers.Default) {
                signalManager.rotateSignedPreKey()
            }
            db.collection("directory").document(uid).update(
                mapOf(
                    "signedPreKeyId" to bundle.signedPreKeyId.toLong(),
                    "signedPreKeyPublic" to bundle.signedPreKeyPublic,
                    "signedPreKeySignature" to bundle.signedPreKeySignature,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(signalManager.getDiagnostics())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "directory/$uid")
            Result.failure(e)
        }
    }

    suspend fun replenishOneTimePreKeys(): Result<SignalProtocolManager.LocalKeyDiagnostics> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            require(uid == requireUserId())
            val opks = withContext(Dispatchers.Default) {
                signalManager.replenishOneTimePreKeys(15)
            }
            val opkCol = db.collection("directory").document(uid).collection("oneTimePreKeys")
            val existingIds = opkCol.get().await().documents.map { it.id }.toSet()
            opks.forEach { opk ->
                val docId = "opk_${opk.keyId}"
                if (docId !in existingIds) {
                    opkCol.document(docId).set(
                        mapOf(
                            "keyId" to opk.keyId.toLong(),
                            "publicKey" to opk.publicKey,
                            "ownerUid" to uid,
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            db.collection("directory").document(uid).update(
                mapOf(
                    "opkCount" to opks.size.toLong(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(signalManager.getDiagnostics())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "directory/$uid/oneTimePreKeys")
            Result.failure(e)
        }
    }

    // --- VETNY DIRECT TRANSLATOR HISTORY ---

    fun observeVetnyMessages(userId: String): Flow<List<VetnyMessage>> = flow {
        val uid = requireUserId()
        check(uid == userId)
        val path = "users/$uid/vetnyMessages"
        emitAll(
            db.collection("users").document(uid).collection("vetnyMessages")
                .snapshots()
                .map { snap ->
                    snap.toObjects(VetnyMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val fsErr = unwrapFirestoreException(error)
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveVetnyMessage(
        senderRole: String,
        originalText: String,
        translatedText: String?,
        sourceLanguage: String?,
        targetLanguage: String,
        culturalNote: String?,
        messageType: String = "TEXT",
        agentMode: String? = null,
        modelUsed: String? = null,
        mediaBase64: String? = null,
        groundingJson: String? = null
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            require(uid == requireUserId())
            val ref = db.collection("users").document(uid).collection("vetnyMessages").document()
            val payload = mapOf(
                "userId" to uid,
                "senderRole" to senderRole,
                "originalText" to originalText.take(4000),
                "translatedText" to translatedText?.take(8000),
                "sourceLanguage" to sourceLanguage?.take(40),
                "targetLanguage" to targetLanguage.take(40),
                "culturalNote" to culturalNote?.take(500),
                "messageType" to messageType,
                "agentMode" to agentMode,
                "modelUsed" to modelUsed?.take(64),
                "mediaBase64" to mediaBase64?.take(310000),
                "groundingJson" to groundingJson?.take(19500),
                "createdAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>
            ref.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "users/$uid/vetnyMessages")
            Result.failure(e)
        }
    }

    suspend fun clearVetnyMessages(): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            require(uid == requireUserId())
            val snap = db.collection("users").document(uid).collection("vetnyMessages").get().await()
            snap.documents.forEach { doc ->
                doc.reference.delete().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/$uid/vetnyMessages")
            Result.failure(e)
        }
    }

    // --- MOMENTS FEED ---

    fun observeMoments(userId: String): Flow<List<MomentPost>> = flow {
        val uid = requireUserId()
        check(uid == userId)
        emitAll(
            db.collection("moments")
                .whereArrayContains("visibleToUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(MomentPost::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val fsErr = unwrapFirestoreException(error)
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, "moments")
                    }
                    throw error
                }
        )
    }

    suspend fun postMoment(
        profile: UserProfile,
        contactUids: List<String>,
        content: String,
        languageTag: String,
        locationTag: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            require(uid == requireUserId())
            val cleanContent = content.trim().take(1000)
            require(cleanContent.isNotEmpty())
            val visible = (listOf(uid) + contactUids).distinct().take(50)
            val payload = mapOf(
                "authorId" to uid,
                "authorName" to profile.displayName.take(60),
                "authorHandle" to profile.kelMiHandle,
                "authorAvatarColor" to pickAvatarColor(uid),
                "content" to cleanContent,
                "languageTag" to languageTag.take(40),
                "locationTag" to locationTag.take(100),
                "visibleToUids" to visible,
                "likedByUids" to emptyList<String>(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("moments").document().set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "moments")
            Result.failure(e)
        }
    }

    suspend fun toggleMomentLike(moment: MomentPost): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            require(uid == requireUserId())
            val updatedLikes = if (uid in moment.likedByUids) {
                moment.likedByUids - uid
            } else {
                (moment.likedByUids + uid).distinct().take(50)
            }
            db.collection("moments").document(moment.id).update(
                mapOf(
                    "likedByUids" to updatedLikes,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "moments/${moment.id}")
            Result.failure(e)
        }
    }

    private fun unwrapFirestoreException(error: Throwable): FirebaseFirestoreException? {
        return generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
    }

    companion object {
        private const val TAG = "KelMiRepository"
        private val AVATAR_COLORS = listOf(
            "#07A96D", "#0D9488", "#0284C7", "#4F46E5", "#7C3AED", "#DB2777", "#D97706"
        )

        fun pickAvatarColor(seed: String): String {
            val idx = abs(seed.hashCode()) % AVATAR_COLORS.size
            return AVATAR_COLORS[idx]
        }
    }
}
