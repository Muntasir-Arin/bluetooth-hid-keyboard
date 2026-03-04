package com.example.btkeyboard.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.example.btkeyboard.data.AppSettings
import com.example.btkeyboard.data.AppThemeMode
import com.example.btkeyboard.ui.components.AppButton
import com.example.btkeyboard.ui.components.AppButtonVariant
import com.example.btkeyboard.ui.components.AppCard
import com.example.btkeyboard.ui.components.AppCardVariant
import com.example.btkeyboard.ui.components.EmptyState
import com.example.btkeyboard.ui.components.SectionHeader
import com.example.btkeyboard.ui.theme.AppMono
import com.example.btkeyboard.ui.theme.UiTokens
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    diagnostics: List<String>,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onAutoReconnectChange: (Boolean) -> Unit,
    onPointerSensitivityChange: (Float) -> Unit,
    onClearTrustedDevices: () -> Unit,
    onExportDiagnostics: () -> Unit,
    onClearDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val recentDiagnostics = diagnostics.takeLast(50).reversed()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(UiTokens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(UiTokens.SectionSpacing),
    ) {
        item {
            AppCard(
                modifier = Modifier.fillMaxWidth(),
                variant = AppCardVariant.Emphasis,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(UiTokens.CardPadding),
                    verticalArrangement = Arrangement.spacedBy(UiTokens.Space3),
                ) {
                    SectionHeader(
                        title = "Appearance",
                        subtitle = "Choose how the app should look.",
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(UiTokens.Space2),
                        verticalArrangement = Arrangement.spacedBy(UiTokens.Space2),
                    ) {
                        AppThemeMode.entries.forEach { mode ->
                            val selected = settings.themeMode == mode
                            AppButton(
                                onClick = { onThemeModeChange(mode) },
                                variant = if (selected) AppButtonVariant.Primary else AppButtonVariant.Secondary,
                            ) {
                                Text(mode.label())
                            }
                        }
                    }
                }
            }
        }

        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(UiTokens.CardPadding),
                    verticalArrangement = Arrangement.spacedBy(UiTokens.Space3),
                ) {
                    SectionHeader(
                        title = "Behavior",
                        subtitle = "Connection reliability and pointer control preferences.",
                    )
                    ToggleRow(
                        title = "Auto reconnect",
                        subtitle = "Reconnect to last trusted host when the service starts.",
                        checked = settings.autoReconnect,
                        onCheckedChange = onAutoReconnectChange,
                    )
                    HorizontalDivider()
                    Text(
                        text = "Smart idle mode",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "The Bluetooth service starts on demand, stays active while connected, and stops after 2 minutes in background when disconnected.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HorizontalDivider()
                    PointerSensitivityRow(
                        value = settings.pointerSensitivity,
                        onChange = onPointerSensitivityChange,
                    )
                }
            }
        }

        item {
            AppCard(
                modifier = Modifier.fillMaxWidth(),
                variant = AppCardVariant.Emphasis,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(UiTokens.CardPadding),
                    verticalArrangement = Arrangement.spacedBy(UiTokens.Space2),
                ) {
                    SectionHeader(
                        title = "Maintenance",
                        subtitle = "Operational and troubleshooting actions.",
                    )
                    AppButton(
                        onClick = onClearTrustedDevices,
                        variant = AppButtonVariant.Danger,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Clear trusted devices")
                    }
                    AppButton(
                        onClick = onExportDiagnostics,
                        variant = AppButtonVariant.Secondary,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Export diagnostics")
                    }
                    AppButton(
                        onClick = onClearDiagnostics,
                        variant = AppButtonVariant.Secondary,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Clear diagnostics")
                    }
                }
            }
        }

        item { SectionHeader(title = "Diagnostics", subtitle = "Recent internal app events") }

        if (recentDiagnostics.isEmpty()) {
            item {
                EmptyState("No diagnostics entries yet.")
            }
        } else {
            item {
                AppCard(
                    modifier = Modifier.fillMaxWidth(),
                    variant = AppCardVariant.Interactive,
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        recentDiagnostics.forEachIndexed { index, line ->
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = AppMono),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = UiTokens.CardPadding,
                                        vertical = UiTokens.Space2,
                                    ),
                            )
                            if (index < recentDiagnostics.lastIndex) {
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(UiTokens.Space1),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun PointerSensitivityRow(
    value: Float,
    onChange: (Float) -> Unit,
) {
    val steppedValue = ((value * 10f).roundToInt() / 10f).coerceIn(0.5f, 2.0f)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(UiTokens.Space1),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "Pointer sensitivity",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "${steppedValue}x",
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Slider(
            value = steppedValue,
            onValueChange = { raw ->
                val normalized = ((raw * 10f).roundToInt() / 10f).coerceIn(0.5f, 2.0f)
                onChange(normalized)
            },
            valueRange = 0.5f..2.0f,
            steps = 14,
        )
        Text(
            text = "Higher values increase cursor speed.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun AppThemeMode.label(): String {
    return when (this) {
        AppThemeMode.DARK -> "Dark"
        AppThemeMode.LIGHT -> "Light"
    }
}
