package com.example.util

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.PixelCopy
import androidx.core.content.FileProvider
import com.example.util.DevicePermissionsHelper.findActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume

object ScreenshotHelper {

    data class ScreenshotResult(
        val savedToGallery: Boolean,
        val fileName: String
    )

    suspend fun captureAndShareScreenshot(context: Context): Result<ScreenshotResult> {
        val activity = context.findActivity()
            ?: return Result.failure(IllegalStateException("Unable to locate active screen window."))
        DevicePermissionsHelper.ensureScreenshotsAllowed(activity)

        val bitmap = captureWindowBitmap(activity)
            ?: return Result.failure(IllegalStateException("Could not capture screen image."))

        return shareBitmapInternal(
            context = context,
            bitmap = bitmap,
            filePrefix = "kelmi_screenshot",
            shareTitle = "Save or Share Kel mi Screenshot",
            shareText = "Captured in Kel mi"
        )
    }

    suspend fun saveBitmapToGallery(
        context: Context,
        bitmap: Bitmap,
        filePrefix: String = "kelmi_vetny_art"
    ): Result<ScreenshotResult> = withContext(Dispatchers.IO) {
        try {
            val timestamp = System.currentTimeMillis()
            val fileName = "${filePrefix}_$timestamp.png"
            var savedToGallery = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/KelMi")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val galleryUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (galleryUri != null) {
                    resolver.openOutputStream(galleryUri)?.use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(galleryUri, values, null, null)
                    savedToGallery = true
                }
            } else {
                val dir = File(context.cacheDir, "screenshots").apply { mkdirs() }
                FileOutputStream(File(dir, fileName)).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            }
            Result.success(ScreenshotResult(savedToGallery = savedToGallery, fileName = fileName))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun shareBitmap(
        context: Context,
        bitmap: Bitmap,
        shareText: String = "Created with Vetny AI in Kel mi"
    ): Result<ScreenshotResult> = shareBitmapInternal(
        context = context,
        bitmap = bitmap,
        filePrefix = "kelmi_vetny_art",
        shareTitle = "Share Vetny AI Photo",
        shareText = shareText
    )

    private suspend fun shareBitmapInternal(
        context: Context,
        bitmap: Bitmap,
        filePrefix: String,
        shareTitle: String,
        shareText: String
    ): Result<ScreenshotResult> {
        return withContext(Dispatchers.IO) {
            try {
                val timestamp = System.currentTimeMillis()
                val fileName = "${filePrefix}_$timestamp.png"
                var savedToGallery = false

                // Save to MediaStore Pictures/KelMi on Android 10+ (zero storage permission required)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/KelMi")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                    val resolver = context.contentResolver
                    val galleryUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    if (galleryUri != null) {
                        resolver.openOutputStream(galleryUri)?.use { out ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                        }
                        values.clear()
                        values.put(MediaStore.Images.Media.IS_PENDING, 0)
                        resolver.update(galleryUri, values, null, null)
                        savedToGallery = true
                    }
                }

                // Also write to cacheDir/screenshots and open ShareSheet via FileProvider
                val dir = File(context.cacheDir, "screenshots").apply { mkdirs() }
                val file = File(dir, fileName)
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }

                val shareUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                withContext(Dispatchers.Main) {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, shareUri)
                        putExtra(Intent.EXTRA_TEXT, shareText)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val chooser = Intent.createChooser(shareIntent, shareTitle).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(chooser)
                }

                Result.success(ScreenshotResult(savedToGallery = savedToGallery, fileName = fileName))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private suspend fun captureWindowBitmap(activity: Activity): Bitmap? {
        val decorView = activity.window?.decorView ?: return null
        val width = decorView.width
        val height = decorView.height
        if (width <= 0 || height <= 0) return null

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return suspendCancellableCoroutine { cont ->
                try {
                    PixelCopy.request(
                        activity.window,
                        bitmap,
                        { copyResult ->
                            if (copyResult == PixelCopy.SUCCESS) {
                                cont.resume(bitmap)
                            } else {
                                drawFallback(decorView, bitmap)
                                cont.resume(bitmap)
                            }
                        },
                        Handler(Looper.getMainLooper())
                    )
                } catch (_: Exception) {
                    drawFallback(decorView, bitmap)
                    cont.resume(bitmap)
                }
            }
        } else {
            return withContext(Dispatchers.Main) {
                drawFallback(decorView, bitmap)
                bitmap
            }
        }
    }

    private fun drawFallback(view: android.view.View, bitmap: Bitmap) {
        val canvas = Canvas(bitmap)
        view.draw(canvas)
    }
}
