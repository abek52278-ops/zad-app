package com.example.utils

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File
import java.io.IOException

class AudioRecorderHelper(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null

    companion object {
        private const val TAG = "AudioRecorderHelper"
    }

    fun startRecording(): File? {
        val outputDir = context.cacheDir
        currentOutputFile = File.createTempFile("zad_voice_", ".m4a", outputDir)

        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        return try {
            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(currentOutputFile?.absolutePath)
                prepare()
                start()
            }
            Log.d(TAG, "Recording started: ${currentOutputFile?.absolutePath}")
            currentOutputFile
        } catch (e: IOException) {
            Log.e(TAG, "prepare() failed", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "startRecording() failed", e)
            null
        }
    }

    fun stopRecording(): File? {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            Log.d(TAG, "Recording stopped")
            return currentOutputFile
        } catch (e: Exception) {
            Log.e(TAG, "stopRecording() failed", e)
            mediaRecorder?.release()
            mediaRecorder = null
            return null
        }
    }
}
