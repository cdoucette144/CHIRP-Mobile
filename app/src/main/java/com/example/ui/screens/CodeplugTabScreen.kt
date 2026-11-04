package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.DuplexMode
import com.example.data.model.PowerLevel
import com.example.data.model.RadioChannel
import com.example.data.model.ZoneDocument
import com.example.domain.AnalogRadioReferenceDirectory
import com.example.domain.DetectedBleRadio
import com.example.domain.HardwareProgrammerManager
import com.example.domain.RadioCatalog
import com.example.domain.RepeaterOffsetCalculator
import com.example.domain.classSupportedRadioModel
import com.example.ui.theme.AmberVfoPrimary
import com.example.ui.theme.CyanRepeaterSecondary
import com.example.ui.theme.FrsFallbackYellow
import com.example.ui.theme.RxSafeGreen
import com.example.ui.viewmodel.LocalSavedCodeplugSummary
import com.example.ui.components.RadioListDrag
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.example.ui.components.RadioBrandModelDropdowns
import java.util.Locale
import kotlin.math.abs

@Composable
fun CodeplugTabScreen(
    programmerManager: HardwareProgrammerManager,
    isCodeplugLoaded: Boolean,
    codeplugTitle: String,
    channels: List<RadioChannel>,
    selectedRadio: classSupportedRadioModel,
    cloudSavedCodeplugs: List<ZoneDocument>,
    showRadioControls: Boolean,
    zoneNames: List<String>,
    selectedZone: Int,
    onSelectZone: (Int) -> Unit,
    isFlashing: Boolean,
    flashProgress: Float,
    flashLogs: List<String>,
    onSelectRadio: (classSupportedRadioModel) -> Unit,
    onToggleRxOnly: (Int, Boolean) -> Unit,
    onBulkRxOnly: (Set<Int>, Boolean) -> Unit,
    onSetScan: (Set<Int>, Boolean) -> Unit,
    onRenumber: (Int, Int) -> Boolean,
    onImportChannels: (List<RadioChannel>, Int) -> Unit,
    onMoveChannel: (Int, Int) -> Unit,
    onSaveOrAddChannel: (RadioChannel, Boolean) -> Unit,
    onDeleteChannel: (Int) -> Unit,
    onDeleteChannels: (Set<Int>) -> Unit,
    onEditChannels: (Set<Int>, String?, PowerLevel?) -> Unit,
    onDownloadFromRadio: (HardwareProgrammerManager, classSupportedRadioModel, String, String, String) -> Unit,
    onUploadToRadio: (HardwareProgrammerManager, String, String, String) -> Unit,
    onSaveLocal: (String) -> Unit,
    onListLocalCodeplugs: () -> List<LocalSavedCodeplugSummary>,
    onOpenLocalCodeplug: (LocalSavedCodeplugSummary) -> Unit,
    onOpenCloudCodeplug: (ZoneDocument) -> Unit,
    onSaveCloudCodeplug: (String, String, String, String) -> Unit,
    onOpenRemoteShareCode: (String) -> Unit
) {
    val context = LocalContext.current

    var showDownloadRadioDialog by remember { mutableStateOf(false) }
    var showUploadRadioDialog by remember { mutableStateOf(false) }
    var showSaveCodeplugDialog by remember { mutableStateOf(false) }
    var showOpenCodeplugDialog by remember { mutableStateOf(false) }

    var editingChannel by remember { mutableStateOf<RadioChannel?>(null) }
    var isAddingNewChannel by remember { mutableStateOf(false) }

    val supportsZones = selectedRadio.zoneSize > 0
    var addMenu by remember { mutableStateOf(false) }
    var showReference by remember { mutableStateOf(false) }
    var slotToEdit by remember { mutableStateOf<Int?>(null) }
    var showZones by remember { mutableStateOf(false) }
    val visibleChannels = if (!supportsZones || selectedZone == 0) channels else channels.filter {
        it.number in selectedRadio.slotsForZone(selectedZone)
    }
    var selectedNumbers by remember(channels, selectedZone) { mutableStateOf(emptySet<Int>()) }
    if (showReference) {
        RadioReferenceHierarchicalBrowser(selectedRadio, { showReference = false }, null) { imported, _ ->
            onImportChannels(imported, selectedZone)
            showReference = false
        }
        return
    }
    slotToEdit?.let { slot ->
        var input by remember(slot) { mutableStateOf(slot.toString()) }
        var failed by remember(slot) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { slotToEdit = null },
            title = { Text("Move channel $slot") },
            text = {
                Column {
                    OutlinedTextField(input, { input = it.filter(Char::isDigit).take(4); failed = false },
                        label = { Text("Destination slot") }, singleLine = true, isError = failed)
                    if (failed) Text("Invalid slot or no room to shift occupied slots upward.")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (onRenumber(slot, input.toIntOrNull() ?: 0)) slotToEdit = null else failed = true
                }) { Text("Move") }
            },
            dismissButton = { TextButton(onClick = { slotToEdit = null }) { Text("Cancel") } }
        )
    }
    val channelListState = rememberLazyListState()
    val channelDrag = remember(channelListState) { RadioListDrag(channelListState) }
    androidx.compose.runtime.LaunchedEffect(selectedZone) { channelListState.scrollToItem(0) }
    fun moveVisibleChannel(from: Int, to: Int) {
        val source = channels.indexOfFirst { it.number == visibleChannels[from].number }
        val target = channels.indexOfFirst { it.number == visibleChannels[to].number }
        onMoveChannel(source, target)
    }
    var showDeleteSelection by remember { mutableStateOf(false) }
    var showEditSelection by remember { mutableStateOf(false) }
    if(showDeleteSelection) {
        AlertDialog(onDismissRequest={showDeleteSelection=false},
            title={Text("Delete ${selectedNumbers.size} channels?")},
            text={Text("This removes the selected channels from the working codeplug. The radio changes only after Upload.")},
            confirmButton={TextButton(onClick={onDeleteChannels(selectedNumbers);selectedNumbers=emptySet();showDeleteSelection=false}){Text("Delete")}},
            dismissButton={TextButton(onClick={showDeleteSelection=false}){Text("Cancel")}})
    }
    if(showEditSelection) {
        var modeChoice by remember { mutableStateOf("Keep current") }
        var powerChoice by remember { mutableStateOf("Keep current") }
        AlertDialog(onDismissRequest={showEditSelection=false},
            title={Text("Edit ${selectedNumbers.size} channels")},
            text={Column {
                com.example.ui.components.ChoiceDropdown("Mode",listOf("Keep current","FM","NFM"),modeChoice,{modeChoice=it})
                com.example.ui.components.ChoiceDropdown("Power",listOf("Keep current","LOW","HIGH"),powerChoice,{powerChoice=it})
                Text("Names, receive/transmit frequencies and tones stay unchanged.")
            }},
            confirmButton={TextButton(onClick={
                onEditChannels(selectedNumbers,modeChoice.takeUnless{it=="Keep current"},powerChoice.takeUnless{it=="Keep current"}?.let{PowerLevel.valueOf(it)})
                selectedNumbers=emptySet();showEditSelection=false
            }){Text("Apply")}},
            dismissButton={TextButton(onClick={showEditSelection=false}){Text("Cancel")}})
    }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("codeplug_tab_screen")
    ) {
        // Leave the channel list room once a codeplug is open.
        if (showRadioControls) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Brand ➔ Model Dropdown Hierarchy Selector
                    RadioBrandModelDropdowns(
                        selectedRadio = selectedRadio,
                        onSelectRadio = onSelectRadio
                    )

                    // Quick Action Buttons (Download, Upload, Save Codeplug)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { showDownloadRadioDialog = true },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .testTag("dashboard_download_radio_btn")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Download",
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        OutlinedButton(
                            onClick = { showUploadRadioDialog = true },
                            enabled = channels.isNotEmpty(),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .testTag("dashboard_upload_radio_btn")
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Upload",
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Button(
                            onClick = { showSaveCodeplugDialog = true },
                            enabled = isCodeplugLoaded || channels.isNotEmpty(),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .testTag("dashboard_save_codeplug_btn")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Save",
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }

        // 2. MAIN CONTENT: DEFAULT BLANK SCREEN OR SIMPLE CODEPLUG CHANNEL LIST
        if (!isCodeplugLoaded && channels.isEmpty()) {
            // Default Blank Screen with Download from Radio or Open Saved Codeplug
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Radio,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "No Active Codeplug Loaded",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Download a codeplug directly from your radio via USB OTG or Bluetooth, open a saved codeplug, or append a Channel Bank from Tab 1.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { showDownloadRadioDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("blank_download_from_radio_btn")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Download from Radio")
                    }

                    OutlinedButton(
                        onClick = { showOpenCodeplugDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("blank_open_saved_codeplug_btn")
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Saved Codeplug")
                    }
                }
            }
        } else {
            // Codeplug Header Strip (Radio Model + Open / Add Channel)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = codeplugTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${channels.size} / ${selectedRadio.maxChannels} channels",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (supportsZones) {
                    Box {
                        TextButton(onClick = { showZones = true }) {
                            Text(if (selectedZone == 0) "All zones ▾" else "Zone $selectedZone ▾", style = MaterialTheme.typography.labelSmall)
                        }
                        DropdownMenu(expanded = showZones, onDismissRequest = { showZones = false }) {
                            (0..10).forEach { zone ->
                                val name = if (zone == 0) "All zones" else
                                    zoneNames.getOrNull(zone - 1).orEmpty().let {
                                        if (it.isBlank()) "Zone $zone" else "Zone $zone · $it"
                                    }
                                DropdownMenuItem(text = { Text(name) }, onClick = {
                                    onSelectZone(zone)
                                    showZones = false
                                })
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = { showOpenCodeplugDialog = true },
                        modifier = Modifier.testTag("header_open_codeplug_btn")
                    ) {
                        Text("Open")
                    }
                    Box {
                        TextButton(onClick = { addMenu = true }, modifier = Modifier.testTag("codeplug_add_channel_btn")) { Text("+ Add Ch") }
                        DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                            DropdownMenuItem(text = { Text("Add manually") }, onClick = {
                                addMenu = false
                            isAddingNewChannel = true
                            editingChannel = RadioChannel(
                                number = if (supportsZones) {
                                    val range = selectedRadio.slotsForZone(selectedZone)
                                    range.firstOrNull { slot -> channels.none { it.number == slot } } ?: return@DropdownMenuItem
                                } else channels.size + 1,
                                name = "CH ${(channels.size + 1).toString().padStart(2, '0')}",
                                rxFreqHz = 146520000L,
                                txFreqHz = 146520000L,
                                duplex = DuplexMode.SIMPLEX,
                                offsetHz = 0L,
                                rxTone = "None",
                                txTone = "None",
                                mode = "FM",
                                power = PowerLevel.HIGH,
                                rxOnly = false
                            )
                            })
                            DropdownMenuItem(text = { Text("Import RadioReference") }, onClick = {
                                addMenu = false
                                showReference = true
                            })
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked=visibleChannels.isNotEmpty() && selectedNumbers.size==visibleChannels.size,onCheckedChange={ checked ->
                    selectedNumbers=if(checked) visibleChannels.map{it.number}.toSet() else emptySet()
                })
                Text(
                    if (selectedNumbers.isEmpty()) "Select all" else "${selectedNumbers.size} selected",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f)
                )
                if (selectedNumbers.isNotEmpty()) {
                    TextButton(onClick = { showEditSelection = true }, enabled = !isFlashing) { Text("Edit") }
                    TextButton(onClick = { showDeleteSelection = true }, enabled = !isFlashing) { Text("Delete") }
                }
                val visibleSlots = visibleChannels.map { it.number }.toSet()
                val allScan = visibleChannels.isNotEmpty() && visibleChannels.all { it.scanIncluded != false }
                TextButton(
                    onClick = { onSetScan(visibleSlots, !allScan) },
                    enabled = visibleChannels.isNotEmpty() && !isFlashing,
                    modifier = Modifier.width(64.dp),
                    contentPadding = PaddingValues(0.dp)
                ) { Text(if (allScan) "Clear scan" else "Scan all", style = MaterialTheme.typography.labelSmall) }
                TextButton(modifier = Modifier.width(56.dp), contentPadding = PaddingValues(0.dp), enabled = visibleChannels.isNotEmpty() && !isFlashing, onClick = {
                    onBulkRxOnly(visibleChannels.map { it.number }.toSet(), visibleChannels.any { !it.rxOnly })
                }) { Text(if (visibleChannels.isNotEmpty() && visibleChannels.all { it.rxOnly }) "Clear RX" else "RX all",
                    style = MaterialTheme.typography.labelSmall) }
            }
            // Simple, clean Channel List with Drag-and-Drop & Single RxOnly Checkbox
            if (visibleChannels.isEmpty() && selectedZone != 0) {
                Text("No channels in this zone.", modifier = Modifier.padding(16.dp))
            }
            LazyColumn(
                state = channelListState,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("codeplug_channels_list"),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(
                    items = visibleChannels,
                    key = { _, ch -> "cp_${ch.number}_${ch.name}_${ch.rxFreqHz}" }
                ) { index, channel ->
                    val move = androidx.compose.runtime.rememberUpdatedState<(Int) -> Unit> { target ->
                        moveVisibleChannel(index, target)
                    }
                    Surface(
                        modifier = Modifier
                            .animateItem()
                            .then(channelDrag.row(index, MaterialTheme.colorScheme.primary))
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                isAddingNewChannel = false
                                editingChannel = channel
                            }
                            .testTag("codeplug_channel_row_${channel.number}"),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outline,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked=channel.number in selectedNumbers,
                                onCheckedChange={checked ->
                                    selectedNumbers=if(checked) selectedNumbers+channel.number else selectedNumbers-channel.number
                                },
                                enabled=!isFlashing,
                                modifier=Modifier.size(36.dp).testTag("select_channel_${channel.number}")
                            )
                            // Drag Handle + Quick Up/Down Reorder
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .then(channelDrag.handle(index, visibleChannels.indices) { move.value }),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DragHandle,
                                    contentDescription = "Drag to reorder",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Channel Slot Number
                            Text(
                                text = channel.number.toString().padStart(2, '0'),
                                style = MaterialTheme.typography.labelLarge,
                                color = AmberVfoPrimary,
                                modifier = Modifier.width(36.dp).clickable(enabled = !isFlashing) { slotToEdit = channel.number }
                            )

                            // Simple Channel Name & RX Frequency (Click row to see/edit PL, DPL, Offset, etc.)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = channel.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val subtleDetail = if (channel.rxOnly && !selectedRadio.supportsNativeRxOnly) {
                                    "${channel.rxFreqMhzString} MHz (TX -> FRS1 0.5W)"
                                } else {
                                    "${channel.rxFreqMhzString} MHz"
                                }
                                Text(
                                    text = subtleDetail,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (channel.rxOnly && !selectedRadio.supportsNativeRxOnly) {
                                        FrsFallbackYellow
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }

                            Column(Modifier.width(64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Scan", style = MaterialTheme.typography.labelSmall)
                                Checkbox(
                                    checked = channel.scanIncluded != false,
                                    onCheckedChange = { onSetScan(setOf(channel.number), it) },
                                    enabled = !isFlashing,
                                    modifier = Modifier.size(36.dp).testTag("scan_checkbox_${channel.number}")
                                )
                            }
                            Column(Modifier.width(56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Rx Only", style = MaterialTheme.typography.labelSmall)
                                Checkbox(
                                    checked = channel.rxOnly,
                                    onCheckedChange = { onToggleRxOnly(channel.number, it) },
                                    enabled = !isFlashing,
                                    modifier = Modifier.size(36.dp).testTag("rx_only_checkbox_${channel.number}")
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Channel Specific Details Modal (Opened when clicking any channel in the codeplug)
    if (editingChannel != null) {
        ChannelDetailEditorModal(
            initialChannel = editingChannel!!,
            isNew = isAddingNewChannel,
            selectedRadio = selectedRadio,
            onDismiss = { editingChannel = null },
            onDelete = if (!isAddingNewChannel) {
                {
                    val num = editingChannel!!.number
                    editingChannel = null
                    onDeleteChannel(num)
                }
            } else null,
            onSave = { saved ->
                val wasNew = isAddingNewChannel
                editingChannel = null
                onSaveOrAddChannel(saved, wasNew)
            }
        )
    }

    // Download from Radio Modal
    if (showDownloadRadioDialog) {
        HardwareTransferModal(
            isDownload = true,
            selectedRadio = selectedRadio,
            isFlashing = isFlashing,
            flashProgress = flashProgress,
            flashLogs = flashLogs,
            programmerManager = programmerManager,
            onSelectRadio = onSelectRadio,
            onExecute = { radio, transport, bleAddr, usbName ->
                onDownloadFromRadio(programmerManager, radio, transport, bleAddr, usbName)
            },
            onDismiss = { showDownloadRadioDialog = false }
        )
    }

    // Upload to Radio Modal
    if (showUploadRadioDialog) {
        HardwareTransferModal(
            isDownload = false,
            selectedRadio = selectedRadio,
            isFlashing = isFlashing,
            flashProgress = flashProgress,
            flashLogs = flashLogs,
            programmerManager = programmerManager,
            onSelectRadio = onSelectRadio,
            onExecute = { _, transport, bleAddr, usbName ->
                onUploadToRadio(programmerManager, transport, bleAddr, usbName)
            },
            onDismiss = { showUploadRadioDialog = false }
        )
    }

    // Save Codeplug Modal (Local and Cloud options when selected)
    if (showSaveCodeplugDialog) {
        SaveCodeplugModal(
            initialName = codeplugTitle,
            onDismiss = { showSaveCodeplugDialog = false },
            onSaveLocal = { name ->
                showSaveCodeplugDialog = false
                onSaveLocal(name)
            },
            onSaveCloud = { name, shareCode, callsign, notes ->
                showSaveCodeplugDialog = false
                onSaveCloudCodeplug(name, shareCode, callsign, notes)
            }
        )
    }

    // Open Saved Codeplug Modal (Local, Cloud, or Remote Share Code)
    if (showOpenCodeplugDialog) {
        val localCodeplugs = remember { onListLocalCodeplugs() }
        OpenSavedCodeplugModal(
            localCodeplugs = localCodeplugs,
            cloudCodeplugs = cloudSavedCodeplugs,
            onDismiss = { showOpenCodeplugDialog = false },
            onSelectLocal = { summary ->
                showOpenCodeplugDialog = false
                onOpenLocalCodeplug(summary)
            },
            onSelectCloud = { doc ->
                showOpenCodeplugDialog = false
                onOpenCloudCodeplug(doc)
            },
            onFetchRemoteShareCode = { code ->
                showOpenCodeplugDialog = false
                onOpenRemoteShareCode(code)
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChannelDetailEditorModal(
    initialChannel: RadioChannel,
    isNew: Boolean,
    selectedRadio: classSupportedRadioModel,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onSave: (RadioChannel) -> Unit
) {
    var name by remember { mutableStateOf(initialChannel.name) }
    var rxMhzText by remember { mutableStateOf(initialChannel.rxFreqMhzString) }
    var duplex by remember { mutableStateOf(initialChannel.duplex) }
    var offsetMhzText by remember { mutableStateOf(initialChannel.offsetMhzString) }
    var txMhzText by remember { mutableStateOf(initialChannel.txFreqMhzString) }
    var toneType by remember {
        mutableStateOf(
            when {
                initialChannel.rxTone.startsWith("D") -> "DPL (DCS)"
                initialChannel.rxTone != "None" -> "PL (CTCSS)"
                else -> "None"
            }
        )
    }
    var selectedPl by remember {
        mutableStateOf(if (!initialChannel.rxTone.startsWith("D") && initialChannel.rxTone != "None") initialChannel.rxTone else "131.8")
    }
    var selectedDpl by remember {
        mutableStateOf(if (initialChannel.rxTone.startsWith("D")) initialChannel.rxTone else "D023N")
    }
    var mode by remember { mutableStateOf(initialChannel.mode) }
    var power by remember { mutableStateOf(initialChannel.power) }
    var rxOnly by remember { mutableStateOf(initialChannel.rxOnly) }
    var notes by remember { mutableStateOf(initialChannel.notes) }
    var rxToneChoice by remember { mutableStateOf(initialChannel.rxTone) }
    var txToneChoice by remember { mutableStateOf(initialChannel.txTone) }
    var scanIncluded by remember { mutableStateOf(initialChannel.scanIncluded) }
    var busyLockout by remember { mutableStateOf(initialChannel.busyChannelLockout) }
    var fhssPrivacy by remember { mutableStateOf(initialChannel.fhssPrivacy) }
    var pttId by remember { mutableStateOf(initialChannel.pttId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (isNew) "Add Channel" else "Channel #${initialChannel.number} Settings")
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Channel",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(if(selectedRadio.id == "baofeng_uv32" || selectedRadio.model.contains("UV-5R Mini")) 12 else 16) },
                    label = { Text("Channel Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("modal_channel_name_input")
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = rxMhzText,
                        onValueChange = { newRx ->
                            rxMhzText = newRx
                            val mhz = newRx.toDoubleOrNull()
                            if (mhz != null && !rxOnly) {
                                val calc = RepeaterOffsetCalculator.calculateStandardOffset(RadioChannel.mhzToHz(mhz))
                                duplex = calc.duplex
                                offsetMhzText = String.format(Locale.US, "%.3f", calc.offsetHz / 1_000_000.0)
                                txMhzText = String.format(Locale.US, "%.4f", calc.txFreqHz / 1_000_000.0)
                            }
                        },
                        label = { Text("RX Frequency (MHz)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("modal_channel_rx_input")
                    )

                    AssistChip(
                        onClick = {
                            val mhz = rxMhzText.toDoubleOrNull() ?: 146.520
                            val calc = RepeaterOffsetCalculator.calculateStandardOffset(RadioChannel.mhzToHz(mhz))
                            duplex = calc.duplex
                            offsetMhzText = String.format(Locale.US, "%.3f", calc.offsetHz / 1_000_000.0)
                            txMhzText = String.format(Locale.US, "%.4f", calc.txFreqHz / 1_000_000.0)
                        },
                        label = { Text("Auto Offset") }
                    )
                }

                // Single RxOnly Checkbox inside modal as well
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable {
                            rxOnly = !rxOnly
                            if (rxOnly) {
                                if (selectedRadio.supportsNativeRxOnly) {
                                    duplex = DuplexMode.OFF
                                } else {
                                    duplex = DuplexMode.SPLIT
                                    txMhzText = "462.5625"
                                    power = PowerLevel.LOW
                                }
                            } else {
                                val mhz = rxMhzText.toDoubleOrNull() ?: 146.520
                                val calc = RepeaterOffsetCalculator.calculateStandardOffset(RadioChannel.mhzToHz(mhz))
                                duplex = calc.duplex
                                offsetMhzText = String.format(Locale.US, "%.3f", calc.offsetHz / 1_000_000.0)
                                txMhzText = String.format(Locale.US, "%.4f", calc.txFreqHz / 1_000_000.0)
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Checkbox(
                        checked = rxOnly,
                        onCheckedChange = { checked ->
                            rxOnly = checked
                            if (checked) {
                                if (selectedRadio.supportsNativeRxOnly) {
                                    duplex = DuplexMode.OFF
                                } else {
                                    duplex = DuplexMode.SPLIT
                                    txMhzText = "462.5625"
                                    power = PowerLevel.LOW
                                }
                            }
                        }
                    )
                    Column {
                        Text(
                            text = "RxOnly",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (selectedRadio.supportsNativeRxOnly) {
                                "Disables transmit (Duplex = OFF) on ${selectedRadio.model}"
                            } else {
                                "${selectedRadio.model} has no native RX-only: programs TX to FRS1 (462.5625 MHz) @ Low Power"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Offset & Duplex Section
                Text("Duplex / Repeater Offset", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DuplexMode.entries.forEach { modeOpt ->
                        FilterChip(
                            selected = duplex == modeOpt,
                            onClick = { duplex = modeOpt },
                            label = { Text(modeOpt.display) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = offsetMhzText,
                        onValueChange = { offsetMhzText = it },
                        label = { Text("Offset (MHz)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = txMhzText,
                        onValueChange = { txMhzText = it },
                        label = { Text("TX Frequency (MHz)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                val toneOptions = remember {
                    (AnalogRadioReferenceDirectory.standardCtcssTones+
                     AnalogRadioReferenceDirectory.standardDcsCodes+
                     AnalogRadioReferenceDirectory.standardDcsCodes.filter{it.startsWith("D")}.map{it.dropLast(1)+"R"}).distinct()
                }
                com.example.ui.components.ChoiceDropdown("Receive tone",toneOptions,rxToneChoice,{rxToneChoice=it})
                com.example.ui.components.ChoiceDropdown("Transmit tone",toneOptions,txToneChoice,{txToneChoice=it})
                if(selectedRadio.id == "baofeng_uv32" || selectedRadio.model.contains("UV-5R Mini")) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Checkbox(checked=scanIncluded ?: true,onCheckedChange={scanIncluded=it})
                        Text("Include in scan")
                    }
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Checkbox(checked=busyLockout ?: false,onCheckedChange={busyLockout=it})
                        Text("Busy-channel lockout")
                    }
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Checkbox(checked=fhssPrivacy ?: false,onCheckedChange={fhssPrivacy=it})
                        Text("FHSS / analog privacy")
                    }
                    Text("Analog privacy for compatible radios.", style = MaterialTheme.typography.bodySmall)
                    val pttChoices=listOf("Keep radio value","Off","Begin","End","Both")
                    val currentPtt=pttId?.takeIf{it in 0..3}?.let{pttChoices[it+1]} ?: pttChoices[0]
                    com.example.ui.components.ChoiceDropdown("PTT ID",pttChoices,currentPtt,{pttId=pttChoices.indexOf(it).takeIf{it>0}?.minus(1)})
                }

                // Power & Mode (Radio-dependent options)
                Text("Transmit Power & Bandwidth (${selectedRadio.model})", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PowerLevel.entries.forEach { p ->
                        FilterChip(
                            selected = power == p,
                            onClick = { power = p },
                            label = { Text(if (selectedRadio.id == "baofeng_uv32") when (p) {
                                PowerLevel.LOW -> "2 W Low"
                                PowerLevel.MID -> "5 W Mid"
                                PowerLevel.HIGH -> "10 W High"
                            } else p.wattsLabel) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("FM", "NFM", "AM").forEach { m ->
                        FilterChip(
                            selected = mode == m,
                            onClick = { mode = m },
                            label = { Text(if (m == "NFM") "NFM (12.5kHz)" else "$m (25kHz)") }
                        )
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Comment / Notes") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val rxHz = RadioChannel.mhzToHz(rxMhzText.toDoubleOrNull() ?: 146.520)
                    val offsetHz = RadioChannel.mhzToHz(offsetMhzText.toDoubleOrNull() ?: 0.0)
                    val computedTxHz = when {
                        rxOnly && !selectedRadio.supportsNativeRxOnly -> RadioChannel.FRS1_FALLBACK_HZ
                        duplex == DuplexMode.PLUS -> rxHz + offsetHz
                        duplex == DuplexMode.MINUS -> rxHz - offsetHz
                        duplex == DuplexMode.SPLIT -> RadioChannel.mhzToHz(txMhzText.toDoubleOrNull() ?: (rxHz / 1_000_000.0))
                        else -> rxHz
                    }
                    val finalTone = when (toneType) {
                        "PL (CTCSS)" -> selectedPl
                        "DPL (DCS)" -> selectedDpl
                        else -> "None"
                    }
                    val finalDuplex = when {
                        rxOnly && selectedRadio.supportsNativeRxOnly -> DuplexMode.OFF
                        rxOnly && !selectedRadio.supportsNativeRxOnly -> DuplexMode.SPLIT
                        else -> duplex
                    }
                    onSave(
                        RadioChannel(
                            number = initialChannel.number,
                            name = name.ifBlank { "CH ${initialChannel.number}" },
                            rxFreqHz = rxHz,
                            txFreqHz = computedTxHz,
                            duplex = finalDuplex,
                            offsetHz = abs(computedTxHz - rxHz),
                            rxTone = rxToneChoice,
                            txTone = if (rxOnly) "None" else txToneChoice,
                            mode = mode,
                            power = if (rxOnly) PowerLevel.LOW else power,
                            rxOnly = rxOnly,
                            notes = notes,
                            scanIncluded = scanIncluded,
                            busyChannelLockout = busyLockout,
                            fhssPrivacy = fhssPrivacy,
                            pttId = pttId
                        )
                    )
                },
                modifier = Modifier.testTag("modal_save_channel_btn")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun HardwareTransferModal(
    isDownload: Boolean,
    selectedRadio: classSupportedRadioModel,
    isFlashing: Boolean,
    flashProgress: Float,
    flashLogs: List<String>,
    programmerManager: HardwareProgrammerManager,
    onSelectRadio: (classSupportedRadioModel) -> Unit,
    onExecute: (classSupportedRadioModel, String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var transport by remember(selectedRadio.id) { mutableStateOf("Bluetooth LE") }
    var usbCables by remember { mutableStateOf(programmerManager.scanUsbOtgDevices()) }
    var selectedUsbDeviceName by remember { mutableStateOf(usbCables.firstOrNull()?.deviceName ?: "") }
    val bleDevices = remember { mutableStateListOf<DetectedBleRadio>() }
    var selectedBleAddress by remember { mutableStateOf("") }
    var stopBleScanRef by remember { mutableStateOf<() -> Unit>({}) }
    var bleStatus by remember { mutableStateOf("Find nearby radios, then choose the one you want to use.") }

    val blePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) {
            stopBleScanRef()
            bleDevices.clear()
            selectedBleAddress = ""
            bleStatus = "Choose your radio from the list."
            stopBleScanRef = programmerManager.startBleScan(
                onDeviceFound = { dev ->
                    if (bleDevices.none { it.address == dev.address }) {
                        bleDevices.add(dev)
                    }
                },
                onError = { err -> bleStatus = err }
            )
        } else {
            bleStatus = "Bluetooth permission required."
        }
    }

    AlertDialog(
        onDismissRequest = {
            stopBleScanRef()
            onDismiss()
        },
        title = {
            Text(if (isDownload) "Download Codeplug from Radio" else "Upload Codeplug to Radio")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                com.example.ui.components.ChoiceDropdown(
                    label = "Radio model",
                    options = RadioCatalog.allRadios.map { it.model },
                    selected = selectedRadio.model,
                    onSelect = { model ->
                        stopBleScanRef()
                        bleDevices.clear()
                        selectedBleAddress = ""
                        onSelectRadio(RadioCatalog.allRadios.first { it.model == model })
                    }
                )

                Text(
                    text = selectedRadio.protocolNotes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (selectedRadio.id == "baofeng_uv32") {
                    Text("For Bluetooth, turn on Wireless CPS and close Ola. Download from this UV-32 using the same connection before uploading.")
                }
                if (selectedRadio.id != "radtel_rt950_pro" || isDownload) {
                    val connections = listOf("Bluetooth", "USB cable")
                    com.example.ui.components.ChoiceDropdown(
                        label = "Connection",
                        options = connections,
                        selected = if (transport == "Bluetooth LE") connections[0] else connections[1],
                        onSelect = { connection ->
                            stopBleScanRef()
                            transport = if (connection == connections[0]) "Bluetooth LE" else "USB OTG Serial"
                            if (transport == "USB OTG Serial") {
                                usbCables = programmerManager.scanUsbOtgDevices()
                                selectedUsbDeviceName = usbCables.firstOrNull()?.deviceName.orEmpty()
                            }
                        }
                    )
                }

                if (transport == "Bluetooth LE") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(bleStatus, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        TextButton(
                            onClick = {
                                val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
                                } else {
                                    arrayOf(Manifest.permission.BLUETOOTH)
                                }
                                blePermissionLauncher.launch(perms)
                            }
                        ) {
                            Text("Find radios")
                        }
                    }
                    bleDevices.sortedBy { device ->
                        !device.name.trim().equals("walkie-talkie", ignoreCase = true)
                    }.forEach { d ->
                        val isSelected = selectedBleAddress == d.address
                        Surface(
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedBleAddress = d.address }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = d.name,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else RxSafeGreen
                                    )
                                    Text(
                                        text = "${d.address} • ${d.rssi} dBm",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isSelected) {
                                    Text("SELECTED", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (usbCables.isEmpty()) "No USB OTG cable detected." else "${usbCables.size} USB OTG adapter(s) ready.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = {
                                usbCables = programmerManager.scanUsbOtgDevices()
                                if (usbCables.none { it.deviceName == selectedUsbDeviceName }) {
                                    selectedUsbDeviceName = usbCables.firstOrNull()?.deviceName ?: ""
                                }
                            }
                        ) {
                            Text("Refresh USB")
                        }
                    }
                    usbCables.forEach { c ->
                        val isSelected = selectedUsbDeviceName == c.deviceName
                        Surface(
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedUsbDeviceName = c.deviceName
                                    if (!c.hasPermission) {
                                        programmerManager.requestUsbPermission(c.deviceName)
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(c.chipsetName, style = MaterialTheme.typography.labelLarge, color = RxSafeGreen)
                                    Text(
                                        text = if (c.hasPermission) "USB Permission Granted" else "Tap to Grant USB Permission",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                }

                if (isFlashing || flashProgress > 0f) {
                    LinearProgressIndicator(
                        progress = { flashProgress },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (flashLogs.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        flashLogs.takeLast(8).forEach { line ->
                            Text(
                                text = line,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (line.startsWith("[ERROR]")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    stopBleScanRef()
                    onExecute(selectedRadio, transport, selectedBleAddress, selectedUsbDeviceName)
                },
                enabled = !isFlashing &&
                    (selectedRadio.id != "radtel_rt950_pro" || isDownload) &&
                    (selectedRadio.id != "baofeng_uv5_mini" || isDownload || transport == "Bluetooth LE") &&
                    (if (transport == "Bluetooth LE") selectedBleAddress.isNotBlank() else selectedUsbDeviceName.isNotBlank()),
                modifier = Modifier.testTag("confirm_hardware_transfer_btn")
            ) {
                Text(if (isDownload) "Download from Radio" else "Upload to Radio")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    stopBleScanRef()
                    onDismiss()
                }
            ) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun SaveCodeplugModal(
    initialName: String,
    onDismiss: () -> Unit,
    onSaveLocal: (String) -> Unit,
    onSaveCloud: (String, String, String, String) -> Unit
) {
    var saveDestination by remember { mutableStateOf("LOCAL") }
    var name by remember { mutableStateOf(initialName) }
    var shareCode by remember { mutableStateOf("") }
    var callsign by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save Codeplug") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = saveDestination == "LOCAL",
                        onClick = { saveDestination = "LOCAL" },
                        leadingIcon = { Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text("Local Storage") },
                        modifier = Modifier.testTag("save_option_local")
                    )
                    FilterChip(
                        selected = saveDestination == "CLOUD",
                        onClick = { saveDestination = "CLOUD" },
                        leadingIcon = { Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text("Cloud & Remote Share") },
                        modifier = Modifier.testTag("save_option_cloud")
                    )
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Codeplug Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (saveDestination == "CLOUD") {
                    Text(
                        text = "Optional: Set a Share Code (e.g. PLYM-FIRE-26) so others can remotely pull or update their codeplug.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = shareCode,
                        onValueChange = { shareCode = it.uppercase() },
                        label = { Text("Remote Share Code (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = callsign,
                        onValueChange = { callsign = it },
                        label = { Text("Author Callsign / Unit") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Release / Update Notes") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (saveDestination == "LOCAL") {
                        onSaveLocal(name)
                    } else {
                        onSaveCloud(name, shareCode, callsign, notes)
                    }
                },
                modifier = Modifier.testTag("confirm_save_codeplug_btn")
            ) {
                Text(if (saveDestination == "LOCAL") "Save Locally" else "Save to Cloud")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun OpenSavedCodeplugModal(
    localCodeplugs: List<LocalSavedCodeplugSummary>,
    cloudCodeplugs: List<ZoneDocument>,
    onDismiss: () -> Unit,
    onSelectLocal: (LocalSavedCodeplugSummary) -> Unit,
    onSelectCloud: (ZoneDocument) -> Unit,
    onFetchRemoteShareCode: (String) -> Unit
) {
    var tab by remember { mutableStateOf("LOCAL") }
    var shareCodeInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Open Saved Codeplug") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = tab == "LOCAL",
                        onClick = { tab = "LOCAL" },
                        label = { Text("Local (${localCodeplugs.size})") }
                    )
                    FilterChip(
                        selected = tab == "CLOUD",
                        onClick = { tab = "CLOUD" },
                        label = { Text("Cloud (${cloudCodeplugs.size})") }
                    )
                    FilterChip(
                        selected = tab == "REMOTE_CODE",
                        onClick = { tab = "REMOTE_CODE" },
                        label = { Text("Share Code") }
                    )
                }

                when (tab) {
                    "LOCAL" -> {
                        if (localCodeplugs.isEmpty()) {
                            Text(
                                text = "No local codeplugs saved on this device yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(localCodeplugs, key = { it.id }) { cp ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onSelectLocal(cp) }
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(cp.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                            Text("${cp.channelCount} channels • ${cp.radioModel}", style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    "CLOUD" -> {
                        if (cloudCodeplugs.isEmpty()) {
                            Text(
                                text = "No cloud codeplugs saved to your account yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(cloudCodeplugs, key = { it.id }) { doc ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onSelectCloud(doc) }
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(doc.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = "${doc.channelSpecs.size} channels • ${doc.targetRadioModel}",
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    "REMOTE_CODE" -> {
                        Text(
                            text = "Enter a shared codeplug ID (e.g. PLYM-FIRE-26) to pull or update from another operator.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = shareCodeInput,
                            onValueChange = { shareCodeInput = it.uppercase() },
                            label = { Text("Remote Share Code") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = { onFetchRemoteShareCode(shareCodeInput) },
                            enabled = shareCodeInput.length >= 3,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open Remote Codeplug")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
