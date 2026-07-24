package com.astrohark.app.ui.chat

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.database.StandaloneDatabaseProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class ChatAudioPlayer(private val context: Context) {
    companion object {
        private var activePlayerInstance: ChatAudioPlayer? = null
        private var cache: SimpleCache? = null

        @Synchronized
        private fun getCache(context: Context): SimpleCache {
            if (cache == null) {
                val cacheDir = File(context.cacheDir, "exo_audio_cache")
                val evictor = LeastRecentlyUsedCacheEvictor(100 * 1024 * 1024) // 100MB
                val databaseProvider = StandaloneDatabaseProvider(context)
                cache = SimpleCache(cacheDir, evictor, databaseProvider)
            }
            return cache!!
        }
    }

    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())
    private var progressJob: Job? = null
    private var exoPlayer: ExoPlayer? = null

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

    init {
        initializePlayer()
    }

    private fun initializePlayer() {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(getCache(context))
            .setUpstreamDataSourceFactory(httpDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        exoPlayer = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(cacheDataSourceFactory))
            .build().apply {
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build()
                setAudioAttributes(audioAttributes, true)
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        _isPreparing.value = (playbackState == Player.STATE_BUFFERING)
                        if (playbackState == Player.STATE_READY) {
                            _duration.value = this@apply.duration.toFloat()
                        }
                        if (playbackState == Player.STATE_ENDED) {
                            stop()
                        }
                    }
                    override fun onIsPlayingChanged(isPlayingStatus: Boolean) {
                        _isPlaying.value = isPlayingStatus
                        if (isPlayingStatus) startProgressUpdate() else stopProgressUpdate()
                    }
                    override fun onPlayerError(error: PlaybackException) {
                        _isPreparing.value = false
                        _isPlaying.value = false
                        stopProgressUpdate()
                        Toast.makeText(context, "Audio playback error: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
                })
            }
    }

    fun play(messageId: String, url: String, durationMs: Long = 0L) {
        if (_currentMessageId.value == messageId && exoPlayer?.isPlaying == true) {
            pause()
            return
        }
        if (_currentMessageId.value == messageId && exoPlayer?.playbackState == Player.STATE_READY) {
            exoPlayer?.play()
            return
        }

        if (activePlayerInstance != this) {
            activePlayerInstance?.stop()
            activePlayerInstance = this
        }

        stop()
        _currentMessageId.value = messageId
        _currentUrl.value = url
        _isPreparing.value = true

        val uri = if (url.startsWith("http")) Uri.parse(url) else Uri.fromFile(File(url))
        val mediaItem = MediaItem.fromUri(uri)
        
        exoPlayer?.setMediaItem(mediaItem)
        exoPlayer?.prepare()
        exoPlayer?.playWhenReady = true
    }

    private fun startProgressUpdate() {
        progressJob?.cancel()
        progressJob = coroutineScope.launch {
            while (isActive) {
                exoPlayer?.let { player ->
                    val durationMs = player.duration
                    if (durationMs > 0 && durationMs != C.TIME_UNSET) {
                        _progress.value = player.currentPosition.toFloat() / durationMs.toFloat()
                    }
                }
                delay(50)
            }
        }
    }

    private fun stopProgressUpdate() {
        progressJob?.cancel()
    }

    fun start() {
        exoPlayer?.play()
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun stop() {
        exoPlayer?.stop()
        exoPlayer?.clearMediaItems()
        stopProgressUpdate()
        _isPreparing.value = false
        _isPlaying.value = false
        _progress.value = 0f
        _currentUrl.value = null
        _currentMessageId.value = null
    }

    fun seekTo(progressFraction: Float) {
        val durationMs = exoPlayer?.duration ?: 0L
        if (durationMs > 0 && durationMs != C.TIME_UNSET) {
            val seekPosition = (durationMs * progressFraction).toLong()
            exoPlayer?.seekTo(seekPosition)
            _progress.value = progressFraction
        }
    }

    fun release() {
        if (activePlayerInstance == this) {
            activePlayerInstance = null
        }
        stop()
        exoPlayer?.release()
        exoPlayer = null
        coroutineScope.cancel()
    }
}
