package com.example.domain.compat

import org.junit.Assert.*
import org.junit.Test

class AnalogCompatibilityTest {
    private val vhf = FrequencyBand(136.0, 174.0, setOf(AnalogMode.FM, AnalogMode.NFM))
    private val uhf = FrequencyBand(400.0, 520.0, setOf(AnalogMode.FM, AnalogMode.NFM))
    private val radio = RadioCapabilities("example", listOf(vhf, uhf), listOf(vhf, uhf), 128, false, true, true)

    @Test fun digitalModesRejected() {
        listOf("P25", "DMR", "NXDN", "D-STAR", "TETRA").forEach { assertNull(AnalogCompatibility.parseMode(it)) }
        assertEquals(AnalogMode.NFM, AnalogCompatibility.parseMode("nfm"))
    }
    @Test fun unsupportedCbFrequency() {
        val ch = AnalogChannel("1", "CB19", 27.185, AnalogMode.AM)
        assertEquals(Verdict.UNSUPPORTED, AnalogCompatibility.evaluate(ch, radio).verdict)
    }
    @Test fun dummyTxRequiresConfirmation() {
        val ch = AnalogChannel("1", "County", 154.43, AnalogMode.NFM, txPolicy = TxPolicy.DUMMY_FALLBACK)
        assertEquals(Verdict.REQUIRES_CONFIRMATION, AnalogCompatibility.evaluate(ch, radio, 462.5625).verdict)
    }
    @Test fun noFalseNativeInhibit() {
        val ch = AnalogChannel("1", "County", 154.43, AnalogMode.NFM)
        assertEquals(Verdict.UNSUPPORTED, AnalogCompatibility.evaluate(ch, radio).verdict)
    }
}
