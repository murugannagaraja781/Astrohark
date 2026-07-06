package com.astrohark.app.ui.chat

import android.content.Context
import android.net.Uri
import android.media.MediaPlayer
import android.widget.Toast
import android.media.AudioManager
import android.os.Build
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class ChatAudioPlayer(private val context: Context) {
    companion object {
        // Shared single instance of MediaPlayer globally
        private var sharedMediaPlayer: MediaPlayer? = null
        private var activePlayerInstance: ChatAudioPlayer? = null
    }

    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())
    private var progressJob: Job? = null
    private var downloadJob: Job? = null
    private var currentTrackDurationMs = 0L

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _currentMessageId = MutableStateFlow<String?>(null)
    val currentMessageId: StateFlow<String?> = _currentMessageId.asStateFlow()

    private val _currentUrl = MutableStateFlow<String?>(null)
    val currentUrl: StateFlow<String?> = _currentUrl.asStateFlow()

    private val _isPreparing = MutableStateFlow(false)
    val isPreparing: StateFlow<Boolean> = _isPreparing.asStateFlow()

    private val _duration = MutableStateFlow(0f)
    val duration: StateFlow<Float> = _duration.asStateFlow()

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: Any? = null

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            if (_isPlaying.value) {
                pause()
            }
        }
    }

    init {
        // Initialize MediaPlayer on the Main thread
        initMediaPlayer()
    }

    private fun initMediaPlayer() {
        try {
            if (sharedMediaPlayer == null) {
                sharedMediaPlayer = MediaPlayer()
            }
            if (activePlayerInstance != this) {
                // Detach listener / stop previous active instance
                activePlayerInstance?.stop()
                activePlayerInstance = this
            }
        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("ChatAudioPlayer", "Failed to initialize MediaPlayer", e)
        }
    }

    private fun isMediaPlayerPlaying(): Boolean {
        return try {
            sharedMediaPlayer?.isPlaying ?: false
        } catch (e: Exception) {
            false
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

    fun play(messageId: String, url: String, durationMs: Long = 0L) {
        initMediaPlayer()
        currentTrackDurationMs = durationMs

        if (_currentMessageId.value == messageId) {
            // Update URL dynamically if local changed to remote URL
            if (_currentUrl.value != url) {
                _currentUrl.value = url
            }
            if (_isPreparing.value) {
                return
            }
            try {
                sharedMediaPlayer?.let { mp ->
                    if (isMediaPlayerPlaying()) {
                        pause()
                    } else {
                        requestAudioFocus()
                        mp.start()
                        _isPlaying.value = true
                        startProgressUpdate()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return
        }

        stopProgressUpdate()
        abandonAudioFocus()
        downloadJob?.cancel()

        // Check if the URL is a remote web URL
        val isRemote = url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)
        if (isRemote) {
            val cachedFileName = "cached_" + url.substringAfterLast("/")
            val cachedFile = File(context.cacheDir, cachedFileName)
            
            if (cachedFile.exists() && cachedFile.length() > 0) {
                Toast.makeText(context, "Loading from cache...", Toast.LENGTH_SHORT).show()
                playLocalFile(messageId, cachedFile.absolutePath, durationMs)
            } else {
                Toast.makeText(context, "Downloading audio...", Toast.LENGTH_SHORT).show()
                downloadAndPlay(messageId, url, durationMs)
            }
        } else {
            // It is already a local path, play it directly
            playLocalFile(messageId, url, durationMs)
        }
    }

    private fun playLocalFile(messageId: String, localPath: String, durationMs: Long) {
        _currentMessageId.value = messageId
        _currentUrl.value = localPath
        _isPreparing.value = true
        _isPlaying.value = false
        _progress.value = 0f

        try {
            sharedMediaPlayer?.let { mp ->
                mp.reset()
                
                mp.setOnCompletionListener {
                    _isPlaying.value = false
                    _progress.value = 0f
                    _currentUrl.value = null
                    _currentMessageId.value = null
                    stopProgressUpdate()
                    abandonAudioFocus()
                }

                mp.setOnPreparedListener { preparedMp ->
                    _isPreparing.value = false
                    val trackDur = if (preparedMp.duration > 0) preparedMp.duration.toFloat() else durationMs.toFloat()
                    _duration.value = trackDur
                    preparedMp.start()
                    _isPlaying.value = true
                    startProgressUpdate()
                }

                mp.setOnErrorListener { _, what, extra ->
                    android.util.Log.e("MediaPlayer", "Error occurred: what=$what, extra=$extra")
                    _isPreparing.value = false
                    _isPlaying.value = false
                    _currentUrl.value = null
                    _currentMessageId.value = null
                    stopProgressUpdate()
                    abandonAudioFocus()
                    Toast.makeText(context, "Playback failed", Toast.LENGTH_SHORT).show()
                    true
                }
                
                mp.setDataSource(localPath)
                mp.prepareAsync()
                requestAudioFocus()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _isPreparing.value = false
            _currentMessageId.value = null
            Toast.makeText(context, "Failed to initialize playback", Toast.LENGTH_SHORT).show()
        }
    }

    private fun downloadAndPlay(messageId: String, url: String, durationMs: Long) {
        _currentMessageId.value = messageId
        _currentUrl.value = url
        _isPreparing.value = true
        _isPlaying.value = false
        _progress.value = 0f

        val cachedFileName = "cached_" + url.substringAfterLast("/")
        val cachedFile = File(context.cacheDir, cachedFileName)

        downloadJob = coroutineScope.launch(Dispatchers.IO) {
            try {
                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                
                val responseCode = conn.responseCode
                if (responseCode == java.net.HttpURLConnection.HTTP_OK) {
                    conn.inputStream.use { input ->
                        cachedFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Download complete: ${cachedFile.length()} bytes", Toast.LENGTH_SHORT).show()
                        if (_currentMessageId.value == messageId) {
                            playLocalFile(messageId, cachedFile.absolutePath, durationMs)
                        }
                    }
                } else {
                    throw java.io.IOException("Server responded with HTTP $responseCode")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    if (_currentMessageId.value == messageId) {
                        _isPreparing.value = false
                        _currentMessageId.value = null
                        Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun startProgressUpdate() {
        progressJob?.cancel()
        progressJob = coroutineScope.launch {
            while (isActive && _isPlaying.value) {
                try {
                    sharedMediaPlayer?.let { mp ->
                        if (isMediaPlayerPlaying()) {
                            val current = mp.currentPosition.toFloat()
                            val dur = if (mp.duration > 0) mp.duration.toFloat() else currentTrackDurationMs.toFloat()
                            if (dur > 0) {
                                _progress.value = current / dur
                                _duration.value = dur
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

    fun start() {
        try {
            sharedMediaPlayer?.let { mp ->
                requestAudioFocus()
                mp.start()
                _isPlaying.value = true
                startProgressUpdate()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun pause() {
        try {
            sharedMediaPlayer?.let { mp ->
                if (isMediaPlayerPlaying()) {
                    mp.pause()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _isPlaying.value = false
        stopProgressUpdate()
    }

    fun stop() {
        stopProgressUpdate()
        abandonAudioFocus()
        downloadJob?.cancel()
        
        try {
            sharedMediaPlayer?.let { mp ->
                if (isMediaPlayerPlaying()) {
                    mp.stop()
                }
                mp.reset()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        _isPreparing.value = false
        _isPlaying.value = false
        _progress.value = 0f
        _currentUrl.value = null
        _currentMessageId.value = null
    }

    fun seekTo(progress: Float) {
        try {
            sharedMediaPlayer?.let { mp ->
                val dur = mp.duration
                if (dur > 0) {
                    val pos = (dur * progress).toInt()
                    mp.seekTo(pos)
                    _progress.value = progress
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun release() {
        if (activePlayerInstance == this) {
            stop()
            activePlayerInstance = null
        }
        downloadJob?.cancel()
        coroutineScope.cancel()
    }
}
