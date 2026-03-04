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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.btkeyboard.model.ConnectionState
import com.example.btkeyboard.model.ModifierKey
import com.example.btkeyboard.model.SpecialKey
import com.example.btkeyboard.ui.components.AppButton
import com.example.btkeyboard.ui.components.AppButtonVariant
import com.example.btkeyboard.ui.components.AppCard
import com.example.btkeyboard.ui.components.AppCardVariant
import com.example.btkeyboard.ui.components.ConnectionStatusPill
import com.example.btkeyboard.ui.components.SectionHeader
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
                        title = "Compose & Send",
                        subtitle = "Type with the Android keyboard and send HID key reports.",
                    )
                    ConnectionStatusPill(connectionState = connectionState)
                    if (unsupportedCharCount > 0) {
                        AppButton(
                            onClick = onClearUnsupportedCount,
                            variant = AppButtonVariant.Secondary,
                        ) {
                            Text("Skipped chars: $unsupportedCharCount")
                        }
                    }
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = onInputChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Type from Android keyboard") },
                        minLines = 6,
                        maxLines = 8,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(UiTokens.Space2),
                    ) {
                        AppButton(
                            onClick = onClearInput,
                            variant = AppButtonVariant.Secondary,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Clear")
                        }
                        AppButton(
                            onClick = { onSendSpecial(SpecialKey.ENTER) },
                            variant = AppButtonVariant.Primary,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Enter")
                        }
                        AppButton(
                            onClick = { onSendSpecial(SpecialKey.BACKSPACE) },
                            variant = AppButtonVariant.Secondary,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = "Backspace",
                            )
                        }
                    }
                }
            }
        }

        item {
            SectionHeader(title = "Modifiers", subtitle = "Applies to subsequent key presses")
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(UiTokens.Space2),
                verticalArrangement = Arrangement.spacedBy(UiTokens.Space2),
            ) {
                ModifierKey.entries.forEach { key ->
                    val selected = key in activeModifiers
                    AppButton(
                        onClick = { onModifierToggle(key, !selected) },
                        variant = if (selected) AppButtonVariant.Primary else AppButtonVariant.Secondary,
                    ) {
                        Text(key.name)
                    }
                }
            }
        }

        item {
            KeySection(
                title = "Core",
                keys = listOf(
                    SpecialKey.ESC,
                    SpecialKey.TAB,
                    SpecialKey.ENTER,
                    SpecialKey.BACKSPACE,
                    SpecialKey.ARROW_UP,
                    SpecialKey.ARROW_DOWN,
                    SpecialKey.ARROW_LEFT,
                    SpecialKey.ARROW_RIGHT,
                ),
                onSendSpecial = onSendSpecial,
            )
        }

        item {
            KeySection(
                title = "Navigation",
                keys = listOf(
                    SpecialKey.HOME,
                    SpecialKey.END,
                    SpecialKey.PAGE_UP,
                    SpecialKey.PAGE_DOWN,
                    SpecialKey.INSERT,
                    SpecialKey.DELETE,
                ),
                onSendSpecial = onSendSpecial,
            )
        }

        item {
            KeySection(
                title = "Function",
                keys = listOf(
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
                ),
                onSendSpecial = onSendSpecial,
            )
        }

        item {
            KeySection(
                title = "Media",
                keys = listOf(
                    SpecialKey.PLAY_PAUSE,
                    SpecialKey.VOL_UP,
                    SpecialKey.VOL_DOWN,
                    SpecialKey.MUTE,
                ),
                onSendSpecial = onSendSpecial,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeySection(
    title: String,
    keys: List<SpecialKey>,
    onSendSpecial: (SpecialKey) -> Unit,
) {
    SectionHeader(title = title)
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(UiTokens.Space2),
        verticalArrangement = Arrangement.spacedBy(UiTokens.Space2),
    ) {
        keys.forEach { key ->
            AppButton(
                onClick = { onSendSpecial(key) },
                variant = AppButtonVariant.Secondary,
            ) {
                Text(
                    text = key.shortLabel(),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
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
