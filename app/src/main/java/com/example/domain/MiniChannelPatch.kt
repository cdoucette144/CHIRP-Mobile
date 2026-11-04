package com.example.domain

import com.example.data.model.*
import java.util.Locale

/** Overlay editable fields on the Mini's 32-byte records; preserve all other bytes. */
object MiniChannelPatch {
    fun apply(original: ByteArray, channels: List<RadioChannel>): ByteArray =
        patch(original, channels, 999, null, emptyMap())

    internal fun applyUv32(original: ByteArray, channels: List<RadioChannel>, changedSlots: Set<Int>, baseline: Map<Int, RadioChannel>): ByteArray =
        patch(original, channels, 1000, changedSlots, baseline)

    private fun patch(original: ByteArray, channels: List<RadioChannel>, slotCount: Int, changedSlots: Set<Int>?, baseline: Map<Int, RadioChannel>): ByteArray {
        val uv32 = slotCount == 1000
        val model = if (uv32) "UV-32" else "UV-5R Mini"
        require(original.size == (if (uv32) 0x8380 else 0x8240)) { "Full $model image required" }
        require(channels.map { it.number }.distinct().size == channels.size) { "Duplicate channel slots" }
        require(channels.all { it.number in 1..slotCount }) { "$model slots must be 1–$slotCount" }
        val result = original.copyOf()
        val bySlot = channels.associateBy { it.number }
        for (slot in 1..slotCount) {
            if (changedSlots != null && slot !in changedSlots) continue
            val at = (slot - 1) * 32
            val ch = bySlot[slot]
            if (ch == null) {
                if (original[at] != 0xff.toByte()) result.fill(0xff.toByte(), at, at + 32)
                continue
            }
            require(ch.mode in listOf("FM", "NFM", "AM")) { "Analog modes only" }
            require(uv32 || (ch.name.length <= 12 && ch.name.all { it.code in 32..126 })) {
                "Mini names require at most 12 ASCII characters"
            }
            if (original[at] == 0xff.toByte()) {
                result.fill(0, at, at + 20)
                result[at+15] = 4 // include new channel in scanning
            }
            fun frequency(offset: Int, hz: Long) {
                require(hz in 108000000L..520000000L && hz % 10 == 0L) { "Invalid $model frequency in channel $slot" }
                var value = hz / 10
                for (i in 0..3) {
                    result[offset+i] = ((value % 10) + ((value / 10) % 10) * 16).toByte()
                    value /= 100
                }
            }
            fun tone(offset: Int, text: String) {
                val value = MiniToneCodec.encode(text)
                // Preserve the radio's original representation for disabled tones.
                val old = (original[offset].toInt() and 255) or ((original[offset+1].toInt() and 255) shl 8)
                if (value == 0 && old == 65535 && original[at] != 0xff.toByte()) return
                result[offset] = value.toByte()
                result[offset+1] = (value shr 8).toByte()
            }
            require(uv32 || ch.power != PowerLevel.MID) { "Mini supports High or Low power" }
            val previous = baseline[slot]
            if (previous?.rxFreqHz != ch.rxFreqHz) frequency(at, ch.rxFreqHz)
            if (previous != null && previous.duplex == ch.duplex && previous.txFreqHz == ch.txFreqHz) {
                // Keep the original transmitter field when editing unrelated channel settings.
            } else if (ch.duplex == DuplexMode.OFF) result.fill(0xff.toByte(), at+4, at+8)
            else {
                require((uv32 && ch.mode != "AM" && (ch.txFreqHz in 220000000L..260000000L || ch.txFreqHz in 350000000L..390000000L || ch.txFreqHz in 400000000L..520000000L)) ||
                    ch.mode != "AM" && ch.txFreqHz in 136000000L..174000000L ||
                    ch.mode != "AM" && ch.txFreqHz in 400000000L..480000000L) { "Channel ${ch.number} (${ch.name}): $model TX ${ch.txFreqHz / 1000000.0} MHz / ${ch.mode} unsupported; RX ${ch.rxFreqHz / 1000000.0} MHz" }
                frequency(at+4, ch.txFreqHz)
            }
            if (previous?.rxTone != ch.rxTone) tone(at+8, ch.rxTone)
            if (previous?.txTone != ch.txTone) tone(at+10, ch.txTone)
            val oldLow = (original[at+14].toInt() and 3) == 1
            if ((uv32 && previous?.power != ch.power) || original[at] == 0xff.toByte() || oldLow != (ch.power == PowerLevel.LOW)) result[at+14] = ((result[at+14].toInt() and 0xfc) or when (ch.power) { PowerLevel.LOW -> 1; PowerLevel.MID -> 2; else -> 0 }).toByte()
            if(ch.mode != "AM" && previous?.mode != ch.mode) result[at+15] = ((result[at+15].toInt() and 0xbf) or if(ch.mode == "FM") 0x40 else 0).toByte()
            fun flag(mask: Int, enabled: Boolean?) {
                if(enabled!=null) result[at+15] = ((result[at+15].toInt() and mask.inv()) or if(enabled) mask else 0).toByte()
            }
            flag(4,ch.scanIncluded)
            flag(8,ch.busyChannelLockout)
            flag(1,ch.fhssPrivacy)
            ch.pttId?.let { require(it in 0..3) { "Invalid PTT ID mode" };result[at+13]=it.toByte() }
            val oldName = original.copyOfRange(at+20,at+32).takeWhile { it != 0.toByte() && it != 0xff.toByte() }.map { (it.toInt() and 255).toChar() }.joinToString("").trim().ifBlank { "CH $slot" }
            val unchangedName = ch.name == (previous?.name ?: oldName)
            if (!unchangedName || original[at] == 0xff.toByte()) {
                result.fill(0xff.toByte(),at+20,at+32)
                val radioName = if (uv32) {
                    ch.name.map { if (it in ' '..'~') it else ' ' }.joinToString("").trim()
                        .take(12).trimEnd().ifBlank { "CH $slot" }
                } else ch.name
                radioName.toByteArray(Charsets.US_ASCII).copyInto(result, at + 20)
            }
        }
        return result
    }
}
