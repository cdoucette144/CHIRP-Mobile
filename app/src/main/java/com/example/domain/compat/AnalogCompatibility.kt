package com.example.domain.compat

/** Universal, radio-independent analog channel definition. */
enum class AnalogMode { FM, NFM, AM }
enum class TxPolicy { ENABLED, NATIVE_INHIBIT, DUMMY_FALLBACK }

data class AnalogChannel(
    val id: String,
    val name: String,
    val rxMHz: Double,
    val mode: AnalogMode,
    val txMHz: Double? = null,
    val txPolicy: TxPolicy = TxPolicy.NATIVE_INHIBIT,
    val toneHz: Double? = null
)

data class FrequencyBand(val minMHz: Double, val maxMHz: Double, val modes: Set<AnalogMode>) {
    fun supports(frequency: Double, mode: AnalogMode) = frequency in minMHz..maxMHz && mode in modes
}

data class RadioCapabilities(
    val modelId: String,
    val receiveBands: List<FrequencyBand>,
    val transmitBands: List<FrequencyBand>,
    val maxChannels: Int,
    val nativeTxInhibit: Boolean,
    val independentTxFrequency: Boolean,
    val minimumPowerSelectable: Boolean
)

enum class Verdict { COMPATIBLE, REQUIRES_CONFIRMATION, UNSUPPORTED }
data class CompatibilityResult(val verdict: Verdict, val messages: List<String>)

object AnalogCompatibility {
    fun evaluate(channel: AnalogChannel, radio: RadioCapabilities, dummyTxMHz: Double? = null): CompatibilityResult {
        val notes = mutableListOf<String>()
        if (!channel.rxMHz.isFinite() || channel.rxMHz <= 0.0 ||
            radio.receiveBands.none { it.supports(channel.rxMHz, channel.mode) }) {
            return CompatibilityResult(Verdict.UNSUPPORTED, listOf("Receive frequency/modulation unsupported by this radio"))
        }
        when (channel.txPolicy) {
            TxPolicy.NATIVE_INHIBIT -> if (!radio.nativeTxInhibit) {
                return CompatibilityResult(Verdict.UNSUPPORTED, listOf("Radio has no verified native TX inhibit"))
            }
            TxPolicy.ENABLED -> {
                val tx = channel.txMHz ?: channel.rxMHz
                if (!tx.isFinite() || radio.transmitBands.none { it.supports(tx, channel.mode) })
                    return CompatibilityResult(Verdict.UNSUPPORTED, listOf("Transmit frequency/modulation unsupported"))
            }
            TxPolicy.DUMMY_FALLBACK -> {
                if (!radio.independentTxFrequency || !radio.minimumPowerSelectable)
                    return CompatibilityResult(Verdict.UNSUPPORTED, listOf("Safe dummy-TX configuration not verified for this model"))
                if (dummyTxMHz == null || !dummyTxMHz.isFinite() || radio.transmitBands.none { it.supports(dummyTxMHz, channel.mode) })
                    return CompatibilityResult(Verdict.UNSUPPORTED, listOf("Dummy transmit frequency unsupported or unspecified"))
                notes += "NOT RX ONLY: PTT still transmits on the dummy frequency. Operator confirmation required."
                notes += "Using non-FRS-certified equipment on FRS frequencies may violate FCC rules."
                return CompatibilityResult(Verdict.REQUIRES_CONFIRMATION, notes)
            }
        }
        return CompatibilityResult(Verdict.COMPATIBLE, notes)
    }

    /** Import only analog modes; do not silently coerce digital modes to FM. */
    fun parseMode(raw: String): AnalogMode? = when (raw.trim().uppercase()) {
        "FM" -> AnalogMode.FM
        "NFM" -> AnalogMode.NFM
        "AM" -> AnalogMode.AM
        else -> null
    }
}
