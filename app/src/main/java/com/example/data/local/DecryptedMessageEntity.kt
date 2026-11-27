package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "decrypted_messages")
data class DecryptedMessageEntity(
    @PrimaryKey val compositeKey: String,
    val messageId: String,
    val chatId: String,
    val ownerUid: String,
    val senderId: String,
    val recipientId: String,
    val plaintext: String,
    val translatedText: String? = null,
    val detectedSourceLanguage: String? = null,
    val targetLanguage: String? = null,
    val culturalNote: String? = null,
    val messageNumber: Int = 0,
    val preKeyMessage: Boolean = false,
    val messageType: String = "TEXT",
    val receiptStatus: String = "SENT",
    val timestampMillis: Long = System.currentTimeMillis()
)
