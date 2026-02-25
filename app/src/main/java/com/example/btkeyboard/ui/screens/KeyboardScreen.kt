package com.example.btkeyboard.ui.screens

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.btkeyboard.model.ConnectionState
import com.example.btkeyboard.model.ModifierKey
import com.example.btkeyboard.model.SpecialKey
import com.example.btkeyboard.ui.components.AppCard
import com.example.btkeyboard.ui.components.SectionTitle
import com.example.btkeyboard.ui.theme.UiTokens

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KeyboardScreen(
    connectionState: ConnectionState,
    inputText: String,
    unsupportedCharCount: Int,
    activeModifiers: Set<ModifierKey>,
    onInputChanged: (String) -> Unit,
    onSendSpecial: (SpecialKey) -> Unit,
    onModifierToggle: (ModifierKey, Boolean) -> Unit,
    onClearUnsupportedCount: () -> Unit,
    onClearInput: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()

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
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Keyboard Input",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Status: ${connectionState.statusLabel()}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (unsupportedCharCount > 0) {
                        AssistChip(
                            onClick = onClearUnsupportedCount,
                            label = { Text("Skipped chars: $unsupportedCharCount") },
                        )
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
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = onInputChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Type from Android keyboard") },
                        minLines = 6,
                        maxLines = 8,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onClearInput) {
                            Text("Clear")
                        }
                        Button(onClick = { onSendSpecial(SpecialKey.ENTER) }) {
                            Text("Enter")
                        }
                        OutlinedButton(onClick = { onSendSpecial(SpecialKey.BACKSPACE) }) {
                            Text("Backspace")
                        }
                    }
                }
            }
        }

        item {
            SectionTitle("Modifiers")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scroll),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ModifierKey.entries.forEach { key ->
                    val selected = key in activeModifiers
                    FilterChip(
                        selected = selected,
                        onClick = { onModifierToggle(key, !selected) },
                        label = { Text(key.name) },
                    )
                }
            }
        }

        item {
            SectionTitle("Core")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    SpecialKey.ESC,
                    SpecialKey.TAB,
                    SpecialKey.ENTER,
                    SpecialKey.BACKSPACE,
                    SpecialKey.ARROW_UP,
                    SpecialKey.ARROW_DOWN,
                    SpecialKey.ARROW_LEFT,
                    SpecialKey.ARROW_RIGHT,
                ).forEach { key ->
                    OutlinedButton(onClick = { onSendSpecial(key) }) {
                        Text(key.shortLabel())
                    }
                }
            }
        }

        item {
            SectionTitle("Navigation")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    SpecialKey.HOME,
                    SpecialKey.END,
                    SpecialKey.PAGE_UP,
                    SpecialKey.PAGE_DOWN,
                    SpecialKey.INSERT,
                    SpecialKey.DELETE,
                ).forEach { key ->
                    OutlinedButton(onClick = { onSendSpecial(key) }) {
                        Text(key.shortLabel())
                    }
                }
            }
        }

        item {
            SectionTitle("Function")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    SpecialKey.F1,
                    SpecialKey.F2,
                    SpecialKey.F3,
                    SpecialKey.F4,
                    SpecialKey.F5,
                    SpecialKey.F6,
                    SpecialKey.F7,
                    SpecialKey.F8,
                    SpecialKey.F9,
                    SpecialKey.F10,
                    SpecialKey.F11,
                    SpecialKey.F12,
                ).forEach { key ->
                    OutlinedButton(onClick = { onSendSpecial(key) }) {
                        Text(key.shortLabel())
                    }
                }
            }
        }

        item {
            SectionTitle("Media")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(SpecialKey.PLAY_PAUSE, SpecialKey.VOL_UP, SpecialKey.VOL_DOWN, SpecialKey.MUTE)
                    .forEach { key ->
                        OutlinedButton(onClick = { onSendSpecial(key) }) {
                            Text(key.shortLabel())
                        }
                    }
            }
        }
    }
}

private fun ConnectionState.statusLabel(): String {
    return when (this) {
        ConnectionState.Idle -> "Idle"
        ConnectionState.Discovering -> "Discovering"
        is ConnectionState.Pairing -> "Pairing ${device.name}"
        is ConnectionState.Connecting -> "Connecting ${device.name}"
        is ConnectionState.Connected -> "Connected ${device.name}"
        is ConnectionState.Error -> "Error: $message"
    }
}

private fun SpecialKey.shortLabel(): String {
    return when (this) {
        SpecialKey.ARROW_UP -> "Up"
        SpecialKey.ARROW_DOWN -> "Down"
        SpecialKey.ARROW_LEFT -> "Left"
        SpecialKey.ARROW_RIGHT -> "Right"
        SpecialKey.PAGE_UP -> "PgUp"
        SpecialKey.PAGE_DOWN -> "PgDn"
        SpecialKey.PLAY_PAUSE -> "Play/Pause"
        SpecialKey.VOL_UP -> "Vol+"
        SpecialKey.VOL_DOWN -> "Vol-"
        else -> name
    }
}
