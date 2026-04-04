package com.astrohark.app.ui.theme.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astrohark.app.ui.theme.AstroDimens
import com.astrohark.app.ui.theme.CosmicAppTheme

/**
 * Reusable Primary Button following the Brand Theme (Cocoa & Gold/Orange)
 */
@Composable
fun AstroButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color? = null,
    contentColor: Color = Color.White,
    isOutline: Boolean = false
) {
    val finalContainerColor = containerColor ?: CosmicAppTheme.colors.accent
    
    if (isOutline) {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.height(AstroDimens.ButtonHeight),
            shape = RoundedCornerShape(AstroDimens.RadiusMedium),
            border = androidx.compose.foundation.BorderStroke(1.dp, finalContainerColor),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = finalContainerColor)
        ) {
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
    } else {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.height(AstroDimens.ButtonHeight),
            shape = RoundedCornerShape(AstroDimens.RadiusMedium),
            colors = ButtonDefaults.buttonColors(
                containerColor = finalContainerColor,
                contentColor = contentColor
            )
        ) {
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * Modern Card with consistent Radius, Elevation, and Surface color.
 */
@Composable
fun AstroCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color? = null,
    elevation: androidx.compose.ui.unit.Dp = AstroDimens.ElevationSmall,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        modifier = modifier,
        shape = RoundedCornerShape(AstroDimens.RadiusLarge),
        colors = CardDefaults.cardColors(
            containerColor = containerColor ?: CosmicAppTheme.colors.cardBg
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation)
    ) {
        content()
    }
}

/**
 * A Section Header used across the app (e.g., "Trending Astrologers", "Daily Horoscope")
 */
@Composable
fun AstroSectionHeader(
    title: String,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AstroDimens.Medium, vertical = AstroDimens.Small),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = CosmicAppTheme.colors.textPrimary
        )
        if (actionText != null) {
            Text(
                text = actionText,
                modifier = Modifier.clickable { onActionClick?.invoke() },
                style = MaterialTheme.typography.labelLarge,
                color = CosmicAppTheme.colors.accent
            )
        }
    }
}

/**
 * Reusable Chip for status (e.g., Online, Offline, Busy)
 */
@Composable
fun AstroStatusChip(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(AstroDimens.RadiusSmall),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = AstroDimens.Small, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}
