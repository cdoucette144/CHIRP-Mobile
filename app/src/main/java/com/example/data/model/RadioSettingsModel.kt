package com.example.data.model

data class Uv32SettingsSnapshot(
    val zoneNames: List<String>,
    val gpsEnabled: Boolean?,
    val gpsMode: String?,
    val gpsUtcOffsetHours: Int?
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
                gpsUtcOffsetHours = unsigned(0x807b).takeIf { it in 0..24 }?.minus(12)
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
    val uv32Downloaded: Uv32SettingsSnapshot? = null
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
