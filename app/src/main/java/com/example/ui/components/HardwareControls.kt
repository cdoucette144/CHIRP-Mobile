package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.RadioCatalog
import com.example.domain.classSupportedRadioModel
import com.example.ui.theme.AmberVfoPrimary
import com.example.ui.theme.RxSafeGreen
import com.example.ui.viewmodel.HardwareConnectionStatus
import com.example.ui.viewmodel.HardwareConnectivityState

@Composable
fun ConnectivityStatusBadge(
    state: HardwareConnectivityState,
    onRetry: () -> Unit,
    onDisconnect: () -> Unit,
    onTapBadge: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusBgColor by animateColorAsState(
        targetValue = when (state.status) {
            HardwareConnectionStatus.CONNECTED -> RxSafeGreen.copy(alpha = 0.15f)
            HardwareConnectionStatus.CONNECTING -> AmberVfoPrimary.copy(alpha = 0.18f)
            HardwareConnectionStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
            HardwareConnectionStatus.DISCONNECTED -> MaterialTheme.colorScheme.surfaceVariant
        },
        label = "statusBg"
    )

    val statusBorderColor by animateColorAsState(
        targetValue = when (state.status) {
            HardwareConnectionStatus.CONNECTED -> RxSafeGreen
            HardwareConnectionStatus.CONNECTING -> AmberVfoPrimary
            HardwareConnectionStatus.FAILED -> MaterialTheme.colorScheme.error
            HardwareConnectionStatus.DISCONNECTED -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        },
        label = "statusBorder"
    )

    Surface(
        color = statusBgColor,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .border(1.dp, statusBorderColor, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .clickable { onTapBadge() }
            .testTag("connectivity_status_badge")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            when (state.status) {
                HardwareConnectionStatus.DISCONNECTED -> {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color.Gray, CircleShape)
                    )
                    Text(
                        text = "Hardware: Disconnected",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HardwareConnectionStatus.CONNECTING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp,
                        color = AmberVfoPrimary
                    )
                    val icon = if (state.transport.contains("USB")) Icons.Default.Usb else Icons.Default.Bluetooth
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = AmberVfoPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Connecting ${state.transport}...",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = AmberVfoPrimary
                    )
                }

                HardwareConnectionStatus.CONNECTED -> {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = RxSafeGreen,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Connected: ${state.deviceLabel.take(24)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = RxSafeGreen,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(
                        onClick = onDisconnect,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Disconnect radio",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                HardwareConnectionStatus.FAILED -> {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Handshake Failed",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    OutlinedButton(
                        onClick = onRetry,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier
                            .height(26.dp)
                            .testTag("retry_hardware_handshake_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Retry", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioBrandModelDropdowns(
    selectedRadio: classSupportedRadioModel,
    onSelectRadio: (classSupportedRadioModel) -> Unit,
    modifier: Modifier = Modifier
) {
    var brandExpanded by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }

    val allBrands = remember { RadioCatalog.allVendors }
    var currentBrand by remember(selectedRadio) { mutableStateOf(selectedRadio.vendor) }
    val modelsForBrand = remember(currentBrand) { RadioCatalog.getRadiosForVendor(currentBrand) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. BRAND DROPDOWN
        ExposedDropdownMenuBox(
            expanded = brandExpanded,
            onExpandedChange = { brandExpanded = it },
            modifier = Modifier.weight(1f)
        ) {
            OutlinedTextField(
                value = currentBrand,
                onValueChange = {},
                readOnly = true,
                label = { Text("Brand") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = brandExpanded) },
                singleLine = true,
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                    .fillMaxWidth()
                    .testTag("radio_brand_dropdown")
            )
            ExposedDropdownMenu(
                expanded = brandExpanded,
                onDismissRequest = { brandExpanded = false }
            ) {
                allBrands.forEach { brand ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = brand,
                                fontWeight = if (brand == currentBrand) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            currentBrand = brand
                            brandExpanded = false
                            val firstInBrand = RadioCatalog.getRadiosForVendor(brand).firstOrNull()
                            if (firstInBrand != null && firstInBrand.id != selectedRadio.id) {
                                onSelectRadio(firstInBrand)
                            }
                        }
                    )
                }
            }
        }

        // 2. MODEL DROPDOWN
        ExposedDropdownMenuBox(
            expanded = modelExpanded,
            onExpandedChange = { modelExpanded = it },
            modifier = Modifier.weight(1.5f)
        ) {
            OutlinedTextField(
                value = selectedRadio.model,
                onValueChange = {},
                readOnly = true,
                label = { Text("Model (${selectedRadio.maxChannels} ch)") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded) },
                singleLine = true,
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                    .fillMaxWidth()
                    .testTag("radio_model_dropdown")
            )
            ExposedDropdownMenu(
                expanded = modelExpanded,
                onDismissRequest = { modelExpanded = false }
            ) {
                modelsForBrand.forEach { radio ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    text = radio.model,
                                    fontWeight = if (radio.id == selectedRadio.id) FontWeight.Bold else FontWeight.Normal
                                )
                                Text(
                                    text = "${radio.maxChannels} ch • ${radio.bandsSummary.take(28)}...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        onClick = {
                            onSelectRadio(radio)
                            modelExpanded = false
                        }
                    )
                }
            }
        }
    }
}
