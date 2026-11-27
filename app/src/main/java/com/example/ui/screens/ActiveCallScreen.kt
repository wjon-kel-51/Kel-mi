package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.data.model.ActiveCallUiState
import com.example.data.model.VetnyTranslationResult
import com.example.ui.components.KelMiAvatar
import com.example.util.DevicePermissionsHelper
import com.example.util.ScreenshotHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

@SuppressLint("MissingPermission")
@Composable
fun ActiveCallScreen(
    callState: ActiveCallUiState,
    myPreferredLanguage: String,
    callTranslation: VetnyTranslationResult?,
    onEndCall: (durationSeconds: Long) -> Unit,
    onTranslatePhrase: (String, String) -> Unit,
    onClearTranslation: () -> Unit,
    onShowBanner: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var elapsedSeconds by remember { mutableLongStateOf(0L) }
    var micMuted by remember { mutableStateOf(false) }
    var speakerOn by remember { mutableStateOf(callState.callType == "VIDEO") }
    var videoEnabled by remember { mutableStateOf(callState.callType == "VIDEO") }
    var useFrontCamera by remember { mutableStateOf(true) }
    var micLevel by remember { mutableFloatStateOf(0.15f) }
    var showVetnyLive by remember { mutableStateOf(false) }
    var captionPhrase by remember { mutableStateOf("") }

    BackHandler { onEndCall(elapsedSeconds) }

    // Live Call Timer
    LaunchedEffect(callState.callId) {
        while (isActive) {
            delay(1000L)
            elapsedSeconds++
        }
    }

    // AudioManager routing for Voice / Video communication
    DisposableEffect(speakerOn) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        try {
            audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
            @Suppress("DEPRECATION")
            audioManager?.isSpeakerphoneOn = speakerOn
        } catch (_: Exception) { }
        onDispose {
            try {
                audioManager?.mode = AudioManager.MODE_NORMAL
                @Suppress("DEPRECATION")
                audioManager?.isSpeakerphoneOn = false
            } catch (_: Exception) { }
        }
    }

    // Real AudioRecord PCM metering when RECORD_AUDIO is granted and mic is unmuted
    LaunchedEffect(micMuted) {
        if (micMuted || !DevicePermissionsHelper.hasMicrophonePermission(context)) {
            micLevel = 0.05f
            return@LaunchedEffect
        }
        launch(Dispatchers.IO) {
            val sampleRate = 16000
            val bufSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(1024)
            var recorder: AudioRecord? = null
            try {
                recorder = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufSize
                )
                if (recorder.state == AudioRecord.STATE_INITIALIZED) {
                    recorder.startRecording()
                    val buffer = ShortArray(bufSize / 2)
                    while (isActive && !micMuted) {
                        val read = recorder.read(buffer, 0, buffer.size)
                        if (read > 0) {
                            var peak = 0
                            for (i in 0 until read step 4) {
                                val v = abs(buffer[i].toInt())
                                if (v > peak) peak = v
                            }
                            micLevel = (peak / 12000f).coerceIn(0.12f, 1.0f)
                        }
                        delay(80L)
                    }
                }
            } catch (_: Throwable) {
                // Keep quiet fallback on emulator without physical audio hardware
            } finally {
                try {
                    recorder?.stop()
                } catch (_: Exception) { }
                try {
                    recorder?.release()
                } catch (_: Exception) { }
            }
        }
    }

    val animatedLevel by animateFloatAsState(targetValue = micLevel, label = "mic_level")
    val durationLabel = remember(elapsedSeconds) {
        String.format("%02d:%02d", elapsedSeconds / 60, elapsedSeconds % 60)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF08110E))
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // Real CameraX Video Stream when videoEnabled & CAMERA permission granted
        if (videoEnabled && DevicePermissionsHelper.hasCameraPermission(context)) {
            AndroidView(
                factory = { ctx -> PreviewView(ctx) },
                update = { previewView ->
                    val providerFuture = ProcessCameraProvider.getInstance(context)
                    providerFuture.addListener({
                        try {
                            val provider = providerFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }
                            val selector = if (useFrontCamera) {
                                CameraSelector.DEFAULT_FRONT_CAMERA
                            } else {
                                CameraSelector.DEFAULT_BACK_CAMERA
                            }
                            provider.unbindAll()
                            provider.bindToLifecycle(lifecycleOwner, selector, preview)
                        } catch (_: Exception) { }
                    }, ContextCompat.getMainExecutor(context))
                },
                modifier = Modifier.fillMaxSize()
            )
            // Subtle dark scrim for legibility
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.28f))
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top E2EE & Screenshot Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.14f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Signal E2EE · ${callState.e2eeFingerprint.take(8)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = { showVetnyLive = !showVetnyLive },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.14f))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Translate,
                            contentDescription = "Vetny Call Translator",
                            tint = Color.White
                        )
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                ScreenshotHelper.captureAndShareScreenshot(context)
                                    .onSuccess { onShowBanner("Call screenshot captured") }
                                    .onFailure { onShowBanner(it.localizedMessage ?: "Screenshot failed") }
                            }
                        },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.14f))
                            .testTag("call_screenshot_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PhotoCamera,
                            contentDescription = "Capture call screenshot",
                            tint = Color.White
                        )
                    }
                }
            }

            // Center Peer Identity & Audio Waveform
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!videoEnabled || !DevicePermissionsHelper.hasCameraPermission(context)) {
                    KelMiAvatar(
                        name = callState.peerName,
                        colorHex = callState.peerAvatarColorHex,
                        size = 104.dp
                    )
                }
                Text(
                    text = callState.peerName,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "${if (callState.callType == "VIDEO") "Video Call" else "Voice Call"} · $durationLabel",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFD1FAE5)
                )

                // Live Audio Waveform Visualizer driven by real AudioRecord amplitude
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.height(38.dp)
                ) {
                    val weights = listOf(0.45f, 0.75f, 1.0f, 0.8f, 0.55f)
                    weights.forEach { w ->
                        val h = (10f + 26f * animatedLevel * w).dp
                        Box(
                            modifier = Modifier
                                .width(6.dp)
                                .height(h)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    if (micMuted) Color.Gray else Color(0xFF34D399)
                                )
                        )
                    }
                }

                if (showVetnyLive) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Black.copy(alpha = 0.72f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.AutoAwesome,
                                        contentDescription = null,
                                        tint = Color(0xFFA5B4FC),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Vetny Call Subtitles ($myPreferredLanguage -> ${callState.peerPreferredLanguage})",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White
                                    )
                                }
                                TextButton(onClick = onClearTranslation) {
                                    Text("Clear", color = Color(0xFFA5B4FC))
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = captionPhrase,
                                    onValueChange = { captionPhrase = it },
                                    placeholder = { Text("Type phrase to translate live...", color = Color.LightGray) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = Color(0xFF34D399),
                                        unfocusedBorderColor = Color.Gray
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                IconButton(
                                    onClick = {
                                        onTranslatePhrase(captionPhrase, callState.peerPreferredLanguage)
                                    }
                                ) {
                                    Icon(Icons.Rounded.Translate, contentDescription = "Translate", tint = Color(0xFFA5B4FC))
                                }
                            }
                            if (callTranslation != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = callTranslation.translatedText,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color(0xFF6EE7B7),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Call Controls
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = Color.White.copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { micMuted = !micMuted },
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(if (micMuted) Color.White else Color.White.copy(alpha = 0.18f))
                            .testTag("call_mute_button")
                    ) {
                        Icon(
                            imageVector = if (micMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                            contentDescription = if (micMuted) "Unmute microphone" else "Mute microphone",
                            tint = if (micMuted) Color.Black else Color.White
                        )
                    }

                    IconButton(
                        onClick = { speakerOn = !speakerOn },
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(if (speakerOn) Color.White else Color.White.copy(alpha = 0.18f))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.VolumeUp,
                            contentDescription = "Toggle speakerphone",
                            tint = if (speakerOn) Color.Black else Color.White
                        )
                    }

                    IconButton(
                        onClick = { videoEnabled = !videoEnabled },
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(if (videoEnabled) Color.White else Color.White.copy(alpha = 0.18f))
                    ) {
                        Icon(
                            imageVector = if (videoEnabled) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff,
                            contentDescription = "Toggle video",
                            tint = if (videoEnabled) Color.Black else Color.White
                        )
                    }

                    if (videoEnabled) {
                        IconButton(
                            onClick = { useFrontCamera = !useFrontCamera },
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.18f))
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Cameraswitch,
                                contentDescription = "Switch camera",
                                tint = Color.White
                            )
                        }
                    }

                    FilledIconButton(
                        onClick = { onEndCall(elapsedSeconds) },
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFEF4444)),
                        modifier = Modifier
                            .size(56.dp)
                            .testTag("end_call_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CallEnd,
                            contentDescription = "End call",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}
