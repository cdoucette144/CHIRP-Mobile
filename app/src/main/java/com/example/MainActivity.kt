package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Radio
import androidx.compose.ui.graphics.Color
import com.example.ui.viewmodel.HardwareConnectionStatus
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.authStateFlow
import com.example.auth.signOutUser
import com.example.data.repository.ChirpRepository
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ChannelBanksTabScreen
import com.example.ui.screens.CodeplugTabScreen
import com.example.ui.screens.RadioSettingsTabScreen
import com.example.ui.theme.AmberVfoPrimary
import com.example.ui.theme.ChirpFieldTheme
import com.example.ui.theme.RxSafeGreen
import com.example.ui.viewmodel.ChirpViewModel
import com.example.ui.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

enum class MainTab {
    CHANNEL_BANKS,
    CODEPLUG,
    RADIO_SETTINGS
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.example.domain.AnalogRadioReferenceDirectory.loadExportCatalog(applicationContext)
        enableEdgeToEdge()
        setContent {
            val appearance = remember { getSharedPreferences("chirpfield_appearance", MODE_PRIVATE) }
            var isDarkTheme by rememberSaveable { mutableStateOf(appearance.getBoolean("dark", true)) }
            var palette by rememberSaveable { mutableStateOf(appearance.getString("palette", "Amber / Cyan") ?: "Amber / Cyan") }
            ChirpFieldTheme(darkTheme = isDarkTheme, palette = palette) {
                ChirpFieldAppNavigation(
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = {
                        isDarkTheme = !isDarkTheme
                        appearance.edit().putBoolean("dark", isDarkTheme).apply()
                    },
                    palette = palette,
                    onPaletteChange = {
                        palette = it
                        appearance.edit().putString("palette", it).apply()
                    }
                )
            }
        }
    }
}

