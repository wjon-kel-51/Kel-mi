package com.example.ui.components

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.VetnyLiveSessionState
import com.example.util.DevicePermissionsHelper

private val liveVoiceNames = listOf("AOEDE", "KORE", "PUCK", "FENRIR", "CHARON")

@Composable
fun VetnyLiveVoicePanel(
    liveState: VetnyLiveSessionState,
    targetLanguage: String,
    onStartLiveVoice: (interpreterMode: Boolean, voiceName: String) -> Unit,
    onStopLiveVoice: () -> Unit,
    onShowBanner: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var interpreterMode by remember(liveState.interpreterMode) { mutableStateOf(liveState.interpreterMode) }
    var selectedVoice by remember(liveState.voiceName) { mutableStateOf(liveState.voiceName) }

    val micPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onStartLiveVoice(interpreterMode, selectedVoice)
        } else {
            onShowBanner("Microphone permission is needed for Vetny Live Voice")
        }
    }

    val pulseTransition = rememberInfiniteTransition(label = "live_voice_pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.16f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .scale(if (liveState.isActive) pulseScale else 1f)
                            .clip(CircleShape)
                            .background(
                                if (liveState.isActive) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.tertiary
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (liveState.isConnecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(
                                imageVector = if (liveState.isActive) Icons.Rounded.GraphicEq else Icons.Rounded.Mic,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = when {
                                liveState.isActive -> "Live Voice Active · Gemini 3.8 Live"
                                liveState.isConnecting -> "Connecting Gemini 3.8 Live..."
                                else -> "Vetny Live Voice Studio"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (interpreterMode) {
                                "Real-time voice interpreter -> $targetLanguage"
                            } else {
                                "Speak naturally & interrupt anytime"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (!liveState.isActive && !liveState.isConnecting) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            Icons.Rounded.Translate,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Voice Interpreter ($targetLanguage)",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Switch(
                        checked = interpreterMode,
                        onCheckedChange = { interpreterMode = it }
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    liveVoiceNames.forEach { voice ->
                        FilterChip(
                            selected = selectedVoice == voice,
                            onClick = { selectedVoice = voice },
                            label = { Text(voice.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
            }

            if (liveState.userTranscript.isNotBlank() || liveState.modelTranscript.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (liveState.userTranscript.isNotBlank()) {
                            Text(
                                text = "You: ${liveState.userTranscript}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (liveState.modelTranscript.isNotBlank()) {
                            Text(
                                text = "Vetny: ${liveState.modelTranscript}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            liveState.error?.let { err ->
                Text(
                    text = err,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (liveState.isActive || liveState.isConnecting) {
                Button(
                    onClick = onStopLiveVoice,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("vetny_stop_live_voice_button")
                ) {
                    Icon(Icons.Rounded.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("End Voice Chat & Save")
                }
            } else {
                Button(
                    onClick = {
                        if (DevicePermissionsHelper.hasMicrophonePermission(context)) {
                            onStartLiveVoice(interpreterMode, selectedVoice)
                        } else {
                            micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("vetny_start_live_voice_button")
                ) {
                    Icon(Icons.Rounded.Mic, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (interpreterMode) "Start Live Voice Translator" else "Start Live Voice Chat"
                    )
                }
            }
        }
    }
}
