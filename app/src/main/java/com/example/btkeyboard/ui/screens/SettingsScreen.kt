package com.example.btkeyboard.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.btkeyboard.data.AppSettings
import com.example.btkeyboard.ui.components.AppCard
import com.example.btkeyboard.ui.components.SectionTitle
import com.example.btkeyboard.ui.theme.UiTokens
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: AppSettings,
    diagnostics: List<String>,
    onAutoReconnectChange: (Boolean) -> Unit,
    onForegroundPersistenceChange: (Boolean) -> Unit,
    onPointerSensitivityChange: (Float) -> Unit,
    onClearTrustedDevices: () -> Unit,
    onExportDiagnostics: () -> Unit,
    onClearDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(UiTokens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(UiTokens.SectionSpacing),
    ) {
        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(UiTokens.CardPadding),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SectionTitle("Behavior")
                    ToggleRow(
                        title = "Auto reconnect",
                        subtitle = "Reconnect to last trusted host when service starts",
                        checked = settings.autoReconnect,
                        onCheckedChange = onAutoReconnectChange,
                    )
                    HorizontalDivider()
                    ToggleRow(
                        title = "Foreground persistence",
                        subtitle = "Keep service active when app is backgrounded",
                        checked = settings.foregroundPersistence,
                        onCheckedChange = onForegroundPersistenceChange,
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
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(UiTokens.CardPadding),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SectionTitle("Maintenance")
                    Button(onClick = onClearTrustedDevices) {
                        Text("Clear trusted devices")
                    }
                    Button(onClick = onExportDiagnostics) {
                        Text("Export diagnostics")
                    }
                    Button(onClick = onClearDiagnostics) {
                        Text("Clear diagnostics")
                    }
                }
            }
        }

        item { SectionTitle("Diagnostics") }

        if (diagnostics.isEmpty()) {
            item {
                Text("No diagnostics entries yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            items(diagnostics.takeLast(50).reversed()) { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth(),
                )
                HorizontalDivider()
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
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Pointer sensitivity", fontWeight = FontWeight.SemiBold)
            Text("${steppedValue}x", style = MaterialTheme.typography.labelLarge)
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
            "Higher values increase cursor speed.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
