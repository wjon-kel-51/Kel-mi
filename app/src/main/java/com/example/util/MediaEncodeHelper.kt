package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

object MediaEncodeHelper {

    data class EncodedMediaPreview(
        val mediaType: String, // "IMAGE" or "VIDEO"
        val base64Preview: String,
        val durationSeconds: Int
    )

    suspend fun processPickedMedia(
        context: Context,
        uri: Uri
    ): EncodedMediaPreview? = withContext(Dispatchers.IO) {
        val mime = context.contentResolver.getType(uri).orEmpty().lowercase()
        val isVideo = mime.startsWith("video") || uri.toString().lowercase().contains("video")

        if (isVideo) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
                val durationSec = (durationMs / 1000L).toInt().coerceIn(1, 600)
                val frame = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                val encoded = frame?.let { compressBitmapToBase64(it, maxDim = 540, quality = 70) }.orEmpty()
                EncodedMediaPreview(
                    mediaType = "VIDEO",
                    base64Preview = encoded,
                    durationSeconds = durationSec
                )
            } catch (_: Exception) {
                EncodedMediaPreview(mediaType = "VIDEO", base64Preview = "", durationSeconds = 10)
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) { }
            }
        } else {
            try {
                val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                } ?: return@withContext null
                val encoded = compressBitmapToBase64(decoded, maxDim = 640, quality = 72)
                EncodedMediaPreview(
                    mediaType = "IMAGE",
                    base64Preview = encoded,
                    durationSeconds = 0
                )
            } catch (_: Exception) {
                null
            }
        }
    }

    fun compressBitmapToBase64(bitmap: Bitmap, maxDim: Int = 640, quality: Int = 72): String {
        val ratio = minOf(
            maxDim.toFloat() / bitmap.width.coerceAtLeast(1),
            maxDim.toFloat() / bitmap.height.coerceAtLeast(1),
            1f
        )
        val targetW = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val targetH = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        val scaled = if (ratio < 1f) {
            Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        } else {
            bitmap
        }
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        val bytes = out.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun decodeBase64Bitmap(base64: String?): Bitmap? {
        if (base64.isNullOrBlank()) return null
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }
}
