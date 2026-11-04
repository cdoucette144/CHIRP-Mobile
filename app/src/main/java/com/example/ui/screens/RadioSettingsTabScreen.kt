package com.example.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.RadioSettingsState
import com.example.domain.RadioCatalog
import com.example.domain.classSupportedRadioModel
import com.example.ui.components.ConnectivityStatusBadge
import com.example.ui.components.RadioBrandModelDropdowns
import com.example.ui.viewmodel.HardwareConnectivityState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RadioSettingsTabScreen(
    selectedRadio: classSupportedRadioModel,
    settings: RadioSettingsState,
    connectivityState: HardwareConnectivityState,
    onSelectRadio: (classSupportedRadioModel) -> Unit,
    onUpdateSettings: (RadioSettingsState) -> Unit,
    onRetryConnection: () -> Unit = {},
    onDisconnectHardware: () -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("radio_settings_tab_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Active Radio Profile Selector & Live Connectivity
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "RADIO MODEL & HARDWARE LINK",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        ConnectivityStatusBadge(
                            state = connectivityState,
                            onRetry = onRetryConnection,
                            onDisconnect = onDisconnectHardware,
                            onTapBadge = {}
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    RadioBrandModelDropdowns(
                        selectedRadio = selectedRadio,
                        onSelectRadio = onSelectRadio
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${selectedRadio.bandsSummary} • ${selectedRadio.transport.label} • Max ${selectedRadio.maxChannels} Channels",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Text("Draft settings only. These controls are not synchronized with the radio. Channel uploads preserve the radio's existing settings.",color=MaterialTheme.colorScheme.error)
        }
        if (selectedRadio.id == "baofeng_uv32") {
            item {
                val snapshot = settings.uv32Downloaded
                Text("UV-32 GPS", style = MaterialTheme.typography.titleMedium)
                if (snapshot == null) {
                    Text("Download from the UV-32 to see its GPS settings.", style = MaterialTheme.typography.bodySmall)
                } else {
                    val enabled = snapshot.gpsEnabled?.let { if (it) "On" else "Off" } ?: "Unknown"
                    val timeZone = snapshot.gpsUtcOffsetHours?.let { "UTC%+d".format(it) } ?: "Unknown"
                    Text("GPS $enabled · ${snapshot.gpsMode ?: "Unknown mode"} · $timeZone")
                    snapshot.scanMode?.let { Text("Radio scan mode: $it") }
                }
            }
            item {
                Text("UV-32 zone names", style = MaterialTheme.typography.titleMedium)
                Text("Names are saved on this phone. The radio keeps its current names until zone uploads are supported.",
                    style = MaterialTheme.typography.bodySmall)
            }
            items(10) { index ->
                val slot = index + 1
                val radioName = settings.uv32Downloaded?.zoneNames?.getOrNull(index).orEmpty()
                OutlinedTextField(
                    value = settings.uv32ZoneNames[index],
                    onValueChange = { input ->
                        val names = settings.uv32ZoneNames.toMutableList()
                        names[index] = input.filter { it in ' '..'~' }.take(16)
                        onUpdateSettings(settings.copy(uv32ZoneNames = names))
                    },
                    label = { Text("Zone $slot · channels ${index * 100 + 1}–${slot * 100}") },
                    placeholder = { Text(radioName.ifBlank { "Zone $slot" }) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Section 1: Basic Settings (Language, Squelch, TOT, Backlight, Battery Save)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "BASIC SETTINGS",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    SettingChipSelectorRow(
                        label = "Voice Prompt Language",
                        options = RadioSettingsState.languageOptions,
                        selected = settings.voiceLanguage,
                        onSelect = { onUpdateSettings(settings.copy(voiceLanguage = it)) }
                    )

                    SettingChipSelectorRow(
                        label = "Squelch Level (SQL 0–9)",
                        options = RadioSettingsState.squelchOptions,
                        selected = settings.squelchLevel.toString(),
                        onSelect = {
                            onUpdateSettings(settings.copy(squelchLevel = it.toIntOrNull() ?: 3))
                        }
                    )

                    SettingChipSelectorRow(
                        label = "Time-Out Timer (TOT)",
                        options = RadioSettingsState.totOptions,
                        selected = settings.timeOutTimerSec,
                        onSelect = { onUpdateSettings(settings.copy(timeOutTimerSec = it)) }
                    )

                    SettingChipSelectorRow(
                        label = "Backlight Timeout (ABR)",
                        options = RadioSettingsState.backlightOptions,
                        selected = settings.backlightTimeout,
                        onSelect = { onUpdateSettings(settings.copy(backlightTimeout = it)) }
                    )

                    SettingChipSelectorRow(
                        label = "Battery Save Ratio (SAVE)",
                        options = RadioSettingsState.batterySaveOptions,
                        selected = settings.batterySaveRatio,
                        onSelect = { onUpdateSettings(settings.copy(batterySaveRatio = it)) }
                    )

                    SettingChipSelectorRow(
                        label = "VOX Sensitivity",
                        options = RadioSettingsState.voxOptions,
                        selected = settings.voxSensitivity,
                        onSelect = { onUpdateSettings(settings.copy(voxSensitivity = it)) }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                    SettingToggleRow(
                        title = "Keypad Beep Prompt",
                        subtitle = "Audible confirmation tone on keypad press",
                        checked = settings.keypadBeep,
                        onCheckedChange = { onUpdateSettings(settings.copy(keypadBeep = it)) }
                    )

                    SettingToggleRow(
                        title = "Dual Watch / Dual Receive (TDR)",
                        subtitle = "Monitor both VFO A and VFO B simultaneously",
                        checked = settings.dualWatchTdr,
                        onCheckedChange = { onUpdateSettings(settings.copy(dualWatchTdr = it)) }
                    )

                    SettingToggleRow(
                        title = "Squelch Tail Elimination (STE)",
                        subtitle = "Suppresses squelch burst noise on PTT release",
                        checked = settings.tailToneElimination,
                        onCheckedChange = { onUpdateSettings(settings.copy(tailToneElimination = it)) }
                    )
                }
            }
        }

        // Section 2: Button & Side-Key Options (Just like CHIRP)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "BUTTON & SIDE-KEY OPTIONS",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    SettingChipSelectorRow(
                        label = "Side Key 1 (PF1) — Short Press",
                        options = RadioSettingsState.sideKeyActions,
                        selected = settings.sideKey1Short,
                        onSelect = { onUpdateSettings(settings.copy(sideKey1Short = it)) }
                    )

                    SettingChipSelectorRow(
                        label = "Side Key 1 (PF1) — Long Press",
                        options = RadioSettingsState.sideKeyActions,
                        selected = settings.sideKey1Long,
                        onSelect = { onUpdateSettings(settings.copy(sideKey1Long = it)) }
                    )

                    SettingChipSelectorRow(
                        label = "Side Key 2 (PF2) — Short Press",
                        options = RadioSettingsState.sideKeyActions,
                        selected = settings.sideKey2Short,
                        onSelect = { onUpdateSettings(settings.copy(sideKey2Short = it)) }
                    )

                    SettingChipSelectorRow(
                        label = "Side Key 2 (PF2) — Long Press",
                        options = RadioSettingsState.sideKeyActions,
                        selected = settings.sideKey2Long,
                        onSelect = { onUpdateSettings(settings.copy(sideKey2Long = it)) }
                    )
                }
            }
        }

        // Section 3: Display, Work Mode, Power-On Message & DTMF / Roger Beep
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "WORK MODE, DISPLAY & POWER-ON MESSAGE",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    SettingChipSelectorRow(
                        label = "Work Mode",
                        options = RadioSettingsState.workModes,
                        selected = settings.workModeA,
                        onSelect = { onUpdateSettings(settings.copy(workModeA = it)) }
                    )

                    SettingChipSelectorRow(
                        label = "Channel A Display Mode (MDF-A)",
                        options = RadioSettingsState.displayFormats,
                        selected = settings.displayFormatA,
                        onSelect = { onUpdateSettings(settings.copy(displayFormatA = it)) }
                    )

                    SettingChipSelectorRow(
                        label = "Channel B Display Mode (MDF-B)",
                        options = RadioSettingsState.displayFormats,
                        selected = settings.displayFormatB,
                        onSelect = { onUpdateSettings(settings.copy(displayFormatB = it)) }
                    )

                    SettingChipSelectorRow(
                        label = "Roger Beep (ROGER)",
                        options = RadioSettingsState.rogerBeepOptions,
                        selected = settings.rogerBeep,
                        onSelect = { onUpdateSettings(settings.copy(rogerBeep = it)) }
                    )

                    SettingChipSelectorRow(
                        label = "Power-On Message Mode (PONMSG)",
                        options = RadioSettingsState.powerOnModes,
                        selected = settings.powerOnMessageMode,
                        onSelect = { onUpdateSettings(settings.copy(powerOnMessageMode = it)) }
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = settings.powerOnLine1,
                            onValueChange = { onUpdateSettings(settings.copy(powerOnLine1 = it.take(7))) },
                            label = { Text("Power-On Line 1 (7 chars)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = settings.powerOnLine2,
                            onValueChange = { onUpdateSettings(settings.copy(powerOnLine2 = it.take(7))) },
                            label = { Text("Power-On Line 2 (7 chars)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    SettingChipSelectorRow(
                        label = "PTT-ID Transmit Mode",
                        options = RadioSettingsState.pttIdModes,
                        selected = settings.pttIdMode,
                        onSelect = { onUpdateSettings(settings.copy(pttIdMode = it)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingChipSelectorRow(label: String,options: List<String>,selected: String,onSelect: (String)->Unit) {
    com.example.ui.components.ChoiceDropdown(label,options,selected,onSelect)
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
