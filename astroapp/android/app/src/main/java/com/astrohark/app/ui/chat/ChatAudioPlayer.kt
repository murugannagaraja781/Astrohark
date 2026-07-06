package com.astrohark.app.ui.chat

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import android.widget.Toast
import android.media.AudioManager
import android.os.Build

class ChatAudioPlayer(private val context: Context) {
    companion object {
        // Shared single instance of ExoPlayer globally
        private var sharedExoPlayer: ExoPlayer? = null
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
        // Initialize ExoPlayer on the Main thread
        initExoPlayer()
    }

    private fun initExoPlayer() {
        try {
            if (sharedExoPlayer == null) {
                // Configure DefaultHttpDataSource with Chrome User-Agent to bypass Cloudflare
                val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                val mediaSourceFactory = DefaultMediaSourceFactory(httpDataSourceFactory)

                val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        1000, // minBufferMs
                        2500, // maxBufferMs
                        400,  // bufferForPlaybackMs
                        800   // bufferForPlaybackAfterRebufferMs
                    )
                    .build()

                sharedExoPlayer = ExoPlayer.Builder(context.applicationContext)
                    .setMediaSourceFactory(mediaSourceFactory)
                    .setLoadControl(loadControl)
                    .build()
            }
            
            if (activePlayerInstance != this) {
                // Detach listener from the previous active instance (resetting its UI states)
                activePlayerInstance?.detachPlayerListeners()
                
                // Attach listener for this instance
                activePlayerInstance = this
                attachPlayerListeners()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("ChatAudioPlayer", "Failed to initialize ExoPlayer", e)
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            val isPlayingNow = sharedExoPlayer?.isPlaying ?: false
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    _isPreparing.value = !isPlayingNow
                }
                Player.STATE_READY -> {
                    _isPreparing.value = false
                    _duration.value = (sharedExoPlayer?.duration ?: 0L).toFloat()
                    _isPlaying.value = sharedExoPlayer?.playWhenReady ?: false
                    if (sharedExoPlayer?.playWhenReady == true) {
                        startProgressUpdate()
                    }
                }
                Player.STATE_ENDED -> {
                    _isPlaying.value = false
                    _progress.value = 0f
                    _currentUrl.value = null
                    _currentMessageId.value = null
                    stopProgressUpdate()
                    abandonAudioFocus()
                }
                Player.STATE_IDLE -> {
                    _isPlaying.value = false
                    _isPreparing.value = false
                }
            }
        }

        override fun onIsPlayingChanged(isPlayingNow: Boolean) {
            _isPlaying.value = isPlayingNow
            if (isPlayingNow) {
                _isPreparing.value = false
                startProgressUpdate()
            } else {
                stopProgressUpdate()
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            _isPreparing.value = false
            _isPlaying.value = false
            stopProgressUpdate()
            abandonAudioFocus()
            Toast.makeText(context, "Audio playback error: ${error.message}", Toast.LENGTH_SHORT).show()
            _currentUrl.value = null
            _currentMessageId.value = null
        }
    }

    private fun attachPlayerListeners() {
        sharedExoPlayer?.addListener(playerListener)
    }

    private fun detachPlayerListeners() {
        sharedExoPlayer?.removeListener(playerListener)
        _isPlaying.value = false
        _progress.value = 0f
        _isPreparing.value = false
        _currentUrl.value = null
        _currentMessageId.value = null
        stopProgressUpdate()
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
        initExoPlayer()
        currentTrackDurationMs = durationMs

        if (_currentMessageId.value == messageId) {
            // Update URL dynamically if local changed to remote URL
            if (_currentUrl.value != url) {
                _currentUrl.value = url
            }
            if (_isPreparing.value) {
                return
            }
            if (_isPlaying.value) {
                pause()
            } else {
                start()
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
                playLocalFile(messageId, cachedFile.absolutePath, durationMs)
            } else {
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

        val mediaUri = Uri.fromFile(File(localPath))
        val mimeType = if (localPath.endsWith(".aac", ignoreCase = true)) "audio/aac" else "video/mp4"
        android.util.Log.d("ChatAudioPlayer", "Playing local cached file: $localPath (MIME: $mimeType)")
        
        val mediaItem = MediaItem.Builder()
            .setUri(mediaUri)
            .setMimeType(mimeType)
            .build()

        sharedExoPlayer?.let { player ->
            player.stop()
            player.clearMediaItems()
            player.setMediaItem(mediaItem)
            player.prepare()
            
            requestAudioFocus()
            player.playWhenReady = true
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
                        // Double check if message ID is still active before playing
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
                        Toast.makeText(context, "Failed to download voice message", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun startProgressUpdate() {
        progressJob?.cancel()
        progressJob = coroutineScope.launch {
            while (isActive && _isPlaying.value) {
                sharedExoPlayer?.let { player ->
                    val current = player.currentPosition.toFloat()
                    val dur = if (player.duration > 0) player.duration.toFloat() else currentTrackDurationMs.toFloat()
                    if (dur > 0) {
                        _progress.value = current / dur
                        _duration.value = dur
                    }
                }
                delay(100)
            }
        }
    }

    private fun stopProgressUpdate() {
        progressJob?.cancel()
    }

    fun start() {
        sharedExoPlayer?.let { player ->
            if (player.playbackState != Player.STATE_IDLE) {
                requestAudioFocus()
                player.playWhenReady = true
            }
        }
    }

    fun pause() {
        sharedExoPlayer?.let { player ->
            player.playWhenReady = false
        }
        _isPlaying.value = false
        stopProgressUpdate()
    }

    fun stop() {
        stopProgressUpdate()
        abandonAudioFocus()
        downloadJob?.cancel()
        
        sharedExoPlayer?.let { player ->
            player.stop()
            player.clearMediaItems()
        }

        _isPreparing.value = false
        _isPlaying.value = false
        _progress.value = 0f
        _currentUrl.value = null
        _currentMessageId.value = null
    }

    fun seekTo(progress: Float) {
        sharedExoPlayer?.let { player ->
            val dur = player.duration
            if (dur > 0) {
                val pos = (dur * progress).toLong()
                player.seekTo(pos)
                _progress.value = progress
            }
        }
    }

    fun release() {
        if (activePlayerInstance == this) {
            stop()
            detachPlayerListeners()
            activePlayerInstance = null
        }
        downloadJob?.cancel()
        coroutineScope.cancel()
    }
}
