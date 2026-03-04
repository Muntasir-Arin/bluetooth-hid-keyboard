package com.example.btkeyboard.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.example.btkeyboard.model.ConnectionState
import com.example.btkeyboard.ui.theme.AppSemanticColors
import com.example.btkeyboard.ui.theme.UiTokens
import com.example.btkeyboard.ui.theme.appColors

enum class AppCardVariant {
    Default,
    Emphasis,
    Interactive,
}

enum class AppButtonVariant {
    Primary,
    Secondary,
    Tertiary,
    Danger,
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    variant: AppCardVariant = AppCardVariant.Default,
    content: @Composable () -> Unit,
) {
    val borderColor = when (variant) {
        AppCardVariant.Default -> MaterialTheme.colorScheme.outline
        AppCardVariant.Emphasis -> MaterialTheme.colorScheme.outlineVariant
        AppCardVariant.Interactive -> MaterialTheme.colorScheme.outlineVariant
    }
    val containerColor = when (variant) {
        AppCardVariant.Default -> MaterialTheme.colorScheme.surface
        AppCardVariant.Emphasis -> MaterialTheme.colorScheme.surfaceVariant
        AppCardVariant.Interactive -> MaterialTheme.colorScheme.surface
    }

    Surface(
        modifier = modifier.border(
            width = UiTokens.BorderWidth,
            color = borderColor,
            shape = MaterialTheme.shapes.medium,
        ),
        color = containerColor,
        shape = MaterialTheme.shapes.medium,
    ) {
        content()
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(UiTokens.Space1),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailing != null) {
            Box(
                modifier = Modifier.padding(start = UiTokens.Space3),
            ) {
                trailing()
            }
        }
    }
}

@Composable
fun SectionTitle(
    title: String,
    modifier: Modifier = Modifier,
) {
    SectionHeader(
        title = title,
        modifier = modifier,
    )
}

@Composable
fun AppButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: AppButtonVariant = AppButtonVariant.Primary,
    content: @Composable RowScope.() -> Unit,
) {
    val buttonModifier = modifier.heightIn(min = UiTokens.ButtonHeight)
    when (variant) {
        AppButtonVariant.Primary -> {
            Button(
                onClick = onClick,
                enabled = enabled,
                modifier = buttonModifier,
                content = content,
            )
        }

        AppButtonVariant.Secondary -> {
            OutlinedButton(
                onClick = onClick,
                enabled = enabled,
                modifier = buttonModifier,
                content = content,
            )
        }

        AppButtonVariant.Tertiary -> {
            TextButton(
                onClick = onClick,
                enabled = enabled,
                modifier = buttonModifier,
                content = content,
            )
        }

        AppButtonVariant.Danger -> {
            OutlinedButton(
                onClick = onClick,
                enabled = enabled,
                modifier = buttonModifier,
                border = androidx.compose.foundation.BorderStroke(
                    width = UiTokens.BorderWidth,
                    color = MaterialTheme.appColors.error,
                ),
                content = content,
            )
        }
    }
}

@Composable
fun ConnectionStatusPill(
    connectionState: ConnectionState,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.appColors
    val statusStyle = connectionState.statusStyle()
    val containerColor by animateColorAsState(
        targetValue = statusStyle.containerColor(colors),
        animationSpec = tween(durationMillis = 160),
        label = "statusContainer",
    )
    val indicatorColor by animateColorAsState(
        targetValue = statusStyle.indicatorColor(colors),
        animationSpec = tween(durationMillis = 160),
        label = "statusIndicator",
    )
    val textColor by animateColorAsState(
        targetValue = statusStyle.textColor(colors),
        animationSpec = tween(durationMillis = 160),
        label = "statusText",
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(containerColor)
            .padding(
                horizontal = UiTokens.ChipHorizontalPadding,
                vertical = UiTokens.ChipVerticalPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(UiTokens.Space2),
    ) {
        Box(
            modifier = Modifier
                .size(UiTokens.Space2)
                .clip(CircleShape)
                .background(indicatorColor),
        )
        Text(
            text = connectionState.shortLabel(),
            style = MaterialTheme.typography.labelLarge,
            color = textColor,
        )
    }
}

@Composable
fun InfoChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(percent = 50),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = UiTokens.Space3, vertical = UiTokens.Space1),
            horizontalArrangement = Arrangement.spacedBy(UiTokens.Space1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$label:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = UiTokens.Space1),
        verticalArrangement = Arrangement.spacedBy(UiTokens.Space2),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actionLabel != null && onAction != null) {
            AppButton(
                onClick = onAction,
                variant = AppButtonVariant.Secondary,
            ) {
                Text(actionLabel)
            }
        }
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

private enum class StatusStyle {
    Idle,
    Discovering,
    Pairing,
    Connecting,
    Connected,
    Error,
    ;

    fun containerColor(colors: AppSemanticColors): Color {
        return when (this) {
            Idle -> colors.surfacePressed
            Discovering -> colors.surfacePressed
            Pairing -> colors.surfacePressed
            Connecting -> colors.surfacePressed
            Connected -> colors.success.copy(alpha = 0.2f)
            Error -> colors.error.copy(alpha = 0.2f)
        }
    }

    fun indicatorColor(colors: AppSemanticColors): Color {
        return when (this) {
            Idle -> colors.textSecondary
            Discovering -> colors.warning
            Pairing -> colors.warning
            Connecting -> colors.accent
            Connected -> colors.success
            Error -> colors.error
        }
    }

    fun textColor(colors: AppSemanticColors): Color {
        return when (this) {
            Connected -> colors.success
            Error -> colors.error
            else -> colors.textSecondary
        }
    }
}

private fun ConnectionState.statusStyle(): StatusStyle {
    return when (this) {
        ConnectionState.Idle -> StatusStyle.Idle
        ConnectionState.Discovering -> StatusStyle.Discovering
        is ConnectionState.Pairing -> StatusStyle.Pairing
        is ConnectionState.Connecting -> StatusStyle.Connecting
        is ConnectionState.Connected -> StatusStyle.Connected
        is ConnectionState.Error -> StatusStyle.Error
    }
}
