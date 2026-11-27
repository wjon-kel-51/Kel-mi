package com.example.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ReadReceiptBlueDark
import com.example.ui.theme.ReadReceiptBlueLight

/**
 * WhatsApp-style message delivery & read receipt indicator:
 * - SENDING: Clock icon
 * - SENT: Single muted tick
 * - DELIVERED: Double muted ticks
 * - READ: Double blue ticks
 * - FAILED: Error indicator
 */
@Composable
fun ReadReceiptTicks(
    status: String,
    modifier: Modifier = Modifier,
    iconSize: Dp = 15.dp
) {
    val isDark = isSystemInDarkTheme()
    val readBlue = if (isDark) ReadReceiptBlueDark else ReadReceiptBlueLight
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)

    val (icon, tint, description) = when (status.uppercase()) {
        "SENDING" -> Triple(Icons.Rounded.Schedule, mutedColor.copy(alpha = 0.7f), "Sending")
        "DELIVERED" -> Triple(Icons.Rounded.DoneAll, mutedColor, "Delivered")
        "READ" -> Triple(Icons.Rounded.DoneAll, readBlue, "Read")
        "FAILED" -> Triple(Icons.Rounded.ErrorOutline, MaterialTheme.colorScheme.error, "Failed to send")
        else -> Triple(Icons.Rounded.Check, mutedColor, "Sent")
    }

    Icon(
        imageVector = icon,
        contentDescription = description,
        tint = tint,
        modifier = modifier.size(iconSize)
    )
}
