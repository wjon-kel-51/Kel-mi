package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val displayName: String = "",
    val kelMiHandle: String = "",
    val qqNumber: String = "",
    val gender: String = "Prefer not to say",
    val dateOfBirth: String = "",
    val ageYears: Long = 0L,
    val interests: List<String> = emptyList(),
    val locationLabel: String = "",
    val timeZoneId: String = "",
    val preferredLanguage: String = "English",
    val autoTranslateEnabled: Boolean = true,
    val profileCompleted: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class UserContact(
    @DocumentId val id: String = "",
    val contactUid: String = "",
    val kelMiHandle: String = "",
    val qqNumber: String = "",
    val displayName: String = "",
    val avatarColorHex: String = "#07A96D",
    val preferredLanguage: String = "English",
    val gender: String = "Prefer not to say",
    val ageYears: Long = 0L,
    val interests: List<String> = emptyList(),
    val locationLabel: String = "",
    val timeZoneId: String = "",
    val verifiedSafetyNumber: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class VetnyMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val senderRole: String = "USER",
    val originalText: String = "",
    val translatedText: String? = null,
    val sourceLanguage: String? = null,
    val targetLanguage: String = "Spanish",
    val culturalNote: String? = null,
    val messageType: String = "TEXT",
    val agentMode: String? = null,
    val modelUsed: String? = null,
    val mediaBase64: String? = null,
    val groundingJson: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class HandleIndex(
    @DocumentId val id: String = "",
    val userId: String = "",
    val kelMiHandle: String = "",
    val qqNumber: String = "",
    val displayName: String = "",
    val avatarColorHex: String = "#07A96D",
    val preferredLanguage: String = "English",
    val gender: String = "Prefer not to say",
    val ageYears: Long = 0L,
    val interests: List<String> = emptyList(),
    val locationLabel: String = "",
    val timeZoneId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PublicPreKeyBundle(
    @DocumentId val id: String = "",
    val userId: String = "",
    val kelMiHandle: String = "",
    val qqNumber: String = "",
    val displayName: String = "",
    val avatarColorHex: String = "#07A96D",
    val preferredLanguage: String = "English",
    val gender: String = "Prefer not to say",
    val ageYears: Long = 0L,
    val interests: List<String> = emptyList(),
    val locationLabel: String = "",
    val timeZoneId: String = "",
    val identityPublicKey: String = "",
    val signedPreKeyId: Long = 1L,
    val signedPreKeyPublic: String = "",
    val signedPreKeySignature: String = "",
    val safetyFingerprint: String = "",
    val opkCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class OneTimePreKey(
    @DocumentId val id: String = "",
    val keyId: Long = 1L,
    val publicKey: String = "",
    val ownerUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatThread(
    @DocumentId val id: String = "",
    val participantUids: List<String> = emptyList(),
    val createdBy: String = "",
    val lastSenderId: String? = null,
    val lastMessagePreviewTag: String? = null,
    val lastMessageReceiptStatus: String? = null,
    @ServerTimestamp val lastMessageAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class EncryptedMessage(
    @DocumentId val id: String = "",
    val senderId: String = "",
    val recipientId: String = "",
    val memberUids: List<String> = emptyList(),
    val ciphertext: String = "",
    val iv: String = "",
    val senderIdentityKey: String = "",
    val ephemeralKey: String? = null,
    val ratchetKey: String = "",
    val usedOpkId: String? = null,
    val messageNumber: Long = 0L,
    val previousChainLength: Long = 0L,
    val preKeyMessage: Boolean = false,
    val messageType: String = "TEXT",
    val receiptStatus: String = "SENT",
    @ServerTimestamp val deliveredAt: Timestamp? = null,
    @ServerTimestamp val readAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class MomentPost(
    @DocumentId val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorHandle: String = "",
    val authorAvatarColor: String = "#07A96D",
    val content: String = "",
    val languageTag: String? = null,
    val locationTag: String? = null,
    val visibleToUids: List<String> = emptyList(),
    val likedByUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CallSession(
    @DocumentId val id: String = "",
    val callerId: String = "",
    val calleeId: String = "",
    val memberUids: List<String> = emptyList(),
    val callType: String = "VOICE",
    val status: String = "RINGING",
    val e2eeFingerprint: String = "",
    val durationSeconds: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ReelPost(
    @DocumentId val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorHandle: String = "",
    val authorAvatarColor: String = "#07A96D",
    val caption: String = "",
    val mediaType: String = "IMAGE",
    val mediaBase64: String? = null,
    val localMediaUri: String? = null,
    val durationSeconds: Long = 0L,
    val songId: String? = null,
    val songTitle: String? = null,
    val songArtist: String? = null,
    val languageTag: String? = null,
    val locationTag: String? = null,
    val filterName: String? = null,
    val visibility: String = "PUBLIC",
    val visibleToUids: List<String> = emptyList(),
    val likedByUids: List<String> = emptyList(),
    val bookmarkedByUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ReelComment(
    @DocumentId val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorHandle: String = "",
    val authorAvatarColor: String = "#07A96D",
    val text: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class StoryPost(
    @DocumentId val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorHandle: String = "",
    val authorAvatarColor: String = "#07A96D",
    val caption: String = "",
    val mediaBase64: String? = null,
    val songTitle: String? = null,
    val locationTag: String? = null,
    val visibility: String = "PUBLIC",
    val visibleToUids: List<String> = emptyList(),
    val viewedByUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class OnlineUser(
    @DocumentId val id: String = "",
    val userId: String = "",
    val kelMiHandle: String = "",
    val qqNumber: String = "",
    val displayName: String = "",
    val avatarColorHex: String = "#07A96D",
    val preferredLanguage: String = "English",
    val gender: String = "Prefer not to say",
    val ageYears: Long = 0L,
    val interests: List<String> = emptyList(),
    val locationLabel: String = "",
    val timeZoneId: String = "",
    val onlineStatus: String = "ONLINE",
    val discoverable: Boolean = true,
    @ServerTimestamp val lastActiveAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

