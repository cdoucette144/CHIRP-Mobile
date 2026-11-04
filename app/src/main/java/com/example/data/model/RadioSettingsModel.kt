package com.example.data.model

enum class Uv32MenuSetting(val offset: Int, val label: String, val options: List<String>) {
    BATTERY_SAVE(0x8041, "Battery Save", listOf("Off", "On")),
    BACKLIGHT(0x8043, "Backlight Timer", listOf("Always On") + (5..20 step 5).map { "$it seconds" }),
    DUAL_WATCH(0x8044, "Dual Watch", listOf("Off", "On")),
    TIMEOUT(0x8045, "Transmit Timeout", listOf("Off") + (15..180 step 15).map { "$it seconds" }),
    VOICE_PROMPTS(0x8047, "Voice Prompts", listOf("Off", "On")),
    VOICE_LANGUAGE(0x8048, "Voice Language", listOf("English", "Chinese")),
    DTMF_SIDETONE(0x8049, "DTMF Sidetone", listOf("Off", "Keypad", "ANI", "Keypad + ANI")),
    SCAN_RESUME(0x804a, "Scan Resume", listOf("Time", "Carrier", "Search")),
    DISPLAY_A(0x804d, "Channel A Display", listOf("Name", "Frequency", "Channel Number")),
    DISPLAY_B(0x804e, "Channel B Display", listOf("Name", "Frequency", "Channel Number")),
    AUTO_LOCK(0x8050, "Keypad Auto Lock", listOf("Off", "On")),
    TAIL_CLEAR(0x8054, "Squelch Tail Elimination", listOf("Off", "On"));
}

data class Uv32SettingsSnapshot(
    val zoneNames: List<String>,
    val gpsEnabled: Boolean?,
    val gpsMode: String?,
    val gpsUtcOffsetHours: Int?,
    val squelch: Int?,
    val keypadBeep: Boolean?,
    val menuValues: Map<Uv32MenuSetting, Int>
) {
    companion object {
        fun fromImage(image: ByteArray): Uv32SettingsSnapshot {
            require(image.size == 0x8380)
            fun unsigned(offset: Int) = image[offset].toInt() and 0xff
            return Uv32SettingsSnapshot(
                zoneNames = List(10) { index ->
                    val start = 0x8280 + index * 16
                    image.copyOfRange(start, start + 16)
                        .takeWhile { it != 0.toByte() && it != 0xff.toByte() }
                        .toByteArray().toString(charset("GB2312")).trim()
                },
                gpsEnabled = when (unsigned(0x8070)) {
                    0 -> false
                    1 -> true
                    else -> null
                },
                gpsMode = listOf("GPS", "BeiDou", "GPS + BeiDou").getOrNull(unsigned(0x8071)),
                gpsUtcOffsetHours = unsigned(0x807b).takeIf { it in 0..24 }?.minus(12),
                squelch = unsigned(0x8040).takeIf { it in 0..9 },
                keypadBeep = when (unsigned(0x8046)) { 0 -> false; 1 -> true; else -> null },
                menuValues = buildMap {
                    for (setting in Uv32MenuSetting.entries) {
                        val value = unsigned(setting.offset)
                        if (value in setting.options.indices) put(setting, value)
                    }
                }
            )
        }
    }
}

data class RadioSettingsState(
    val squelchLevel: Int = 3,
    val timeOutTimerSec: String = "60s",
    val voiceLanguage: String = "English",
    val backlightTimeout: String = "10s",
    val powerOnMessageMode: String = "Message",
    val powerOnLine1: String = "CHIRP",
    val powerOnLine2: String = "FIELD",
    val batterySaveRatio: String = "1:2",
    val keypadBeep: Boolean = true,
    val rogerBeep: String = "Off",
    val dualWatchTdr: Boolean = true,
    val tailToneElimination: Boolean = true,
    val voxSensitivity: String = "Off",
    val sideKey1Short: String = "Monitor",
    val sideKey1Long: String = "Flashlight",
    val sideKey2Short: String = "Scan",
    val sideKey2Long: String = "FM Radio",
    val workModeA: String = "Channel Mode",
    val displayFormatA: String = "Name",
    val displayFormatB: String = "Frequency",
    val pttIdMode: String = "Off",
    val aniCode: String = "101",
    val uv32ZoneNames: List<String> = List(10) { "" },
    val uv32Downloaded: Uv32SettingsSnapshot? = null,
    val uv32MenuEdits: Map<Uv32MenuSetting, Int> = emptyMap()
) {
    fun toCompactString(): String {
        return listOf(
            "SQ=$squelchLevel",
            "TOT=$timeOutTimerSec",
            "LNG=$voiceLanguage",
            "BL=$backlightTimeout",
            "KB=${if (keypadBeep) 1 else 0}",
            "RB=$rogerBeep",
            "TDR=${if (dualWatchTdr) 1 else 0}",
            "SK1=$sideKey1Short/$sideKey1Long",
            "SK2=$sideKey2Short/$sideKey2Long",
            "DSP=$displayFormatA"
        ).joinToString(";").take(80)
    }

    companion object {
        val squelchOptions = (0..9).map { it.toString() }
        val totOptions = listOf("Off", "30s", "60s", "90s", "120s", "180s")
        val languageOptions = listOf("English", "Chinese", "Off")
        val backlightOptions = listOf("Always On", "5s", "10s", "15s", "20s", "Off")
        val powerOnModes = listOf("Full", "Message", "Voltage")
        val batterySaveOptions = listOf("Off", "1:1", "1:2", "1:3", "1:4")
        val rogerBeepOptions = listOf("Off", "Standard", "MDC1200")
        val voxOptions = listOf("Off", "1 (Low)", "2", "3", "4", "5 (Med)", "6", "7", "8", "9 (High)")
        val sideKeyActions = listOf("Off", "Monitor", "Scan", "Flashlight", "FM Radio", "VOX", "TX Power", "SOS Alarm", "Tone 1750Hz")
        val workModes = listOf("Channel Mode", "Frequency (VFO) Mode")
        val displayFormats = listOf("Name", "Frequency", "Channel Number")
        val pttIdModes = listOf("Off", "BOT (Begin)", "EOT (End)", "Both")
    }
}
