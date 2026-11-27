package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.util.QrCodeHelper
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(
    onBack: () -> Unit,
    onShowMyQr: () -> Unit = {},
    onQrContactResolved: (payloadOrHandle: String) -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var manualQrInput by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var detectedContact by remember { mutableStateOf<QrCodeHelper.ParsedKelMiQr?>(null) }
    var torchEnabled by remember { mutableStateOf(false) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }

    val cameraPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            statusMessage = "Camera permission denied. Pick a QR image from Photos or paste a Kel mi QR link below."
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val bitmap = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
                        .copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
            } catch (_: Exception) {
                null
            }
            val decoded = bitmap?.let { QrCodeHelper.decodeQrFromBitmap(it) }
            val parsed = decoded?.let { QrCodeHelper.parseContactQrPayload(it) }
            if (parsed != null) {
                statusMessage = null
                detectedContact = parsed
            } else {
                statusMessage = "Could not detect a Kel mi QR code in that image."
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("qr_scanner_back_button")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Scan Kel mi QR Code") },
                actions = {
                    TextButton(
                        onClick = {
                            onBack()
                            onShowMyQr()
                        },
                        modifier = Modifier.testTag("scanner_my_qr_button")
                    ) {
                        Icon(Icons.Rounded.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("My QR")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (hasCameraPermission) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(310.dp)
                        .clip(RoundedCornerShape(22.dp))
                ) {
                    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
                    DisposableEffect(Unit) {
                        onDispose { cameraExecutor.shutdown() }
                    }
                    AndroidView(
                        factory = { ctx ->
                            val previewView = PreviewView(ctx)
                            val providerFuture = ProcessCameraProvider.getInstance(ctx)
                            providerFuture.addListener({
                                val cameraProvider = providerFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.surfaceProvider = previewView.surfaceProvider
                                }
                                val reader = MultiFormatReader().apply {
                                    setHints(
                                        mapOf(
                                            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)
                                        )
                                    )
                                }
                                val analysis = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()
                                    .also { imageAnalysis ->
                                        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                            try {
                                                if (detectedContact == null) {
                                                    val buffer = imageProxy.planes[0].buffer
                                                    val bytes = ByteArray(buffer.remaining())
                                                    buffer.get(bytes)
                                                    val source = PlanarYUVLuminanceSource(
                                                        bytes,
                                                        imageProxy.width,
                                                        imageProxy.height,
                                                        0,
                                                        0,
                                                        imageProxy.width,
                                                        imageProxy.height,
                                                        false
                                                    )
                                                    val result = reader.decodeWithState(
                                                        BinaryBitmap(HybridBinarizer(source))
                                                    )
                                                    val parsed = QrCodeHelper.parseContactQrPayload(result.text)
                                                    if (parsed != null) {
                                                        previewView.post {
                                                            statusMessage = null
                                                            detectedContact = parsed
                                                        }
                                                    }
                                                }
                                            } catch (_: Exception) {
                                                // Frame had no QR code
                                            } finally {
                                                reader.reset()
                                                imageProxy.close()
                                            }
                                        }
                                    }
                                try {
                                    cameraProvider.unbindAll()
                                    boundCamera = cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_BACK_CAMERA,
                                        preview,
                                        analysis
                                    )
                                } catch (_: Exception) { }
                            }, ContextCompat.getMainExecutor(ctx))
                            previewView
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Viewfinder reticle overlay
                    val frameColor = MaterialTheme.colorScheme.primary
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val boxSize = minOf(size.width, size.height) * 0.62f
                        val left = (size.width - boxSize) / 2f
                        val top = (size.height - boxSize) / 2f
                        val right = left + boxSize
                        val bottom = top + boxSize
                        val cornerLen = 34.dp.toPx()
                        val stroke = 4.dp.toPx()

                        // Top-left
                        drawLine(frameColor, Offset(left, top), Offset(left + cornerLen, top), stroke, StrokeCap.Round)
                        drawLine(frameColor, Offset(left, top), Offset(left, top + cornerLen), stroke, StrokeCap.Round)
                        // Top-right
                        drawLine(frameColor, Offset(right, top), Offset(right - cornerLen, top), stroke, StrokeCap.Round)
                        drawLine(frameColor, Offset(right, top), Offset(right, top + cornerLen), stroke, StrokeCap.Round)
                        // Bottom-left
                        drawLine(frameColor, Offset(left, bottom), Offset(left + cornerLen, bottom), stroke, StrokeCap.Round)
                        drawLine(frameColor, Offset(left, bottom), Offset(left, bottom - cornerLen), stroke, StrokeCap.Round)
                        // Bottom-right
                        drawLine(frameColor, Offset(right, bottom), Offset(right - cornerLen, bottom), stroke, StrokeCap.Round)
                        drawLine(frameColor, Offset(right, bottom), Offset(right, bottom - cornerLen), stroke, StrokeCap.Round)
                    }

                    // Torch toggle
                    IconButton(
                        onClick = {
                            val next = !torchEnabled
                            torchEnabled = next
                            boundCamera?.cameraControl?.enableTorch(next)
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .testTag("qr_torch_button")
                    ) {
                        Icon(
                            imageVector = if (torchEnabled) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                            contentDescription = "Toggle flashlight",
                            tint = Color.White
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Black.copy(alpha = 0.55f),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp)
                    ) {
                        Text(
                            text = "Align a friend's Kel mi QR code inside the frame",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            } else {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.QrCodeScanner,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Enable Camera to scan a friend's Kel mi QR Card in real time.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { cameraPermLauncher.launch(Manifest.permission.CAMERA) },
                            modifier = Modifier.testTag("enable_qr_camera_button")
                        ) {
                            Icon(Icons.Rounded.CameraAlt, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Enable Camera Scanner")
                        }
                    }
                }
            }

            // Decoded QR Confirmation Card
            detectedContact?.let { parsed ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Kel mi QR Code Detected",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        val title = parsed.displayName ?: "@${parsed.handle.ifBlank { "user" }}"
                        val subtitle = buildList {
                            if (parsed.handle.isNotBlank()) add("@${parsed.handle}")
                            if (!parsed.qqNumber.isNullOrBlank()) add("ID ${parsed.qqNumber}")
                        }.joinToString(" · ")
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (subtitle.isNotBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    onQrContactResolved(parsed.rawPayload.ifBlank { parsed.handle })
                                    onBack()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("qr_confirm_add_button")
                            ) {
                                Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add & Chat")
                            }
                            OutlinedButton(
                                onClick = { detectedContact = null },
                                modifier = Modifier.testTag("qr_rescan_button")
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Rescan")
                            }
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("qr_pick_from_gallery_button")
            ) {
                Icon(Icons.Rounded.PhotoLibrary, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Scan QR from Photo Gallery")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = manualQrInput,
                    onValueChange = { manualQrInput = it },
                    label = { Text("Or paste kelmi:// QR link or @handle") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("manual_qr_input")
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val parsed = QrCodeHelper.parseContactQrPayload(manualQrInput)
                        if (parsed != null) {
                            statusMessage = null
                            onQrContactResolved(parsed.rawPayload.ifBlank { parsed.handle })
                            onBack()
                        } else {
                            statusMessage = "Invalid Kel mi QR link or handle."
                        }
                    },
                    modifier = Modifier.testTag("manual_qr_add_button")
                ) {
                    Text("Add")
                }
            }

            if (statusMessage != null) {
                Text(
                    text = statusMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
