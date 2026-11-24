package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "AudioPlaybackHelper"

class AudioPlaybackHelper(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var audioTrack: AudioTrack? = null
    private var tempFile: File? = null
    private val sessionCounter = AtomicLong(0L)

    private val _playingMessageId = MutableStateFlow<String?>(null)
    val playingMessageId: StateFlow<String?> = _playingMessageId.asStateFlow()

    suspend fun playAudio(
        messageId: String,
        bytes: ByteArray,
        mimeType: String,
        onError: (String) -> Unit
    ) {
        stop()
        val sessionId = sessionCounter.incrementAndGet()
        _playingMessageId.value = messageId
        try {
            if (bytes.isEmpty()) {
                onError("Generated speech audio was empty.")
                return
            }
            val normalized = mimeType.lowercase()
            if (normalized.contains("pcm") || normalized.contains("l16")) {
                val sampleRate = parseSampleRate(normalized) ?: 24000
                playPcmAudio(bytes, sampleRate, sessionId)
            } else {
                val ext = when {
                    normalized.contains("wav") -> "wav"
                    normalized.contains("ogg") -> "ogg"
                    normalized.contains("mp4") || normalized.contains("aac") -> "m4a"
                    else -> "mp3"
                }
                playEncodedAudio(bytes, ext, sessionId)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Audio playback failed", e)
            onError(e.localizedMessage ?: "Audio playback failed.")
        } finally {
            if (sessionCounter.get() == sessionId && _playingMessageId.value == messageId) {
                _playingMessageId.value = null
            }
        }
    }

    private suspend fun playPcmAudio(bytes: ByteArray, sampleRate: Int, sessionId: Long) =
        withContext(Dispatchers.IO) {
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            if (track.state != AudioTrack.STATE_INITIALIZED) {
                track.release()
                throw IllegalStateException("AudioTrack could not be initialized for PCM playback.")
            }

            audioTrack = track
            try {
                track.play()
                var offset = 0
                while (offset < bytes.size &&
                    currentCoroutineContext().isActive &&
                    sessionCounter.get() == sessionId &&
                    _playingMessageId.value != null
                ) {
                    val chunkSize = minOf(minBuf, bytes.size - offset)
                    val written = track.write(bytes, offset, chunkSize)
                    if (written <= 0) break
                    offset += written
                }
            } finally {
                if (audioTrack === track) {
                    stopAudioTrack(track)
                } else {
                    try {
                        track.stop()
                        track.release()
                    } catch (_: Exception) {
                    }
                }
            }
        }

    private suspend fun playEncodedAudio(bytes: ByteArray, ext: String, sessionId: Long) =
        withContext(Dispatchers.IO) {
            val file = File(context.cacheDir, "tts_${System.currentTimeMillis()}.$ext")
            tempFile = file
            file.writeBytes(bytes)

            val player = MediaPlayer()
            mediaPlayer = player
            val completionLatch = CompletableDeferred<Unit>()
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            player.setDataSource(file.absolutePath)
            player.setOnCompletionListener {
                completionLatch.complete(Unit)
            }
            player.setOnErrorListener { _, what, extra ->
                completionLatch.completeExceptionally(
                    IllegalStateException("MediaPlayer error $what/$extra")
                )
                true
            }
            player.prepare()
            if (sessionCounter.get() != sessionId) {
                stopMediaPlayer(player, file)
                return@withContext
            }
            player.start()
            try {
                completionLatch.await()
            } finally {
                if (mediaPlayer === player) {
                    stopMediaPlayer(player, file)
                } else {
                    try {
                        player.stop()
                        player.release()
                    } catch (_: Exception) {
                    }
                    file.delete()
                }
            }
        }

    fun stop() {
        sessionCounter.incrementAndGet()
        _playingMessageId.value = null
        stopAudioTrack(audioTrack)
        stopMediaPlayer(mediaPlayer, tempFile)
    }

    private fun stopAudioTrack(target: AudioTrack?) {
        try {
            target?.stop()
            target?.release()
        } catch (_: Exception) {
        } finally {
            if (audioTrack === target) {
                audioTrack = null
            }
        }
    }

    private fun stopMediaPlayer(target: MediaPlayer?, file: File?) {
        try {
            target?.stop()
            target?.release()
        } catch (_: Exception) {
        } finally {
            if (mediaPlayer === target) {
                mediaPlayer = null
            }
            file?.delete()
            if (tempFile === file) {
                tempFile = null
            }
        }
    }

    private fun parseSampleRate(mimeType: String): Int? {
        val match = Regex("rate=(\\d+)").find(mimeType) ?: return null
        return match.groupValues.getOrNull(1)?.toIntOrNull()
    }
}
