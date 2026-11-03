package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
        enableEdgeToEdge()
        setContent {
            var isDarkTheme by rememberSaveable { mutableStateOf(true) }
            ChirpFieldTheme(darkTheme = isDarkTheme) {
                ChirpFieldAppNavigation(
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = { isDarkTheme = !isDarkTheme }
                )
            }
        }
    }
}

@Composable
fun ChirpFieldAppNavigation(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    auth: FirebaseAuth = Firebase.auth
) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(
            onAuthSuccess = { /* AuthStateListener updates currentUser automatically */ }
        )
    } else {
        AuthenticatedFieldWorkspace(
            currentUserId = user.uid,
            userDisplayName = user.displayName ?: user.email ?: "Operator",
            isDarkTheme = isDarkTheme,
            onToggleTheme = onToggleTheme
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthenticatedFieldWorkspace(
    currentUserId: String,
    userDisplayName: String,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    val context = LocalContext.current
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
    val rrUsername by viewModel.rrUsername.collectAsStateWithLifecycle()
    val rrConnected by viewModel.rrConnected.collectAsStateWithLifecycle()

    var currentTab by rememberSaveable { mutableStateOf(MainTab.CHANNEL_BANKS) }
    var showSettingsModal by remember { mutableStateOf(false) }

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
                                isCodeplugLoaded = isCodeplugLoaded,
                                codeplugTitle = codeplugTitle,
                                channels = codeplugChannels,
                                selectedRadio = selectedRadio,
                                cloudSavedCodeplugs = cloudSavedCodeplugs,
                                connectivityState = connectivityState,
                                isFlashing = isFlashing,
                                flashProgress = flashProgress,
                                flashLogs = flashLogs,
                                onSelectRadio = { radio ->
                                    viewModel.selectRadioModelForCodeplug(radio)
                                },
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
                                onDownloadFromRadio = { mgr, radio, mode, bleAddr, usbName ->
                                    viewModel.downloadFromRadioIntoCodeplug(mgr, radio, mode, bleAddr, usbName)
                                },
                                onUploadToRadio = { mgr, mode, bleAddr, usbName ->
                                    viewModel.uploadCurrentCodeplugToRadio(mgr, mode, bleAddr, usbName)
                                },
                                onRetryConnection = {
                                    viewModel.retryLastHardwareOperation()
                                },
                                onDisconnectHardware = {
                                    viewModel.disconnectHardware()
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

    if (showSettingsModal) {
        var usernameInput by remember(rrUsername) { mutableStateOf(rrUsername) }
        var passwordInput by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showSettingsModal = false },
            title = { Text("Settings & RadioReference Login") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "RADIOREFERENCE.COM ACCOUNT (OPTIONAL)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (rrConnected) {
                            "Connected as '$rrUsername' for live SOAP/CSV imports."
                        } else {
                            "Built-in National & 50-State Analog Directory works offline. Sign in if using a personal RadioReference subscription."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (rrConnected) RxSafeGreen else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = usernameInput,
                        onValueChange = { usernameInput = it },
                        label = { Text("RadioReference Username") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("rr_username_input")
                    )
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("RadioReference Password / API Key") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("rr_password_input")
                    )
                    Button(
                        onClick = {
                            viewModel.saveRadioReferenceLogin(context, usernameInput, passwordInput)
                            showSettingsModal = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("save_rr_login_button")
                    ) {
                        Text("Save RadioReference Login")
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Text(
                        text = "APP & OPERATOR ACCOUNT",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Signed in as: $userDisplayName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        OutlinedButton(
                            onClick = {
                                showSettingsModal = false
                                signOutUser(
                                    credentialManager = credentialManager,
                                    onSignOutComplete = {},
                                    scope = coroutineScope
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sign Out")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettingsModal = false }) {
                    Text("Close")
                }
            }
        )
    }
}
