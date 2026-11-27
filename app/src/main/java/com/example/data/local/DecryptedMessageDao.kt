package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DecryptedMessageDao {
    @Query("SELECT * FROM decrypted_messages WHERE ownerUid = :ownerUid AND chatId = :chatId ORDER BY timestampMillis ASC, messageNumber ASC")
    fun observeMessagesForChat(ownerUid: String, chatId: String): Flow<List<DecryptedMessageEntity>>

    @Query("SELECT * FROM decrypted_messages WHERE ownerUid = :ownerUid")
    fun observeAllForOwner(ownerUid: String): Flow<List<DecryptedMessageEntity>>

    @Query("SELECT * FROM decrypted_messages WHERE compositeKey = :compositeKey LIMIT 1")
    suspend fun getByCompositeKey(compositeKey: String): DecryptedMessageEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entity: DecryptedMessageEntity)

    @Query(
        "UPDATE decrypted_messages SET translatedText = :translatedText, detectedSourceLanguage = :sourceLang, targetLanguage = :targetLang, culturalNote = :culturalNote WHERE compositeKey = :compositeKey"
    )
    suspend fun updateTranslation(
        compositeKey: String,
        translatedText: String,
        sourceLang: String,
        targetLang: String,
        culturalNote: String?
    )

    @Query("UPDATE decrypted_messages SET receiptStatus = :receiptStatus WHERE compositeKey = :compositeKey")
    suspend fun updateReceiptStatus(compositeKey: String, receiptStatus: String)

    @Query("DELETE FROM decrypted_messages WHERE ownerUid = :ownerUid AND chatId = :chatId")
    suspend fun deleteChatHistory(ownerUid: String, chatId: String)
}
