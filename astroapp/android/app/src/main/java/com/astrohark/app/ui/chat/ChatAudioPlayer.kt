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
import android.media.AudioManager
import android.os.Build

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

    // Internal robust state flags
    private var isPrepared = false
    private var isReleased = false

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: Any? = null

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            if (_isPlaying.value) {
                try { mediaPlayer?.pause(); _isPlaying.value = false; stopProgressUpdate() } catch(e: Exception) {}
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener(audioFocusChangeListener)
                    .build()
                val res = audioManager.requestAudioFocus(audioFocusRequest as android.media.AudioFocusRequest)
                res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            } else {
                @Suppress("DEPRECATION")
                val res = audioManager.requestAudioFocus(
                    audioFocusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                )
                res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it as android.media.AudioFocusRequest) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(audioFocusChangeListener)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun play(url: String) {
        if (_isPreparing.value) {
            // Ignore rapid clicks while preparing
            return
        }

        if (_currentUrl.value == url) {
            if (_isPlaying.value) {
                try {
                    if (isPrepared && mediaPlayer != null) {
                        mediaPlayer?.pause()
                    }
                } catch (e: Exception) { e.printStackTrace() }
                _isPlaying.value = false
                stopProgressUpdate()
            } else {
                try {
                    if (isPrepared && mediaPlayer != null) {
                        try { requestAudioFocus() } catch(e: Exception){}
                        mediaPlayer?.start()
                        _isPlaying.value = true
                        startProgressUpdate()
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
            return
        }

        stop()
        _currentUrl.value = url
        _isPreparing.value = true
        isPrepared = false
        isReleased = false

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
                    if (isReleased) return@withContext
                    
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
                            if (isReleased) return@setOnPreparedListener
                            
                            isPrepared = true
                            _isPreparing.value = false
                            _duration.value = mp.duration.toFloat()
                            
                            try {
                                try { requestAudioFocus() } catch (e: Exception) {}
                                mp.start()
                                _isPlaying.value = true
                                startProgressUpdate()
                            } catch (e: Exception) { e.printStackTrace() }
                        }
                        
                        setOnCompletionListener {
                            _isPlaying.value = false
                            _progress.value = 0f
                            _currentUrl.value = null
                            stopProgressUpdate()
                            abandonAudioFocus()
                        }
                        
                        setOnErrorListener { _, what, extra ->
                            try { fis?.close() } catch (e: Exception) {}
                            if (!isReleased) {
                                _isPreparing.value = false
                                Handler(Looper.getMainLooper()).post {
                                    Toast.makeText(context, "Audio play error: $what, $extra", Toast.LENGTH_SHORT).show()
                                }
                                stop()
                            }
                            true
                        }
                        
                        try {
                            prepareAsync()
                        } catch (e: Exception) {
                            try { fis?.close() } catch (ex: Exception) {}
                            _isPreparing.value = false
                            _currentUrl.value = null
                            e.printStackTrace()
                        }
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
                try {
                    if (mediaPlayer != null && isPrepared && !isReleased) {
                        mediaPlayer?.let {
                            if (it.isPlaying) {
                                val current = it.currentPosition.toFloat()
                                val dur = it.duration.toFloat()
                                if (dur > 0) {
                                    _progress.value = current / dur
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(100)
            }
        }
    }

    private fun stopProgressUpdate() {
        progressJob?.cancel()
    }

    fun stop() {
        stopProgressUpdate()
        abandonAudioFocus()
        try {
            mediaPlayer?.reset()
        } catch (e: Exception) { e.printStackTrace() }
        try {
            mediaPlayer?.release()
        } catch (e: Exception) { e.printStackTrace() }
        
        mediaPlayer = null
        isReleased = true
        isPrepared = false
        _isPreparing.value = false
        _isPlaying.value = false
        _progress.value = 0f
        _currentUrl.value = null
    }

    fun seekTo(progress: Float) {
        coroutineScope.launch(Dispatchers.Main) {
            if (isPrepared && mediaPlayer != null && !isReleased) {
                try {
                    val dur = mediaPlayer?.duration ?: 0
                    if (dur > 0) {
                        val pos = (dur * progress).toInt()
                        mediaPlayer?.seekTo(pos)
                        _progress.value = progress
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    fun release() {
        stop()
        coroutineScope.cancel()
    }
}
