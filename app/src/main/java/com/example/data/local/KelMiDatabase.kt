package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DecryptedMessageEntity::class],
    version = 2,
    exportSchema = false
)
abstract class KelMiDatabase : RoomDatabase() {
    abstract fun decryptedMessageDao(): DecryptedMessageDao

    companion object {
        @Volatile
        private var INSTANCE: KelMiDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE decrypted_messages ADD COLUMN receiptStatus TEXT NOT NULL DEFAULT 'SENT'"
                )
            }
        }

        fun getInstance(context: Context): KelMiDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    KelMiDatabase::class.java,
                    "kelmi_decrypted_vault.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
