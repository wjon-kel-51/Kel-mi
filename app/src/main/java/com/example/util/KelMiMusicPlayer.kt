package com.example.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

data class KelMiSong(
    val id: String,
    val title: String,
    val artist: String,
    val genre: String,
    val durationLabel: String,
    val bpm: Int,
    val melodyHz: List<Double>,
    val bassHz: List<Double>,
    val accentColorHex: String
)

object KelMiMusicLibrary {
    val songs: List<KelMiSong> = listOf(
        KelMiSong(
            id = "song_guqin_lotus",
            title = "Guqin Lotus Breeze",
            artist = "Kel mi Studio · China",
            genre = "Guqin Lo-Fi",
            durationLabel = "0:24 Loop",
            bpm = 88,
            melodyHz = listOf(523.25, 587.33, 659.25, 783.99, 880.0, 783.99, 659.25, 587.33),
            bassHz = listOf(130.81, 146.83, 164.81, 196.00),
            accentColorHex = "#07A96D"
        ),
        KelMiSong(
            id = "song_shanghai_neon",
            title = "Shanghai Neon Pulse",
            artist = "Velvet Synth · China",
            genre = "Synthwave",
            durationLabel = "0:20 Loop",
            bpm = 118,
            melodyHz = listOf(440.0, 523.25, 659.25, 587.33, 440.0, 659.25, 783.99, 659.25),
            bassHz = listOf(110.0, 110.0, 146.83, 164.81),
            accentColorHex = "#6366F1"
        ),
        KelMiSong(
            id = "song_kyoto_bamboo",
            title = "Kyoto Bamboo Dawn",
            artist = "Sora Keys",
            genre = "Chill Piano",
            durationLabel = "0:28 Loop",
            bpm = 80,
            melodyHz = listOf(392.00, 440.00, 493.88, 587.33, 659.25, 587.33, 493.88, 440.00),
            bassHz = listOf(98.00, 123.47, 146.83, 130.81),
            accentColorHex = "#0D9488"
        ),
        KelMiSong(
            id = "song_lagos_sunset",
            title = "Lagos Sunset Groove",
            artist = "Kora Rhythm",
            genre = "Afro-Chill",
            durationLabel = "0:22 Loop",
            bpm = 104,
            melodyHz = listOf(349.23, 440.00, 523.25, 587.33, 523.25, 440.00, 392.00, 440.00),
            bassHz = listOf(87.31, 110.00, 130.81, 98.00),
            accentColorHex = "#D97706"
        ),
        KelMiSong(
            id = "song_emerald_rain",
            title = "Emerald Tea Rain",
            artist = "Lin Acoustic",
            genre = "Acoustic",
            durationLabel = "0:26 Loop",
            bpm = 92,
            melodyHz = listOf(493.88, 554.37, 659.25, 739.99, 659.25, 554.37, 493.88, 440.00),
            bassHz = listOf(123.47, 164.81, 146.83, 110.00),
            accentColorHex = "#059669"
        ),
        KelMiSong(
            id = "song_silk_road",
            title = "Silk Road Starlight",
            artist = "Desert Oud Ensemble",
            genre = "Global Fusion",
            durationLabel = "0:24 Loop",
            bpm = 96,
            melodyHz = listOf(440.00, 466.16, 554.37, 587.33, 659.25, 587.33, 554.37, 466.16),
            bassHz = listOf(110.00, 146.83, 130.81, 110.00),
            accentColorHex = "#DB2777"
        )
    )

    fun findById(songId: String?): KelMiSong? = songs.firstOrNull { it.id == songId }
}

/**
 * Polyphonic real-time AudioTrack synthesizer for Kel mi in-app Reel & Story songs.
 * Plays immediately offline or online with smooth warm harmonic envelopes and zero external dependencies.
 */
object KelMiMusicPlayer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var playJob: Job? = null

    private val _currentSongId = MutableStateFlow<String?>(null)
    val currentSongId: StateFlow<String?> = _currentSongId.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    fun toggleSong(song: KelMiSong) {
        if (_isPlaying.value && _currentSongId.value == song.id) {
            stop()
        } else {
            playSong(song)
        }
    }

    fun playSong(song: KelMiSong) {
        stop()
        _currentSongId.value = song.id
        _isPlaying.value = true

        playJob = scope.launch {
            val sampleRate = 22050
            val minBuffer = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(2048)

            var audioTrack: AudioTrack? = null
            try {
                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(sampleRate)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBuffer)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                audioTrack.play()

                val stepSeconds = 60.0 / song.bpm.coerceIn(60, 150)
                val stepSamples = (sampleRate * stepSeconds).toInt().coerceAtLeast(1024)
                val buffer = ShortArray(stepSamples)
                var stepIndex = 0

                while (isActive) {
                    val melodyFreq = song.melodyHz[stepIndex % song.melodyHz.size]
                    val harmonicFreq = melodyFreq * 1.5
                    val bassFreq = song.bassHz[(stepIndex / 2) % song.bassHz.size]

                    for (i in 0 until stepSamples) {
                        val t = i.toDouble() / sampleRate
                        val progress = i.toDouble() / stepSamples
                        val attack = (progress / 0.06).coerceAtMost(1.0)
                        val decay = exp(-3.4 * progress)
                        val env = attack * decay

                        val lead = sin(2.0 * PI * melodyFreq * t) * 0.45 +
                            sin(2.0 * PI * harmonicFreq * t) * 0.15
                        val bass = sin(2.0 * PI * bassFreq * t) * 0.30
                        val sample = ((lead * env + bass * (0.4 + 0.6 * env)) * 0.45 * Short.MAX_VALUE)
                            .toInt()
                            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        buffer[i] = sample.toShort()
                    }
                    audioTrack.write(buffer, 0, buffer.size)
                    stepIndex++
                }
            } catch (_: Throwable) {
                // Ignore AudioTrack errors cleanly on headless / restricted devices
            } finally {
                try {
                    audioTrack?.stop()
                } catch (_: Exception) { }
                try {
                    audioTrack?.release()
                } catch (_: Exception) { }
            }
        }
    }

    fun stop() {
        playJob?.cancel()
        playJob = null
        _isPlaying.value = false
        _currentSongId.value = null
    }
}
