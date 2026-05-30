package com.astrohark.app.ui.chat

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
import com.astrohark.app.ui.theme.CosmicAppTheme

@Composable
fun AudioPlayerBubble(audioUrl: String, durationStr: String, isMe: Boolean, audioPlayer: ChatAudioPlayer) {
    val isPlayingGlobal by audioPlayer.isPlaying.collectAsState()
    val currentUrlGlobal by audioPlayer.currentUrl.collectAsState()
    val progressGlobal by audioPlayer.progress.collectAsState()
    val isPreparingGlobal by audioPlayer.isPreparing.collectAsState()
    val durationGlobal by audioPlayer.duration.collectAsState()

    val isThisPlaying = isPlayingGlobal && currentUrlGlobal == audioUrl
    val isThisPreparing = isPreparingGlobal && currentUrlGlobal == audioUrl
    
    val currentProgress = if (currentUrlGlobal == audioUrl) progressGlobal else 0f
    val currentDuration = if (currentUrlGlobal == audioUrl) durationGlobal else 0f
    
    val currentPosition = currentProgress * currentDuration

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
                    if (isThisPreparing) return@clickable
                    audioPlayer.play(audioUrl)
                }
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isThisPreparing) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        imageVector = if (isThisPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isThisPlaying) "Pause" else "Play",
                        tint = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            LinearProgressIndicator(
                progress = { currentProgress },
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
                    text = if (isThisPlaying) currentStr else durationStr,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
    }
}
