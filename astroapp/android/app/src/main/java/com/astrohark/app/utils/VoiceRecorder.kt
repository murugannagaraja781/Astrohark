package com.astrohark.app.utils

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File
import java.io.IOException

class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: String = ""
    val currentOutputFile: String
        get() = outputFile
    var isRecording = false
        private set
    var startTime = 0L
        private set

    fun startRecording(): String? {
        val fileName = "voice_${System.currentTimeMillis()}.aac"
        outputFile = "${context.cacheDir.absolutePath}/$fileName"

        recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(outputFile)
            try {
                prepare()
                start()
                isRecording = true
                startTime = System.currentTimeMillis()
                return outputFile
            } catch (e: IOException) {
                Log.e("VoiceRecorder", "prepare() failed", e)
                return null
            }
        }
        return outputFile
    }

    fun stopRecording(): Long {
        if (!isRecording) return 0L
        val duration = System.currentTimeMillis() - startTime
        try {
            recorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            Log.e("VoiceRecorder", "stop() failed", e)
        }
        recorder = null
        isRecording = false
        return duration
    }

    fun cancelRecording() {
        if (!isRecording) return
        try {
            recorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            Log.e("VoiceRecorder", "stop() failed", e)
        }
        recorder = null
        isRecording = false
        val file = File(outputFile)
        if (file.exists()) {
            file.delete()
        }
    }
}
