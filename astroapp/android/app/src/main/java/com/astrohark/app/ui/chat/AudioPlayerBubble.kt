package com.astrohark.app.ui.chat

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.astrohark.app.ui.theme.CosmicAppTheme
import java.io.IOException

@Composable
fun AudioPlayerBubble(audioUrl: String, durationStr: String, isMe: Boolean) {
    var isPlaying by remember { mutableStateOf(false) }
    var isPreparing by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0f) }
    var duration by remember { mutableStateOf(0f) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    DisposableEffect(audioUrl) {
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
            isPreparing = false
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(8.dp)
            .width(220.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = if (isMe) CosmicAppTheme.colors.accent else Color(0xFF34B7F1),
            modifier = Modifier
                .size(40.dp)
                .clickable {
                    if (isPreparing) {
                        return@clickable // Do nothing if it's currently preparing
                    }
                    if (isPlaying) {
                        mediaPlayer?.pause()
                        isPlaying = false
                    } else {
                        if (mediaPlayer == null) {
                            try {
                                isPreparing = true
                                mediaPlayer = MediaPlayer().apply {
                                    setAudioAttributes(
                                        android.media.AudioAttributes.Builder()
                                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                                            .build()
                                    )
                                    setOnPreparedListener { mp ->
                                        isPreparing = false
                                        duration = mp.duration.toFloat()
                                        mp.start()
                                        isPlaying = true
                                        coroutineScope.launch {
                                            while (isPlaying) {
                                                currentPosition = mp.currentPosition.toFloat()
                                                delay(100)
                                            }
                                        }
                                    }
                                    setOnCompletionListener {
                                        isPlaying = false
                                        currentPosition = 0f
                                    }
                                    setOnErrorListener { _, what, extra ->
                                        isPreparing = false
                                        isPlaying = false
                                        mediaPlayer?.release()
                                        mediaPlayer = null
                                        android.widget.Toast.makeText(context, "Audio Error: $what, $extra", android.widget.Toast.LENGTH_SHORT).show()
                                        android.util.Log.e("AudioPlayerBubble", "MediaPlayer error: $what, $extra")
                                        true
                                    }
                                }

                                coroutineScope.launch(Dispatchers.IO) {
                                    try {
                                        if (audioUrl.startsWith("http")) {
                                            val urlObj = java.net.URL(audioUrl)
                                            val fileName = "cached_audio_${audioUrl.hashCode()}.mp4"
                                            val cachedFile = java.io.File(context.cacheDir, fileName)
                                            if (!cachedFile.exists()) {
                                                urlObj.openStream().use { input ->
                                                    java.io.FileOutputStream(cachedFile).use { output ->
                                                        input.copyTo(output)
                                                    }
                                                }
                                            }
                                            withContext(Dispatchers.Main) {
                                                mediaPlayer?.setDataSource(cachedFile.absolutePath)
                                                mediaPlayer?.prepareAsync()
                                            }
                                        } else {
                                            withContext(Dispatchers.Main) {
                                                mediaPlayer?.setDataSource(audioUrl)
                                                mediaPlayer?.prepareAsync()
                                            }
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        withContext(Dispatchers.Main) {
                                            isPreparing = false
                                            isPlaying = false
                                            mediaPlayer?.release()
                                            mediaPlayer = null
                                            android.widget.Toast.makeText(context, "Download failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                isPreparing = false
                                e.printStackTrace()
                                android.widget.Toast.makeText(context, "Audio Exception: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            mediaPlayer?.start()
                            isPlaying = true
                            coroutineScope.launch {
                                while (isPlaying) {
                                    currentPosition = mediaPlayer?.currentPosition?.toFloat() ?: 0f
                                    delay(100)
                                }
                            }
                        }
                    }
                }
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isPreparing) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            val progress = if (duration > 0f) currentPosition / duration else 0f
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = if (isMe) CosmicAppTheme.colors.accent else Color(0xFF34B7F1),
                trackColor = Color.Gray.copy(alpha = 0.3f),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val currentSec = (currentPosition / 1000).toInt()
                val currentStr = String.format("%02d:%02d", currentSec / 60, currentSec % 60)
                Text(
                    text = if (isPlaying) currentStr else durationStr,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
    }
}
