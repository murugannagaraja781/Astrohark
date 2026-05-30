package com.astrohark.app.ui.chat

import android.content.Context
import android.media.MediaPlayer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import android.widget.Toast
import android.os.Handler
import android.os.Looper

class ChatAudioPlayer(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())
    private var progressJob: Job? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _currentUrl = MutableStateFlow<String?>(null)
    val currentUrl: StateFlow<String?> = _currentUrl.asStateFlow()

    private val _isPreparing = MutableStateFlow(false)
    val isPreparing: StateFlow<Boolean> = _isPreparing.asStateFlow()

    private val _duration = MutableStateFlow(0f)
    val duration: StateFlow<Float> = _duration.asStateFlow()

    fun play(url: String) {
        if (_currentUrl.value == url) {
            if (_isPlaying.value) {
                mediaPlayer?.pause()
                _isPlaying.value = false
                stopProgressUpdate()
            } else {
                mediaPlayer?.start()
                _isPlaying.value = true
                startProgressUpdate()
            }
            return
        }

        stop()
        _currentUrl.value = url
        _isPreparing.value = true

        coroutineScope.launch(Dispatchers.IO) {
            try {
                val cachedFile = if (url.startsWith("http")) {
                    val fileName = "cached_audio_${url.hashCode()}.mp4"
                    val file = File(context.cacheDir, fileName)
                    if (!file.exists()) {
                        val client = OkHttpClient.Builder()
                            .connectTimeout(15, TimeUnit.SECONDS)
                            .readTimeout(15, TimeUnit.SECONDS)
                            .build()
                        val request = Request.Builder()
                            .url(url)
                            .header("User-Agent", "Mozilla/5.0")
                            .build()
                        val response = client.newCall(request).execute()
                        if (response.isSuccessful) {
                            response.body?.byteStream()?.use { input ->
                                FileOutputStream(file).use { output ->
                                    input.copyTo(output)
                                }
                            }
                            if (file.length() == 0L) {
                                file.delete()
                                throw Exception("Downloaded file is empty")
                            }
                        } else {
                            throw Exception("Network Error: ${response.code}")
                        }
                    }
                    if (file.length() == 0L) {
                        file.delete()
                        throw Exception("Cached file is empty")
                    }
                    file
                } else {
                    File(url)
                }

                withContext(Dispatchers.Main) {
                    if (!cachedFile.exists() && url.startsWith("http")) {
                        _isPreparing.value = false
                        return@withContext
                    }
                    mediaPlayer = MediaPlayer().apply {
                        setAudioAttributes(
                            android.media.AudioAttributes.Builder()
                                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                                .build()
                        )
                        
                        var fis: FileInputStream? = null
                        if (cachedFile.exists()) {
                            fis = FileInputStream(cachedFile)
                            setDataSource(fis.fd)
                        } else {
                            setDataSource(url)
                        }

                        setOnPreparedListener { mp ->
                            try { fis?.close() } catch (e: Exception) {}
                            _isPreparing.value = false
                            _duration.value = mp.duration.toFloat()
                            mp.start()
                            _isPlaying.value = true
                            startProgressUpdate()
                        }
                        setOnCompletionListener {
                            _isPlaying.value = false
                            _progress.value = 0f
                            _currentUrl.value = null
                            stopProgressUpdate()
                        }
                        setOnErrorListener { _, what, extra ->
                            try { fis?.close() } catch (e: Exception) {}
                            _isPreparing.value = false
                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(context, "Audio play error: $what, $extra", Toast.LENGTH_SHORT).show()
                            }
                            stop()
                            true
                        }
                        prepareAsync()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _isPreparing.value = false
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    stop()
                }
            }
        }
    }

    private fun startProgressUpdate() {
        progressJob?.cancel()
        progressJob = coroutineScope.launch {
            while (isActive && _isPlaying.value) {
                mediaPlayer?.let {
                    if (it.duration > 0) {
                        _progress.value = it.currentPosition.toFloat() / it.duration
                    }
                }
                delay(100)
            }
        }
    }

    private fun stopProgressUpdate() {
        progressJob?.cancel()
    }

    fun stop() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        _isPlaying.value = false
        _progress.value = 0f
        _currentUrl.value = null
        _isPreparing.value = false
        stopProgressUpdate()
    }

    fun release() {
        stop()
        coroutineScope.cancel()
    }
}
