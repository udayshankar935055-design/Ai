package com.example.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

private const val TAG = "AudioRecorderHelper"

class AudioRecorderHelper(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun startRecording(): Boolean {
        return try {
            stopAndDiscard()
            val file = File(context.cacheDir, "voice_input_${System.currentTimeMillis()}.m4a")
            outputFile = file
            val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mediaRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(16000)
                setAudioEncodingBitRate(64000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = mediaRecorder
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio recording", e)
            stopAndDiscard()
            false
        }
    }

    fun stopAndReadBytes(): ByteArray? {
        return try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            val file = outputFile
            outputFile = null
            val bytes = file?.takeIf { it.exists() && it.length() > 0 }?.readBytes()
            file?.delete()
            bytes
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop audio recording", e)
            stopAndDiscard()
            null
        }
    }

    fun stopAndDiscard() {
        try {
            recorder?.release()
        } catch (_: Exception) {
        } finally {
            recorder = null
            outputFile?.delete()
            outputFile = null
        }
    }
}