@Composable
fun ChirpFieldAppNavigation(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    palette: String = "Amber / Cyan",
    onPaletteChange: (String) -> Unit = {},

) {
    AuthenticatedFieldWorkspace(
        currentUserId = "local",
        userDisplayName = "Local Operator",
        isDarkTheme = isDarkTheme,
        onToggleTheme = onToggleTheme,
        palette = palette,
        onPaletteChange = onPaletteChange
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthenticatedFieldWorkspace(
    currentUserId: String,
    userDisplayName: String,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    palette: String = "Amber / Cyan",
    onPaletteChange: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val programmerManager = remember(context) { com.example.domain.HardwareProgrammerManager(context) }
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: ChirpViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                ChirpViewModel(ChirpRepository(db), currentUserId)
            }
        }
    )

    LaunchedEffect(context) {
        viewModel.loadPrefs(context)
    }

    val channelBanksState by viewModel.channelBanksState.collectAsStateWithLifecycle()
    val cloudSavedCodeplugs by viewModel.cloudSavedCodeplugsState.collectAsStateWithLifecycle()
    val isCodeplugLoaded by viewModel.isCodeplugLoaded.collectAsStateWithLifecycle()
    val codeplugTitle by viewModel.codeplugTitle.collectAsStateWithLifecycle()
    val codeplugChannels by viewModel.codeplugChannels.collectAsStateWithLifecycle()
    val radioSettings by viewModel.radioSettings.collectAsStateWithLifecycle()
    val selectedRadio by viewModel.selectedRadio.collectAsStateWithLifecycle()
    val connectivityState by viewModel.hardwareConnectivity.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val isFlashing by viewModel.isFlashing.collectAsStateWithLifecycle()
    val flashProgress by viewModel.flashProgress.collectAsStateWithLifecycle()
    val flashLogs by viewModel.flashLogs.collectAsStateWithLifecycle()

    var currentTab by rememberSaveable { mutableStateOf(MainTab.CHANNEL_BANKS) }
    var showSettingsModal by remember { mutableStateOf(false) }
    val hasCodeplug = isCodeplugLoaded || codeplugChannels.isNotEmpty()
    var showCodeplugRadioControls by remember(hasCodeplug) { mutableStateOf(!hasCodeplug) }

    BackHandler(enabled = currentTab != MainTab.CHANNEL_BANKS) {
        currentTab = MainTab.CHANNEL_BANKS
    }

    val savedBanks = (channelBanksState as? UiState.Success)?.data ?: emptyList()

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "// ${selectedRadio.model}",
                                style = MaterialTheme.typography.labelMedium,
                                color = AmberVfoPrimary
                            )
                        }
                    },
                    actions = {
                        if (currentTab != MainTab.CHANNEL_BANKS) {
                            val (description, tint) = when {
                                isFlashing || connectivityState.status == HardwareConnectionStatus.CONNECTING ->
                                    "Radio transfer in progress" to Color(0xFFFFC547)
                                connectivityState.status == HardwareConnectionStatus.CONNECTED ->
                                    "Radio connected" to Color(0xFF4CAF70)
                                connectivityState.status == HardwareConnectionStatus.FAILED ->
                                    "Radio error" to Color(0xFFF36C68)
                                else -> "Radio disconnected" to Color(0xFFF36C68)
                            }
                            IconButton(onClick = {
                                if (currentTab == MainTab.CODEPLUG) showCodeplugRadioControls = !showCodeplugRadioControls
                                else { currentTab = MainTab.CODEPLUG; showCodeplugRadioControls = true }
                            }) {
                                Icon(Icons.Default.Radio, contentDescription = description, tint = tint)
                            }
                        }
                        IconButton(
                            onClick = { showSettingsModal = true },
                            modifier = Modifier.testTag("top_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(R.string.cd_settings)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                // Top Horizontal Bar with 3 Tabs
                PrimaryTabRow(
                    selectedTabIndex = currentTab.ordinal,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Tab(
                        selected = currentTab == MainTab.CHANNEL_BANKS,
                        onClick = { currentTab = MainTab.CHANNEL_BANKS },
                        text = {
                            Text(
                                text = stringResource(R.string.tab_channel_banks),
                                fontWeight = if (currentTab == MainTab.CHANNEL_BANKS) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        modifier = Modifier.testTag("top_tab_channel_banks")
                    )
                    Tab(
                        selected = currentTab == MainTab.CODEPLUG,
                        onClick = { currentTab = MainTab.CODEPLUG },
                        text = {
                            val badge = if (codeplugChannels.isNotEmpty()) " (${codeplugChannels.size})" else ""
                            Text(
                                text = stringResource(R.string.tab_codeplug) + badge,
                                fontWeight = if (currentTab == MainTab.CODEPLUG) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        modifier = Modifier.testTag("top_tab_codeplug")
                    )
                    Tab(
                        selected = currentTab == MainTab.RADIO_SETTINGS,
                        onClick = { currentTab = MainTab.RADIO_SETTINGS },
                        text = {
                            Text(
                                text = stringResource(R.string.tab_radio_settings),
                                fontWeight = if (currentTab == MainTab.RADIO_SETTINGS) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        modifier = Modifier.testTag("top_tab_radio_settings")
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedVisibility(visible = statusBanner != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.clearBanner() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = statusBanner ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.clearBanner() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss notification",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            when (val st = channelBanksState) {
                is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AmberVfoPrimary)
                    }
                }
                is UiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = st.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                is UiState.Success -> {
                    when (currentTab) {
                        MainTab.CHANNEL_BANKS -> {
                            ChannelBanksTabScreen(
                                savedBanks = savedBanks,
                                selectedRadio = selectedRadio,
                                onSaveBank = { existing, name, reg, chList ->
                                    viewModel.saveChannelBank(existing, name, reg, chList)
                                },
                                onMoveBank = viewModel::moveLocalBank,
                                onDeleteBank = { bank ->
                                    viewModel.deleteChannelBank(bank)
                                },
                                onAddBankToCodeplug = { bank ->
                                    viewModel.appendBankToCurrentCodeplug(bank) {
                                        currentTab = MainTab.CODEPLUG
                                    }
                                }
                            )
                        }
                        MainTab.CODEPLUG -> {
                            CodeplugTabScreen(
                                programmerManager = programmerManager,
                                isCodeplugLoaded = isCodeplugLoaded,
                                codeplugTitle = codeplugTitle,
                                channels = codeplugChannels,
                                selectedRadio = selectedRadio,
                                cloudSavedCodeplugs = cloudSavedCodeplugs,
                                showRadioControls = showCodeplugRadioControls,
                                zoneNames = List(10) { index ->
                                    radioSettings.uv32ZoneNames[index].ifBlank {
                                        radioSettings.uv32Downloaded?.zoneNames?.getOrNull(index).orEmpty()
                                    }
                                },
                                isFlashing = isFlashing,
                                flashProgress = flashProgress,
                                flashLogs = flashLogs,
                                onSelectRadio = { radio ->
                                    viewModel.selectRadioModelForCodeplug(radio)
                                },
                                onBulkRxOnly = viewModel::setCodeplugRxOnly,
                                onSetScan = viewModel::setCodeplugScan,
                                onRenumber = viewModel::renumberCodeplugChannel,
                                onImportChannels = viewModel::importIntoCodeplug,
                                onToggleRxOnly = { channelNum, rxOnlyChecked ->
                                    viewModel.toggleChannelRxOnly(channelNum, rxOnlyChecked)
                                },
                                onMoveChannel = { fromIdx, toIdx ->
                                    viewModel.moveCodeplugChannel(fromIdx, toIdx)
                                },
                                onSaveOrAddChannel = { ch, isNew ->
                                    viewModel.updateOrAddCodeplugChannel(ch, isNew)
                                },
                                onDeleteChannel = { chNum ->
                                    viewModel.deleteCodeplugChannel(chNum)
                                },
                                onDeleteChannels = { numbers -> viewModel.deleteCodeplugChannels(numbers) },
                                onEditChannels = { numbers, mode, power -> viewModel.editCodeplugChannels(numbers,mode,power) },
                                onDownloadFromRadio = { mgr, radio, mode, bleAddr, usbName ->
                                    viewModel.downloadFromRadioIntoCodeplug(mgr, radio, mode, bleAddr, usbName)
                                },
                                onUploadToRadio = { mgr, mode, bleAddr, usbName ->
                                    viewModel.uploadCurrentCodeplugToRadio(mgr, mode, bleAddr, usbName)
                                },

                                onSaveLocal = { name ->
                                    viewModel.saveCodeplugLocally(context, name)
                                },
                                onListLocalCodeplugs = {
                                    viewModel.listLocalCodeplugs(context)
                                },
                                onOpenLocalCodeplug = { summary ->
                                    viewModel.openLocalCodeplug(summary)
                                },
                                onOpenCloudCodeplug = { doc ->
                                    viewModel.openCloudSavedCodeplug(doc)
                                },
                                onSaveCloudCodeplug = { name, shareCode, callsign, notes ->
                                    viewModel.saveCodeplugToCloud(name, shareCode, callsign, notes)
                                },
                                onOpenRemoteShareCode = { code ->
                                    viewModel.lookupAndOpenRemoteCodeplug(code)
                                }
                            )
                        }
                        MainTab.RADIO_SETTINGS -> {
                            RadioSettingsTabScreen(
                                selectedRadio = selectedRadio,
                                settings = radioSettings,
                                connectivityState = connectivityState,
                                onSelectRadio = { radio ->
                                    viewModel.selectRadioModelForCodeplug(radio)
                                },
                                onUpdateSettings = { updated ->
                                    viewModel.updateRadioSettings(updated)
                                },
                                onRetryConnection = {
                                    viewModel.retryLastHardwareOperation()
                                },
                                onDisconnectHardware = {
                                    viewModel.disconnectHardware()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    val exportBackup = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip")
    ) { destination ->
        if (destination != null) viewModel.exportLocalBackup(context, destination)
    }

    if (showSettingsModal) {

        AlertDialog(
            onDismissRequest = { showSettingsModal = false },
            title = { Text("Settings") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "RADIOREFERENCE CSV IMPORT",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Use Channel Banks → Import channels / CSV to add RadioReference exports.",
                        style = MaterialTheme.typography.bodySmall
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Text(
                        text = "APPEARANCE",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    com.example.ui.components.ChoiceDropdown(
                        "Color palette", listOf("Amber / Cyan", "Teal / Blue", "Purple / Rose", "Blue / Lime"),
                        palette, onPaletteChange
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onToggleTheme,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("toggle_theme_button")
                        ) {
                            Icon(
                                imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isDarkTheme) "Light UI" else "Dark UI")
                        }

                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showSettingsModal = false
                    val date = java.text.SimpleDateFormat("yyyy-MM-dd_HHmm", java.util.Locale.US).format(java.util.Date())
                    exportBackup.launch("ChirpField-backup-$date.zip")
                }) { Text("Export backup") }
                TextButton(onClick = { showSettingsModal = false }) {
                    Text("Close")
                }
            }
        )
    }
}
