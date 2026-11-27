package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.BuildConfig
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.File
import java.io.FileOutputStream

object QrCodeHelper {

    data class ParsedKelMiQr(
        val handle: String,
        val uid: String? = null,
        val qqNumber: String? = null,
        val displayName: String? = null,
        val rawPayload: String = ""
    )

    fun buildContactQrUri(
        uid: String,
        handle: String,
        qqNumber: String,
        displayName: String
    ): String {
        val encodedName = Uri.encode(displayName)
        return "kelmi://contact?uid=$uid&handle=$handle&qq=$qqNumber&name=$encodedName"
    }

    fun parseContactQrPayload(raw: String): ParsedKelMiQr? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null

        if (trimmed.startsWith("kelmi://", ignoreCase = true)) {
            val uri = Uri.parse(trimmed)
            val handle = uri.getQueryParameter("handle")?.trim()?.lowercase().orEmpty()
            val uid = uri.getQueryParameter("uid")?.trim()?.takeIf { it.isNotBlank() }
            val qq = uri.getQueryParameter("qq")?.trim()?.takeIf { it.isNotBlank() }
            val name = uri.getQueryParameter("name")?.trim()?.takeIf { it.isNotBlank() }
            if (handle.isNotBlank() || !uid.isNullOrBlank()) {
                return ParsedKelMiQr(
                    handle = handle,
                    uid = uid,
                    qqNumber = qq,
                    displayName = name,
                    rawPayload = trimmed
                )
            }
        }

        // Allow direct @handle or handle fallback
        val normalized = trimmed.lowercase().removePrefix("@")
        if (normalized.matches(Regex("^[a-z0-9_]{3,32}$"))) {
            return ParsedKelMiQr(
                handle = normalized,
                rawPayload = trimmed
            )
        }
        return null
    }

    fun generateQrBitmap(content: String, sizePx: Int = 640): Bitmap {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.MARGIN to 1
        )
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val pixels = IntArray(width * height)
        val fgColor = Color.parseColor("#064E3B")
        val bgColor = Color.WHITE
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (bitMatrix.get(x, y)) fgColor else bgColor
            }
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }

    fun decodeQrFromBitmap(bitmap: Bitmap): String? {
        return try {
            val maxDim = 1200
            val scale = minOf(
                maxDim.toFloat() / bitmap.width.coerceAtLeast(1),
                maxDim.toFloat() / bitmap.height.coerceAtLeast(1),
                1f
            )
            val target = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt().coerceAtLeast(1),
                    (bitmap.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                bitmap
            }
            val width = target.width
            val height = target.height
            val pixels = IntArray(width * height)
            target.getPixels(pixels, 0, width, 0, 0, width, height)
            val source = RGBLuminanceSource(width, height, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val reader = MultiFormatReader().apply {
                setHints(
                    mapOf(
                        DecodeHintType.TRY_HARDER to true,
                        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)
                    )
                )
            }
            reader.decode(binaryBitmap).text
        } catch (_: Exception) {
            null
        }
    }

    fun shareQrCard(context: Context, qrBitmap: Bitmap, displayName: String, handle: String, qqNumber: String) {
        try {
            val cacheDir = File(context.cacheDir, "qr_cards").apply { mkdirs() }
            val imageFile = File(cacheDir, "kelmi_qr_${handle}.png")
            FileOutputStream(imageFile).use { out ->
                qrBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${BuildConfig.APPLICATION_ID}.fileprovider",
                imageFile
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Add $displayName on Kel mi! Handle: @$handle · Kel mi ID: $qqNumber\n${buildContactQrUri("", handle, qqNumber, displayName)}"
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Kel mi QR Card"))
        } catch (_: Exception) {
            // Ignore share sheet cancellation or unavailable handler
        }
    }
}
