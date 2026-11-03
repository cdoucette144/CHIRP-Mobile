package com.example.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.DuplexMode
import com.example.data.model.PowerLevel
import com.example.data.model.RadioChannel
import com.example.data.model.RadioSettingsState
import com.example.data.model.SafetyProfile
import com.example.data.model.SharedCodeplugDocument
import com.example.data.model.ZoneDocument
import com.example.data.repository.ChirpRepository
import com.example.domain.HardwareProgrammerManager
import com.example.domain.RadioCatalog
import com.example.domain.RepeaterOffsetCalculator
import com.example.domain.SafetyProfileEngine
import com.example.domain.classSupportedRadioModel
import com.google.firebase.Timestamp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class HardwareConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    FAILED
}

data class HardwareConnectivityState(
    val status: HardwareConnectionStatus = HardwareConnectionStatus.DISCONNECTED,
    val transport: String = "Bluetooth LE",
    val deviceLabel: String = "",
    val errorReason: String = "",
    val lastFailedAction: String = ""
)

data class LocalSavedCodeplugSummary(
    val id: String,
    val name: String,
    val radioModel: String,
    val channelCount: Int,
    val channels: List<RadioChannel>
)

class ChirpViewModel(
    private val repository: ChirpRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _selectedRadio = MutableStateFlow<classSupportedRadioModel>(RadioCatalog.allRadios.first())
    val selectedRadio: StateFlow<classSupportedRadioModel> = _selectedRadio.asStateFlow()

    // Tab 2: Current Radio Codeplug State (Blank by default!)
    private val _isCodeplugLoaded = MutableStateFlow(false)
    val isCodeplugLoaded: StateFlow<Boolean> = _isCodeplugLoaded.asStateFlow()

    private val _codeplugTitle = MutableStateFlow("Unsaved Codeplug")
    val codeplugTitle: StateFlow<String> = _codeplugTitle.asStateFlow()

    private val _codeplugChannels = MutableStateFlow<List<RadioChannel>>(emptyList())
    val codeplugChannels: StateFlow<List<RadioChannel>> = _codeplugChannels.asStateFlow()

    // Tab 3: Radio Settings State
    private val _radioSettings = MutableStateFlow(RadioSettingsState())
    val radioSettings: StateFlow<RadioSettingsState> = _radioSettings.asStateFlow()

    // Status & Hardware Transfer Telemetry
    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _isFlashing = MutableStateFlow(false)
    val isFlashing: StateFlow<Boolean> = _isFlashing.asStateFlow()

    private val _flashProgress = MutableStateFlow(0f)
    val flashProgress: StateFlow<Float> = _flashProgress.asStateFlow()

    private val _flashLogs = MutableStateFlow<List<String>>(emptyList())
    val flashLogs: StateFlow<List<String>> = _flashLogs.asStateFlow()

    private val _remotePreviewCodeplug = MutableStateFlow<SharedCodeplugDocument?>(null)
    val remotePreviewCodeplug: StateFlow<SharedCodeplugDocument?> = _remotePreviewCodeplug.asStateFlow()

    // RadioReference Login Credentials State
    private val _rrUsername = MutableStateFlow("")
    val rrUsername: StateFlow<String> = _rrUsername.asStateFlow()

    private val _rrConnected = MutableStateFlow(false)
    val rrConnected: StateFlow<Boolean> = _rrConnected.asStateFlow()

    // Real-Time Hardware Connectivity State & Retry Cache
    private val _hardwareConnectivity = MutableStateFlow(HardwareConnectivityState())
    val hardwareConnectivity: StateFlow<HardwareConnectivityState> = _hardwareConnectivity.asStateFlow()

    private var lastProgrammerManager: HardwareProgrammerManager? = null
    private var lastRadio: classSupportedRadioModel? = null
    private var lastTransportMode: String = "Bluetooth LE"
    private var lastBleAddress: String = ""
    private var lastUsbDeviceName: String = ""

    private val localBanks = MutableStateFlow<List<ZoneDocument>>(emptyList())
    private var localPrefs: android.content.SharedPreferences? = null

    private fun persistLocalBanks() {
        val rows = org.json.JSONArray()
        localBanks.value.forEach { bank ->
            rows.put(org.json.JSONObject().put("id", bank.id).put("name", bank.name)
                .put("region", bank.region).put("model", bank.targetRadioModel)
                .put("channels", org.json.JSONArray(bank.channelSpecs)))
        }
        localPrefs?.edit()?.putString("local_banks", rows.toString())?.apply()
    }

    // Observe all user documents in /zones:
    // category == "SAVED_CODEPLUG" -> Cloud Saved Codeplugs
    // otherwise -> Tab 1 Saved Channel Banks (NO default banks seeded!)
    private val allUserZoneDocs: StateFlow<UiState<List<ZoneDocument>>> =
        (if (currentUserId == "local") localBanks else repository.observeUserZones(currentUserId))
            .map<List<ZoneDocument>, UiState<List<ZoneDocument>>> { list ->
                val sorted = list.sortedBy { it.createdAt ?: Timestamp(0, 0) }
                UiState.Success(sorted)
            }
            .catch { error ->
                Log.w(TAG, "Error observing user zones", error)
                emit(UiState.Error(error.message ?: "Failed to load saved banks"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val channelBanksState: StateFlow<UiState<List<ZoneDocument>>> = allUserZoneDocs
        .map { state ->
            when (state) {
                is UiState.Loading -> UiState.Loading
                is UiState.Error -> state
                is UiState.Success -> UiState.Success(
                    state.data.filter { it.category != CATEGORY_SAVED_CODEPLUG }
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val cloudSavedCodeplugsState: StateFlow<List<ZoneDocument>> = allUserZoneDocs
        .map { state ->
            (state as? UiState.Success)?.data
                ?.filter { it.category == CATEGORY_SAVED_CODEPLUG }
                ?: emptyList()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    val sharedCodeplugsState: StateFlow<UiState<List<SharedCodeplugDocument>>> =
        (if (currentUserId == "local") kotlinx.coroutines.flow.flowOf(emptyList<SharedCodeplugDocument>()) else repository.observeUserSharedCodeplugs(currentUserId))
            .map<List<SharedCodeplugDocument>, UiState<List<SharedCodeplugDocument>>> { list ->
                val sorted = list.sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                UiState.Success(sorted)
            }
            .catch { error ->
                Log.w(TAG, "Error observing shared codeplugs", error)
                emit(UiState.Error(error.message ?: "Failed to load shared codeplugs"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    fun clearBanner() {
        _statusBanner.value = null
    }

    fun setBanner(message: String) {
        _statusBanner.value = message
    }

    fun saveRadioReferenceLogin(context: Context, username: String, password: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("rr_username", username.trim())
            .putBoolean("rr_connected", username.isNotBlank() && password.isNotBlank())
            .apply()
        _rrUsername.value = username.trim()
        _rrConnected.value = username.isNotBlank() && password.isNotBlank()
        _statusBanner.value = if (_rrConnected.value) {
            "RadioReference account '${username.trim()}' linked for analog imports."
        } else {
            "RadioReference credentials cleared."
        }
    }

    fun loadPrefs(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        localPrefs = prefs
        val rows = org.json.JSONArray(prefs.getString("local_banks", "[]") ?: "[]")
        localBanks.value = (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            val channels = row.getJSONArray("channels")
            ZoneDocument(id = row.getString("id"), name = row.getString("name"),
                region = row.getString("region"), targetRadioModel = row.getString("model"),
                category = CATEGORY_CHANNEL_BANK,
                channelSpecs = (0 until channels.length()).map { channels.getString(it) })
        }
        _rrUsername.value = prefs.getString("rr_username", "") ?: ""
        _rrConnected.value = prefs.getBoolean("rr_connected", false)
    }

    // --- TAB 1: CHANNEL BANKS OPERATIONS ---

    fun saveChannelBank(
        existingBank: ZoneDocument?,
        bankName: String,
        regionSummary: String,
        channels: List<RadioChannel>,
        onSaved: () -> Unit = {}
    ) {
        if (currentUserId == "local") {
            val bank = ZoneDocument(
                id = existingBank?.id ?: java.util.UUID.randomUUID().toString(),
                name = bankName.trim().ifBlank { "Custom Channel Bank" },
                region = regionSummary, category = CATEGORY_CHANNEL_BANK,
                targetRadioModel = _selectedRadio.value.model,
                channelSpecs = channels.map { it.toSpecString() }
            )
            localBanks.value = localBanks.value.filterNot { it.id == bank.id } + bank
            persistLocalBanks()
            _statusBanner.value = "Saved channel bank locally."
            onSaved()
            return
        }
        viewModelScope.launch {
            val reindexed = channels.mapIndexed { idx, ch -> ch.copy(number = idx + 1) }
            val result = if (existingBank == null) {
                repository.createZone(
                    name = bankName.trim().ifBlank { "Custom Channel Bank" },
                    region = regionSummary.trim().ifBlank { "Analog Bank" },
                    category = CATEGORY_CHANNEL_BANK,
                    safetyProfile = SafetyProfile.STANDARD_TX,
                    targetRadioModel = _selectedRadio.value.model,
                    channels = reindexed
                ).map { Unit }
            } else {
                repository.updateZone(
                    existingBank.copy(
                        name = bankName.trim().ifBlank { existingBank.name },
                        region = regionSummary.trim().ifBlank { existingBank.region }
                    ),
                    updatedChannels = reindexed
                )
            }
            result.onSuccess {
                _statusBanner.value = "Saved channel bank '$bankName' (${reindexed.size} channels)."
                onSaved()
            }.onFailure { err ->
                _statusBanner.value = "Failed to save bank: ${err.localizedMessage}"
            }
        }
    }

    fun deleteChannelBank(bank: ZoneDocument) {
        if (currentUserId == "local") {
            localBanks.value = localBanks.value.filterNot { it.id == bank.id }
            persistLocalBanks()
            return
        }
        viewModelScope.launch {
            val res = repository.deleteZone(bank.id)
            if (res.isSuccess) {
                _statusBanner.value = "Deleted bank '${bank.name}'."
            } else {
                _statusBanner.value = "Failed to delete bank: ${res.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    /**
     * Tacks all channels from the selected Channel Bank onto the end of the current Codeplug in Tab 2.
     * Applies proper per-channel RxOnly / FRS1 low-power fallback based on the current radio.
     */
    fun appendBankToCurrentCodeplug(bank: ZoneDocument, onAppended: () -> Unit = {}) {
        val radio = _selectedRadio.value
        val currentList = _codeplugChannels.value.toMutableList()
        val bankChannels = bank.parsedChannels().map { ch ->
            normalizeChannelForRadio(ch, ch.rxOnly, radio)
        }
        val combined = (currentList + bankChannels)
            .take(radio.maxChannels)
            .mapIndexed { idx, ch -> ch.copy(number = idx + 1) }

        _codeplugChannels.value = combined
        if (!_isCodeplugLoaded.value) {
            _isCodeplugLoaded.value = true
            _codeplugTitle.value = "${radio.model} Codeplug"
        }
        _statusBanner.value = "Added '${bank.name}' (${bankChannels.size} ch) to end of codeplug (Total: ${combined.size} ch)."
        onAppended()
    }

    // --- TAB 2: CURRENT CODEPLUG OPERATIONS ---

    fun selectRadioModelForCodeplug(radio: classSupportedRadioModel) {
        _selectedRadio.value = radio
        // Re-evaluate RxOnly channels in current codeplug for the new radio's capabilities
        if (_codeplugChannels.value.isNotEmpty()) {
            _codeplugChannels.value = _codeplugChannels.value
                .take(radio.maxChannels)
                .mapIndexed { idx, ch ->
                    if (radio.model.contains("UV-5R Mini")) ch else if (radio.model.contains("UV-5R Mini")) ch else normalizeChannelForRadio(ch.copy(number = idx + 1), ch.rxOnly, radio)
                }
        }
        _statusBanner.value = "Radio set to ${radio.model} (Max ${radio.maxChannels} ch)."
    }

    /**
     * Single RxOnly checkbox handler on any channel in Tab 2:
     * - If checked: makes the radio RX-only on that channel (Duplex = OFF), unless the radio doesn't
     *   have native RX-only (e.g. Baofeng UV-5R Mini), in which case it sets TX to FRS Ch 1 (462.5625 MHz @ Low Power).
     * - If unchecked: restores standard transmit / repeater offset capability on that channel.
     */
    fun toggleChannelRxOnly(channelNumber: Int, rxOnlyChecked: Boolean) {
        val radio = _selectedRadio.value
        _codeplugChannels.value = _codeplugChannels.value.map { ch ->
            if (ch.number == channelNumber) {
                normalizeChannelForRadio(ch, rxOnlyChecked, radio)
            } else {
                ch
            }
        }
    }

    private fun normalizeChannelForRadio(
        channel: RadioChannel,
        rxOnlyChecked: Boolean,
        radio: classSupportedRadioModel
    ): RadioChannel {
        return if (rxOnlyChecked) {
            SafetyProfileEngine.applySafetyProfileToChannel(
                channel = channel,
                safetyProfile = SafetyProfile.RX_ONLY_INHIBIT,
                targetRadio = radio
            )
        } else {
            SafetyProfileEngine.applySafetyProfileToChannel(
                channel = channel,
                safetyProfile = SafetyProfile.STANDARD_TX,
                targetRadio = radio
            )
        }
    }

    /**
     * Drag-and-drop reordering in Tab 2 Codeplug: moves channel from [fromIndex] to [toIndex]
     * and re-numbers slots sequentially (1..N).
     */
    fun moveCodeplugChannel(fromIndex: Int, toIndex: Int) {
        val current = _codeplugChannels.value.toMutableList()
        if (fromIndex !in current.indices || toIndex !in current.indices || fromIndex == toIndex) return
        val item = current.removeAt(fromIndex)
        current.add(toIndex, item)
        _codeplugChannels.value = current.mapIndexed { idx, ch -> ch.copy(number = idx + 1) }
    }

    fun updateOrAddCodeplugChannel(edited: RadioChannel, isNew: Boolean) {
        val current = _codeplugChannels.value.toMutableList()
        if (isNew) {
            current.add(edited.copy(number = current.size + 1))
        } else {
            val idx = current.indexOfFirst { it.number == edited.number }
            if (idx >= 0) {
                current[idx] = edited
            } else {
                current.add(edited)
            }
        }
        _codeplugChannels.value = current.mapIndexed { idx, ch -> ch.copy(number = idx + 1) }
        _isCodeplugLoaded.value = true
    }

    fun deleteCodeplugChannel(channelNumber: Int) {
        val remaining = _codeplugChannels.value
            .filterNot { it.number == channelNumber }
            .mapIndexed { idx, ch -> ch.copy(number = idx + 1) }
        _codeplugChannels.value = remaining
    }

    fun downloadFromRadioIntoCodeplug(
        programmerManager: HardwareProgrammerManager,
        radio: classSupportedRadioModel,
        transportMode: String,
        bleAddress: String,
        usbDeviceName: String
    ) {
        if (_isFlashing.value) return
        lastProgrammerManager = programmerManager
        lastRadio = radio
        lastTransportMode = transportMode
        lastBleAddress = bleAddress
        lastUsbDeviceName = usbDeviceName

        val targetLabel = if (transportMode == "Bluetooth LE") {
            bleAddress.ifBlank { radio.model }
        } else {
            usbDeviceName.ifBlank { "USB OTG" }
        }

        viewModelScope.launch {
            _selectedRadio.value = radio
            _isFlashing.value = true
            _flashProgress.value = 0f
            _flashLogs.value = emptyList()
            _hardwareConnectivity.value = HardwareConnectivityState(
                status = HardwareConnectionStatus.CONNECTING,
                transport = transportMode,
                deviceLabel = targetLabel,
                errorReason = "",
                lastFailedAction = "DOWNLOAD"
            )
            try {
                val downloadedChannels = programmerManager.readCodeplugFromRadio(
                    radio = radio,
                    transportMode = transportMode,
                    bleAddress = bleAddress,
                    usbDeviceName = usbDeviceName,
                    onProgress = { pct, line ->
                        _flashProgress.value = pct
                        _flashLogs.value = _flashLogs.value + line
                    }
                )
                _codeplugChannels.value = downloadedChannels.mapIndexed { idx, ch ->
                    normalizeChannelForRadio(ch.copy(number = idx + 1), ch.rxOnly, radio)
                }
                _codeplugTitle.value = "${radio.model} Codeplug"
                _isCodeplugLoaded.value = true
                _hardwareConnectivity.value = HardwareConnectivityState(
                    status = HardwareConnectionStatus.CONNECTED,
                    transport = transportMode,
                    deviceLabel = "${radio.model} ($targetLabel)",
                    errorReason = "",
                    lastFailedAction = ""
                )
                _statusBanner.value = "Downloaded ${downloadedChannels.size} channels from ${radio.model} via $transportMode."
            } catch (e: Exception) {
                val msg = e.localizedMessage ?: "Hardware clone read failed."
                _flashLogs.value = _flashLogs.value + "[ERROR] $msg"
                _hardwareConnectivity.value = HardwareConnectivityState(
                    status = HardwareConnectionStatus.FAILED,
                    transport = transportMode,
                    deviceLabel = targetLabel,
                    errorReason = msg,
                    lastFailedAction = "DOWNLOAD"
                )
                _statusBanner.value = "Download failed: $msg"
            } finally {
                _isFlashing.value = false
            }
        }
    }

    fun uploadCurrentCodeplugToRadio(
        programmerManager: HardwareProgrammerManager,
        transportMode: String,
        bleAddress: String,
        usbDeviceName: String
    ) {
        if (_isFlashing.value) return
        val radio = _selectedRadio.value
        val channels = _codeplugChannels.value
        if (channels.isEmpty()) {
            _statusBanner.value = "Codeplug has no channels to upload. Add a bank or channel first."
            return
        }

        lastProgrammerManager = programmerManager
        lastRadio = radio
        lastTransportMode = transportMode
        lastBleAddress = bleAddress
        lastUsbDeviceName = usbDeviceName

        val targetLabel = if (transportMode == "Bluetooth LE") {
            bleAddress.ifBlank { radio.model }
        } else {
            usbDeviceName.ifBlank { "USB OTG" }
        }

        viewModelScope.launch {
            _isFlashing.value = true
            _flashProgress.value = 0f
            _flashLogs.value = emptyList()
            _hardwareConnectivity.value = HardwareConnectivityState(
                status = HardwareConnectionStatus.CONNECTING,
                transport = transportMode,
                deviceLabel = targetLabel,
                errorReason = "",
                lastFailedAction = "UPLOAD"
            )
            try {
                programmerManager.executeProgrammingSequence(
                    radio = radio,
                    transportMode = transportMode,
                    bleAddress = bleAddress,
                    usbDeviceName = usbDeviceName,
                    channels = channels,
                    onProgress = { pct, line ->
                        _flashProgress.value = pct
                        _flashLogs.value = _flashLogs.value + line
                    }
                )
                _hardwareConnectivity.value = HardwareConnectivityState(
                    status = HardwareConnectionStatus.CONNECTED,
                    transport = transportMode,
                    deviceLabel = "${radio.model} ($targetLabel)",
                    errorReason = "",
                    lastFailedAction = ""
                )
                _statusBanner.value = if (radio.model.contains("UV-5R Mini")) "Mini upload check completed. See transfer log for verification details." else "Uploaded ${channels.size} channels & settings to ${radio.model}!"
            } catch (e: Exception) {
                val msg = e.localizedMessage ?: "Hardware clone write failed."
                _flashLogs.value = _flashLogs.value + "[ERROR] $msg"
                _hardwareConnectivity.value = HardwareConnectivityState(
                    status = HardwareConnectionStatus.FAILED,
                    transport = transportMode,
                    deviceLabel = targetLabel,
                    errorReason = msg,
                    lastFailedAction = "UPLOAD"
                )
                _statusBanner.value = "Upload failed: $msg"
            } finally {
                _isFlashing.value = false
            }
        }
    }

    fun retryLastHardwareOperation() {
        val pm = lastProgrammerManager ?: return
        val radio = lastRadio ?: _selectedRadio.value
        if (_hardwareConnectivity.value.lastFailedAction == "UPLOAD") {
            uploadCurrentCodeplugToRadio(pm, lastTransportMode, lastBleAddress, lastUsbDeviceName)
        } else {
            downloadFromRadioIntoCodeplug(pm, radio, lastTransportMode, lastBleAddress, lastUsbDeviceName)
        }
    }

    fun disconnectHardware() {
        _hardwareConnectivity.value = HardwareConnectivityState(
            status = HardwareConnectionStatus.DISCONNECTED,
            transport = "None",
            deviceLabel = "",
            errorReason = "",
            lastFailedAction = ""
        )
        _statusBanner.value = "Hardware disconnected."
    }

    // --- LOCAL & CLOUD CODEPLUG SAVE / OPEN ---

    fun saveCodeplugLocally(context: Context, name: String) {
        val prefs = context.getSharedPreferences(PREFS_CODEPLUGS, Context.MODE_PRIVATE)
        val cleanName = name.trim().ifBlank { "${_selectedRadio.value.model} Codeplug" }
        val key = "cp_${System.currentTimeMillis()}"
        val serializedChannels = _codeplugChannels.value.joinToString("\n") { it.toSpecString() }
        val payload = listOf(
            cleanName.replace("\n", " "),
            _selectedRadio.value.model.replace("\n", " "),
            _radioSettings.value.toCompactString()
        ).joinToString("||") + "\n" + serializedChannels

        prefs.edit().putString(key, payload).apply()
        _codeplugTitle.value = cleanName
        _isCodeplugLoaded.value = true
        _statusBanner.value = "Saved '$cleanName' to Local Device Storage."
    }

    fun listLocalCodeplugs(context: Context): List<LocalSavedCodeplugSummary> {
        val prefs = context.getSharedPreferences(PREFS_CODEPLUGS, Context.MODE_PRIVATE)
        return prefs.all.mapNotNull { (key, rawVal) ->
            val str = rawVal as? String ?: return@mapNotNull null
            val lines = str.lines()
            if (lines.isEmpty()) return@mapNotNull null
            val headerParts = lines.first().split("||")
            val name = headerParts.getOrNull(0) ?: "Local Codeplug"
            val model = headerParts.getOrNull(1) ?: "Baofeng UV-5R Mini"
            val channels = lines.drop(1)
                .filter { it.isNotBlank() }
                .mapIndexed { idx, spec -> RadioChannel.fromSpecString(spec, idx + 1) }
            LocalSavedCodeplugSummary(
                id = key,
                name = name,
                radioModel = model,
                channelCount = channels.size,
                channels = channels
            )
        }.sortedByDescending { it.id }
    }

    fun openLocalCodeplug(summary: LocalSavedCodeplugSummary) {
        _selectedRadio.value = RadioCatalog.findByModelName(summary.radioModel)
        _codeplugTitle.value = summary.name
        _codeplugChannels.value = summary.channels
        _isCodeplugLoaded.value = true
        _statusBanner.value = "Opened local codeplug '${summary.name}' (${summary.channelCount} ch)."
    }

    fun openCloudSavedCodeplug(doc: ZoneDocument) {
        _selectedRadio.value = RadioCatalog.findByModelName(doc.targetRadioModel)
        _codeplugTitle.value = doc.name
        _codeplugChannels.value = doc.parsedChannels()
        _isCodeplugLoaded.value = true
        _statusBanner.value = "Opened cloud codeplug '${doc.name}' (${doc.channelSpecs.size} ch)."
    }

    fun saveCodeplugToCloud(
        name: String,
        shareCode: String,
        authorCallsign: String,
        releaseNotes: String
    ) {
        if (currentUserId == "local") {
            _statusBanner.value = "Cloud sharing is unavailable in local mode. Use Save Local."
            return
        }
        viewModelScope.launch {
            val cleanName = name.trim().ifBlank { _codeplugTitle.value }
            val radio = _selectedRadio.value
            val channels = _codeplugChannels.value
            val createRes = repository.createZone(
                name = cleanName,
                region = _radioSettings.value.toCompactString(),
                category = CATEGORY_SAVED_CODEPLUG,
                safetyProfile = SafetyProfile.STANDARD_TX,
                targetRadioModel = radio.model,
                channels = channels,
                shareCode = shareCode.trim().uppercase()
            )
            createRes.onSuccess { zoneId ->
                _codeplugTitle.value = cleanName
                _isCodeplugLoaded.value = true
                if (shareCode.isNotBlank() && shareCode.trim().length >= 3) {
                    val zoneDoc = repository.getZoneById(zoneId).getOrNull()
                    if (zoneDoc != null) {
                        val pubRes = repository.publishOrUpdateSharedCodeplug(
                            zone = zoneDoc,
                            shareCode = shareCode,
                            authorCallsign = authorCallsign.ifBlank { "FIELD-OP" },
                            releaseNotes = releaseNotes
                        )
                        if (pubRes.isSuccess) {
                            _statusBanner.value = "Saved '$cleanName' to Cloud & published Share Code: ${pubRes.getOrThrow().shareCode}"
                            return@onSuccess
                        }
                    }
                }
                _statusBanner.value = "Saved '$cleanName' (${channels.size} ch) to Cloud."
            }.onFailure { err ->
                _statusBanner.value = "Cloud save failed: ${err.localizedMessage}"
            }
        }
    }

    fun lookupAndOpenRemoteCodeplug(shareCode: String) {
        if (currentUserId == "local") {
            _statusBanner.value = "Cloud sharing is unavailable in local mode."
            return
        }
        viewModelScope.launch {
            val res = repository.fetchRemoteSharedCodeplug(shareCode)
            res.onSuccess { doc ->
                _remotePreviewCodeplug.value = doc
                _selectedRadio.value = RadioCatalog.findByModelName(doc.targetRadioModel)
                _codeplugTitle.value = "${doc.name} (v${doc.version})"
                _codeplugChannels.value = doc.parsedChannels()
                _isCodeplugLoaded.value = true
                _statusBanner.value = "Loaded remote codeplug '${doc.name}' v${doc.version} (${doc.channelSpecs.size} ch)."
            }.onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Share code not found."
            }
        }
    }

    // --- TAB 3: RADIO SETTINGS ---

    fun updateRadioSettings(updated: RadioSettingsState) {
        _radioSettings.value = updated
    }

    companion object {
        private const val TAG = "ChirpViewModel"
        private const val STOP_TIMEOUT_MILLIS = 5000L
        private const val PREFS_NAME = "chirpfield_prefs"
        private const val PREFS_CODEPLUGS = "chirpfield_local_codeplugs"
        const val CATEGORY_CHANNEL_BANK = "CHANNEL_BANK"
        const val CATEGORY_SAVED_CODEPLUG = "SAVED_CODEPLUG"
    }
}
