package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.DuplexMode
import com.example.data.model.PowerLevel
import com.example.data.model.RadioChannel
import com.example.data.model.ZoneDocument
import com.example.domain.AnalogCategoryGroup
import com.example.domain.AnalogRadioReferenceDirectory
import com.example.domain.CountyDirectory
import com.example.domain.StateDirectory
import com.example.domain.classSupportedRadioModel

@Composable
fun ChannelBanksTabScreen(
    savedBanks: List<ZoneDocument>,
    selectedRadio: classSupportedRadioModel,
    onSaveBank: (ZoneDocument?, String, String, List<RadioChannel>) -> Unit,
    onDeleteBank: (ZoneDocument) -> Unit,
    onAddBankToCodeplug: (ZoneDocument) -> Unit
) {
    // Sub-navigation inside Tab 1:
    // 1. Saved Banks List (Default)
    // 2. Bank Editor (New Bank or Edit Existing Bank)
    // 3. RadioReference Hierarchical Importer (National + All 50 States > County > Analog Categories)
    var isEditingBank by remember { mutableStateOf(false) }
    var editingBankDoc by remember { mutableStateOf<ZoneDocument?>(null) }
    var bankNameInput by remember { mutableStateOf("") }
    var bankRegionSummary by remember { mutableStateOf("Analog Custom Bank") }
    val draftChannels = remember { mutableStateListOf<RadioChannel>() }

    var showCustomChannelModal by remember { mutableStateOf(false) }
    var editingDraftChannelIndex by remember { mutableStateOf<Int?>(null) }
    var showRadioReferenceBrowser by remember { mutableStateOf(false) }
    var importMessage by remember { mutableStateOf<String?>(null) }
    val importContext = androidx.compose.ui.platform.LocalContext.current
    val csvPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if(uri!=null) {
            try {
                val text=importContext.contentResolver.openInputStream(uri)?.bufferedReader()?.use{it.readText()} ?: error("Unable to read CSV")
                val result=com.example.domain.RadioReferenceCsv.parse(text)
                require(result.channels.isNotEmpty()) { "No supported analog channels found" }
                draftChannels.addAll(result.channels.mapIndexed { index,ch -> ch.copy(number=draftChannels.size+index+1) })
                if(bankNameInput.isBlank()) bankNameInput="Imported analog channels"
                bankRegionSummary="User-supplied conventional CSV"
                isEditingBank=true
                showRadioReferenceBrowser=false
                importMessage="Imported ${result.channels.size} analog channels; skipped ${result.skipped} digital or invalid rows. Alpha tags are preserved exactly."
            } catch(error:Exception) { importMessage=error.message ?: "CSV import failed" }
        }
    }
    if(importMessage!=null) {
        AlertDialog(onDismissRequest={importMessage=null},title={Text("CSV import")},text={Text(importMessage ?: "")},
            confirmButton={TextButton(onClick={importMessage=null}){Text("OK")}})
    }

    var selectedBankIdInList by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = showRadioReferenceBrowser || isEditingBank) {
        if (showRadioReferenceBrowser) {
            showRadioReferenceBrowser = false
        } else {
            isEditingBank = false
        }
    }

    when {
        showRadioReferenceBrowser -> {
            RadioReferenceHierarchicalBrowser(
                selectedRadio = selectedRadio,
                onBack = { showRadioReferenceBrowser = false },
                onImportCsv = { csvPicker.launch(arrayOf("text/*","application/csv","application/octet-stream")) },
                onImportSelectedChannels = { importedList, sourceLabel ->
                    val startNum = draftChannels.size + 1
                    importedList.forEachIndexed { idx, ch ->
                        draftChannels.add(ch.copy(number = startNum + idx))
                    }
                    if (bankNameInput.isBlank()) {
                        bankNameInput = sourceLabel
                    }
                    bankRegionSummary = sourceLabel
                    showRadioReferenceBrowser = false
                }
            )
        }

        isEditingBank -> {
            ChannelBankEditorView(
                isNew = editingBankDoc == null,
                bankName = bankNameInput,
                onBankNameChange = { bankNameInput = it },
                channels = draftChannels,
                onOpenAddCustomChannel = {
                    editingDraftChannelIndex = null
                    showCustomChannelModal = true
                },
                onOpenEditChannel = { index ->
                    editingDraftChannelIndex = index
                    showCustomChannelModal = true
                },
                onDeleteDraftChannel = { index ->
                    if (index in draftChannels.indices) {
                        draftChannels.removeAt(index)
                        val renumbered = draftChannels.mapIndexed { i, c -> c.copy(number = i + 1) }
                        draftChannels.clear()
                        draftChannels.addAll(renumbered)
                    }
                },
                onOpenRadioReferenceImport = {
                    showRadioReferenceBrowser = true
                },
                onCancel = { isEditingBank = false },
                onSave = {
                    val finalName = bankNameInput.trim().ifBlank { "Channel Bank ${savedBanks.size + 1}" }
                    onSaveBank(
                        editingBankDoc,
                        finalName,
                        bankRegionSummary,
                        draftChannels.toList()
                    )
                    isEditingBank = false
                }
            )
        }

        else -> {
            // Default view: Saved Channel Banks list (NO default banks!)
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("channel_banks_list"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Channel Banks",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Build reusable analog channel banks and append them to your radio codeplug.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = {
                                editingBankDoc = null
                                bankNameInput = ""
                                bankRegionSummary = "Custom Bank"
                                draftChannels.clear()
                                isEditingBank = true
                            },
                            modifier = Modifier.testTag("add_bank_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Bank")
                        }
                    }
                }

                if (savedBanks.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                                    .padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(42.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "No Saved Channel Banks Yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Tap 'Add Bank' to create a bank by adding custom frequencies or importing analog channels from RadioReference (National or State > County).",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        editingBankDoc = null
                                        bankNameInput = ""
                                        bankRegionSummary = "Custom Bank"
                                        draftChannels.clear()
                                        isEditingBank = true
                                    },
                                    modifier = Modifier.testTag("empty_state_add_bank_button")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Add Bank")
                                }
                            }
                        }
                    }
                } else {
                    items(savedBanks, key = { it.id }) { bank ->
                        val isSelected = selectedBankIdInList == bank.id
                        val bankChannels = remember(bank.channelSpecs) { bank.parsedChannels() }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedBankIdInList = if (isSelected) null else bank.id
                                }
                                .testTag("bank_item_${bank.name}"),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) {
                                    MaterialTheme.colorScheme.surfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .padding(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = bank.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${bankChannels.size} channels • ${bank.region}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(
                                        onClick = { onDeleteBank(bank) },
                                        modifier = Modifier.testTag("delete_bank_${bank.name}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Bank",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { onAddBankToCodeplug(bank) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("append_bank_to_codeplug_${bank.name}")
                                    ) {
                                        Icon(Icons.Default.PlaylistAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Add to Codeplug")
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            editingBankDoc = bank
                                            bankNameInput = bank.name
                                            bankRegionSummary = bank.region
                                            draftChannels.clear()
                                            draftChannels.addAll(bankChannels)
                                            isEditingBank = true
                                        },
                                        modifier = Modifier.testTag("edit_bank_${bank.name}")
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Edit List")
                                    }
                                }

                                if (isSelected && bankChannels.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    bankChannels.forEach { ch ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 2.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "${ch.number.toString().padStart(2, '0')}. ${ch.name}",
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Text(
                                                text = "${ch.rxFreqMhzString} MHz",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCustomChannelModal) {
        val editIndex = editingDraftChannelIndex
        val initialCh = if (editIndex != null && editIndex in draftChannels.indices) {
            draftChannels[editIndex]
        } else {
            RadioChannel(
                number = draftChannels.size + 1,
                name = "",
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
        }
        ChannelDetailEditorModal(
            initialChannel = initialCh,
            isNew = editIndex == null,
            selectedRadio = selectedRadio,
            onDismiss = { showCustomChannelModal = false },
            onSave = { savedCh ->
                showCustomChannelModal = false
                if (editIndex != null && editIndex in draftChannels.indices) {
                    draftChannels[editIndex] = savedCh
                } else {
                    draftChannels.add(savedCh.copy(number = draftChannels.size + 1))
                }
            }
        )
    }
}

@Composable
private fun ChannelBankEditorView(
    isNew: Boolean,
    bankName: String,
    onBankNameChange: (String) -> Unit,
    channels: List<RadioChannel>,
    onOpenAddCustomChannel: () -> Unit,
    onOpenEditChannel: (Int) -> Unit,
    onDeleteDraftChannel: (Int) -> Unit,
    onOpenRadioReferenceImport: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCancel) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = if (isNew) "New Channel Bank" else "Edit Channel Bank",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Button(
                onClick = onSave,
                enabled = bankName.isNotBlank() || channels.isNotEmpty(),
                modifier = Modifier.testTag("save_bank_button")
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save Bank")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = bankName,
            onValueChange = onBankNameChange,
            label = { Text("Bank Name (e.g. Plymouth County Fire)") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("bank_name_input")
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onOpenAddCustomChannel,
                modifier = Modifier
                    .weight(1f)
                    .testTag("add_custom_channel_to_bank_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Custom Channel")
            }

            Button(
                onClick = onOpenRadioReferenceImport,
                modifier = Modifier
                    .weight(1f)
                    .testTag("import_from_radioref_button")
            ) {
                Icon(Icons.Default.SettingsInputAntenna, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Import channels / CSV")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Channels in Bank (${channels.size})",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(6.dp))

        if (channels.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No channels in this bank yet. Use 'Add Custom Channel' or 'Import RadioReference' above.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(channels) { index, ch ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onOpenEditChannel(index) },
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${(index + 1).toString().padStart(2, '0')}. ${ch.name}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${ch.rxFreqMhzString} MHz • ${ch.duplex.display} • Tone: ${ch.rxTone}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { onDeleteDraftChannel(index) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Remove channel",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RadioReferenceHierarchicalBrowser(
    selectedRadio: classSupportedRadioModel,
    onBack: () -> Unit,
    onImportCsv: () -> Unit,
    onImportSelectedChannels: (List<RadioChannel>, String) -> Unit
) {
    // Drill-down state:
    // Root: National Frequencies at top + All 50 States below
    // Level 1: Selected State -> List of Counties
    // Level 2: Selected County or National Category -> List of Analog Categories & Checkable Channels
    var selectedState by remember { mutableStateOf<StateDirectory?>(null) }
    var selectedCounty by remember { mutableStateOf<CountyDirectory?>(null) }
    var selectedNationalGroup by remember { mutableStateOf<AnalogCategoryGroup?>(null) }
    var stateSearchFilter by remember { mutableStateOf("") }

    BackHandler {
        when {
            selectedNationalGroup != null -> selectedNationalGroup = null
            selectedCounty != null -> selectedCounty = null
            selectedState != null -> selectedState = null
            else -> onBack()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("rr_hierarchical_browser")
    ) {
        Text("Dated source snapshots and conventional CSV imports. Police, Fire and EMS are shown together. No live RadioReference connection.",
            modifier=Modifier.padding(12.dp),color=MaterialTheme.colorScheme.error)
        Button(onClick=onImportCsv,modifier=Modifier.fillMaxWidth().padding(horizontal=12.dp)) {
            Text("Import RadioReference CSV • all agencies together")
        }

        // Header Breadcrumb Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    when {
                        selectedNationalGroup != null -> selectedNationalGroup = null
                        selectedCounty != null -> selectedCounty = null
                        selectedState != null -> selectedState = null
                        else -> onBack()
                    }
                }
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                val title = when {
                    selectedNationalGroup != null -> selectedNationalGroup!!.title
                    selectedCounty != null -> "${selectedState?.name} > ${selectedCounty!!.name}"
                    selectedState != null -> "${selectedState!!.name} — Select County"
                    else -> "Offline directory / CSV import"
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Analog FM/NFM Conventional & Repeaters Only (P25 Digital Filtered Out)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        when {
            // View National Group Channels
            selectedNationalGroup != null -> {
                val group = selectedNationalGroup!!
                CategoryChannelPickerView(
                    selectedRadio = selectedRadio,
                    groups = listOf(group),
                    sourceLabel = group.title,
                    onImport = onImportSelectedChannels
                )
            }

            // View County Categories & Channels
            selectedCounty != null -> {
                val county = selectedCounty!!
                CategoryChannelPickerView(
                    selectedRadio = selectedRadio,
                    groups = county.categories,
                    sourceLabel = county.name,
                    onImport = onImportSelectedChannels
                )
            }

            // View Counties within Selected State
            selectedState != null -> {
                val state = selectedState!!
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.counties, key = { it.name }) { county ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedCounty = county }
                                .testTag("rr_county_${county.name}"),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = county.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    val totalCh = county.categories.sumOf { it.channels.size }
                                    Text(
                                        text = "${county.categories.size} Analog Categories ($totalCh Channels)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                            }
                        }
                    }
                }
            }

            // Root View: National Frequencies at the Top + All 50 States Below
            else -> {
                OutlinedTextField(
                    value = stateSearchFilter,
                    onValueChange = { stateSearchFilter = it },
                    label = { Text("Filter State or National Bank") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                val filteredNational = remember(stateSearchFilter) {
                    AnalogRadioReferenceDirectory.nationalGroups.filter {
                        stateSearchFilter.isBlank() ||
                            it.title.contains(stateSearchFilter, ignoreCase = true) ||
                            it.subtitle.contains(stateSearchFilter, ignoreCase = true)
                    }
                }
                val filteredStates = remember(stateSearchFilter) {
                    AnalogRadioReferenceDirectory.all50States.filter {
                        stateSearchFilter.isBlank() ||
                            it.name.contains(stateSearchFilter, ignoreCase = true) ||
                            it.code.equals(stateSearchFilter.trim(), ignoreCase = true)
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (filteredNational.isNotEmpty()) {
                        item {
                            Text(
                                text = "NATIONAL FREQUENCIES (ANALOG)",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        items(filteredNational, key = { it.id }) { natGroup ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedNationalGroup = natGroup }
                                    .testTag("rr_national_${natGroup.id}"),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Public,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = natGroup.title,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "${natGroup.channels.size} Analog Channels • ${natGroup.subtitle}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "AVAILABLE COUNTY SNAPSHOTS",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    items(filteredStates, key = { it.code }) { state ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedState = state }
                                .testTag("rr_state_${state.code}"),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = state.code,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = state.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${state.counties.size} Counties (Analog Conventional & Repeaters)",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryChannelPickerView(
    selectedRadio: classSupportedRadioModel,
    groups: List<AnalogCategoryGroup>,
    sourceLabel: String,
    onImport: (List<RadioChannel>, String) -> Unit
) {
    val allAvailable = remember(groups) {
        groups.flatMap { grp -> grp.channels.map { ch -> "${grp.id}_${ch.name}_${ch.rxFreqHz}" to ch } }
    }
    fun eligible(ch: RadioChannel): Boolean {
        if(!selectedRadio.model.contains("UV-5R Mini")) return true
        val f=ch.rxFreqHz
        val supported=if(ch.mode=="AM") f in 108000000L..135999999L else
            f in 136000000L..174000000L || f in 350000000L..390000000L || f in 400000000L..520000000L
        return supported && ch.name.length<=12
    }
    val eligibleAvailable = allAvailable.filter { eligible(it.second) }
    val selectedKeys = remember(groups,selectedRadio.id) {
        mutableStateListOf<String>().apply {
            addAll(eligibleAvailable.map { it.first })
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if(eligibleAvailable.size!=allAvailable.size) Text(
            "${allAvailable.size} source entries. ${allAvailable.size-eligibleAvailable.size} need a shorter radio alias or a different receive band; they are not selected by default.",
            color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = {
                    if (selectedKeys.size == eligibleAvailable.size) {
                        selectedKeys.clear()
                    } else {
                        selectedKeys.clear()
                        selectedKeys.addAll(eligibleAvailable.map { it.first })
                    }
                }
            ) {
                Text(if (selectedKeys.size == eligibleAvailable.size) "Deselect All" else "Select compatible (${eligibleAvailable.size})")
            }

            Button(
                onClick = {
                    val chosen = allAvailable
                        .filter { it.first in selectedKeys }
                        .map { it.second }
                    onImport(chosen, sourceLabel)
                },
                enabled = selectedKeys.isNotEmpty(),
                modifier = Modifier.testTag("confirm_import_rr_channels_button")
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Import Selected (${selectedKeys.size})")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            groups.forEach { group ->
                item(key = "header_${group.id}") {
                    Column(modifier = Modifier.padding(top = 4.dp)) {
                        Text(
                            text = group.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = group.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                items(group.channels, key = { "${group.id}_${it.name}_${it.rxFreqHz}" }) { ch ->
                    val key = "${group.id}_${ch.name}_${ch.rxFreqHz}"
                    val isChecked = key in selectedKeys
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                if (isChecked) selectedKeys.remove(key) else selectedKeys.add(key)
                            },
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        if (key !in selectedKeys) selectedKeys.add(key)
                                    } else {
                                        selectedKeys.remove(key)
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = ch.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${ch.rxFreqMhzString} MHz",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(
                                    text = "${ch.notes} • ${ch.duplex.display} • Tone: ${ch.rxTone}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
