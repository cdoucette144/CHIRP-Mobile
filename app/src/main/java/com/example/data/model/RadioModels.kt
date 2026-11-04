package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.util.Locale
import kotlin.math.roundToLong

enum class SafetyProfile(val id: String, val label: String, val shortBadge: String, val description: String) {
    RX_ONLY_INHIBIT(
        id = "RX_ONLY_INHIBIT",
        label = "Scanner Safe (RX-Only / Auto FRS1 Fallback)",
        shortBadge = "RX SAFE",
        description = "Disables TX (Duplex=OFF). On radios without native RX-only (e.g. UV-5R Mini), routes TX to FRS Ch 1 (462.5625 MHz) at 0.5W Low Power."
    ),
    SAFE_TX_FALLBACK_FRS1(
        id = "SAFE_TX_FALLBACK_FRS1",
        label = "Force FRS1 0.5W TX Fallback",
        shortBadge = "FRS1 0.5W",
        description = "Forces every non-simplex scanner channel's TX to FRS Ch 1 (462.5625 MHz) @ 0.5W Low Power so accidental PTT presses never key public safety repeaters."
    ),
    STANDARD_TX(
        id = "STANDARD_TX",
        label = "Authorized TX Capable (EMT / Fire / Ham)",
        shortBadge = "TX ENABLED",
        description = "Programs full repeater TX offsets, CTCSS/DCS tones, and selected power levels for authorized emergency backup and licensed operators."
    );

    companion object {
        fun fromId(id: String): SafetyProfile = entries.find { it.id == id } ?: RX_ONLY_INHIBIT
    }
}

enum class DuplexMode(val code: String, val display: String) {
    SIMPLEX("SIMPLEX", "Simplex"),
    PLUS("+", "+ Offset"),
    MINUS("-", "- Offset"),
    SPLIT("SPLIT", "Split TX"),
    OFF("OFF", "RX Only (TX Off)");

    companion object {
        fun fromCode(code: String): DuplexMode = entries.find { it.code == code } ?: SIMPLEX
    }
}

enum class PowerLevel(val code: String, val wattsLabel: String) {
    LOW("LOW", "0.5W / 1W Low"),
    MID("MID", "2W / 5W Mid"),
    HIGH("HIGH", "5W / 8W High");

    companion object {
        fun fromCode(code: String): PowerLevel = entries.find { it.code == code } ?: LOW
    }
}

data class RadioChannel(
    val number: Int = 1,
    val name: String = "CH 01",
    val rxFreqHz: Long = 146520000L,
    val txFreqHz: Long = 146520000L,
    val duplex: DuplexMode = DuplexMode.SIMPLEX,
    val offsetHz: Long = 0L,
    val rxTone: String = "None",
    val txTone: String = "None",
    val mode: String = "FM",
    val power: PowerLevel = PowerLevel.LOW,
    val rxOnly: Boolean = false,
    val notes: String = "",
    val scanIncluded: Boolean? = null,
    val busyChannelLockout: Boolean? = null,
    val fhssPrivacy: Boolean? = null,
    val pttId: Int? = null
) {
    val rxFreqMhzString: String
        get() = String.format(Locale.US, "%.4f", rxFreqHz / 1_000_000.0)

    val txFreqMhzString: String
        get() = String.format(Locale.US, "%.4f", txFreqHz / 1_000_000.0)

    val offsetMhzString: String
        get() = String.format(Locale.US, "%.3f", offsetHz / 1_000_000.0)

    fun toSpecString(): String {
        val safeName = name.replace("|", " ").take(24)
        val safeNotes = notes.replace("|", " ").take(48)
        val rxModeFlag = if (rxOnly) "RX_ONLY" else "TX_OK"
        return listOf(
            number.toString(),
            safeName,
            rxFreqHz.toString(),
            txFreqHz.toString(),
            duplex.code,
            offsetHz.toString(),
            rxTone,
            txTone,
            mode,
            power.code,
            rxModeFlag,
            safeNotes,
            scanIncluded?.toString() ?: "",
            busyChannelLockout?.toString() ?: "",
            fhssPrivacy?.toString() ?: "",
            pttId?.toString() ?: ""
        ).joinToString("|")
    }

    companion object {
        const val FRS1_FALLBACK_HZ = 462562500L

        fun mhzToHz(mhz: Double): Long = (mhz * 1_000_000.0).roundToLong()

        fun fromSpecString(spec: String, fallbackIndex: Int = 1): RadioChannel {
            val parts = spec.split("|")
            if (parts.size < 11) {
                return RadioChannel(number = fallbackIndex, name = spec.take(16))
            }
            val num = parts[0].toIntOrNull() ?: fallbackIndex
            val name = parts[1].ifBlank { "CH $num" }
            val rxHz = parts[2].toLongOrNull() ?: 146520000L
            val txHz = parts[3].toLongOrNull() ?: rxHz
            val duplex = DuplexMode.fromCode(parts[4])
            val offsetHz = parts[5].toLongOrNull() ?: 0L
            val rxTone = parts[6].ifBlank { "None" }
            val txTone = parts[7].ifBlank { "None" }
            val mode = parts[8].ifBlank { "FM" }
            val power = PowerLevel.fromCode(parts[9])
            val rxOnly = parts[10] == "RX_ONLY" || duplex == DuplexMode.OFF
            val notes = parts.getOrNull(11) ?: ""
            return RadioChannel(
                number = num,
                name = name,
                rxFreqHz = rxHz,
                txFreqHz = txHz,
                duplex = duplex,
                offsetHz = offsetHz,
                rxTone = rxTone,
                txTone = txTone,
                mode = mode,
                power = power,
                rxOnly = rxOnly,
                notes = notes,
                scanIncluded = parts.getOrNull(12)?.toBooleanStrictOrNull(),
                busyChannelLockout = parts.getOrNull(13)?.toBooleanStrictOrNull(),
                fhssPrivacy = parts.getOrNull(14)?.toBooleanStrictOrNull(),
                pttId = parts.getOrNull(15)?.toIntOrNull()
            )
        }
    }
}

@IgnoreExtraProperties
data class ZoneDocument(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val region: String = "",
    val category: String = "Custom",
    val safetyProfile: String = SafetyProfile.RX_ONLY_INHIBIT.id,
    val targetRadioModel: String = "Baofeng UV-5R Mini",
    val shareCode: String = "",
    val version: Long = 1L,
    val channelSpecs: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun parsedChannels(): List<RadioChannel> =
        channelSpecs.mapIndexed { idx, spec -> RadioChannel.fromSpecString(spec, idx + 1) }

    fun resolvedSafetyProfile(): SafetyProfile = SafetyProfile.fromId(safetyProfile)
}

@IgnoreExtraProperties
data class SharedCodeplugDocument(
    @DocumentId val id: String = "",
    val userId: String = "",
    val authorCallsign: String = "",
    val shareCode: String = "",
    val name: String = "",
    val region: String = "",
    val category: String = "Custom",
    val safetyProfile: String = SafetyProfile.RX_ONLY_INHIBIT.id,
    val targetRadioModel: String = "Baofeng UV-5R Mini",
    val version: Long = 1L,
    val releaseNotes: String = "",
    val channelSpecs: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun parsedChannels(): List<RadioChannel> =
        channelSpecs.mapIndexed { idx, spec -> RadioChannel.fromSpecString(spec, idx + 1) }

    fun resolvedSafetyProfile(): SafetyProfile = SafetyProfile.fromId(safetyProfile)
}
