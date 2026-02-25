package com.example.btkeyboard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.btkeyboard.model.ConnectionState
import com.example.btkeyboard.ui.theme.AppAccent
import com.example.btkeyboard.ui.theme.AppTextSecondary
import com.example.btkeyboard.ui.theme.UiTokens

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier
            .border(
                width = UiTokens.BorderWidth,
                color = MaterialTheme.colorScheme.outline,
                shape = MaterialTheme.shapes.medium,
            ),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
    ) {
        content()
    }
}

@Composable
fun SectionTitle(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier,
    )
}

@Composable
fun ConnectionStatusPill(
    connectionState: ConnectionState,
    modifier: Modifier = Modifier,
) {
    val connected = connectionState is ConnectionState.Connected
    val indicatorColor = if (connected) AppAccent else AppTextSecondary
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(indicatorColor)
                .padding(4.dp),
        )
        Text(
            text = connectionState.shortLabel(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

fun ConnectionState.shortLabel(): String {
    return when (this) {
        ConnectionState.Idle -> "Idle"
        ConnectionState.Discovering -> "Discovering"
        is ConnectionState.Pairing -> "Pairing ${device.name}"
        is ConnectionState.Connecting -> "Connecting ${device.name}"
        is ConnectionState.Connected -> "Connected ${device.name}"
        is ConnectionState.Error -> "Error"
    }
}
