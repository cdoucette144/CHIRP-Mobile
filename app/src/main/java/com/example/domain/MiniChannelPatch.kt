package com.example.domain

import com.example.data.model.*
import java.util.Locale

/** Overlay editable fields on the Mini's 32-byte records; preserve all other bytes. */
object MiniChannelPatch {
    fun apply(original: ByteArray, channels: List<RadioChannel>): ByteArray {
        require(original.size == 0x8240) { "Full Mini image required" }
        require(channels.map { it.number }.distinct().size == channels.size) { "Duplicate channel slots" }
        require(channels.all { it.number in 1..999 }) { "Mini slots must be 1–999" }
        val result = original.copyOf()
        val bySlot = channels.associateBy { it.number }
        for (slot in 1..999) {
            val at = (slot - 1) * 32
            val ch = bySlot[slot]
            if (ch == null) {
                if (original[at] != 0xff.toByte()) result.fill(0xff.toByte(), at, at + 32)
                continue
            }
            require(ch.mode in listOf("FM", "NFM", "AM")) { "Analog modes only" }
            require(ch.name.length <= 12 && ch.name.all { it.code in 32..126 }) { "Mini names require at most 12 ASCII characters" }
            if (original[at] == 0xff.toByte()) {
                result.fill(0, at, at + 20)
                result[at+15] = 4 // include new channel in scanning
            }
            fun frequency(offset: Int, hz: Long) {
                require(hz in 108000000L..520000000L && hz % 10 == 0L) { "Invalid Mini frequency" }
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
            require(ch.power != PowerLevel.MID) { "Mini supports High or Low power" }
            frequency(at, ch.rxFreqHz)
            if (ch.duplex == DuplexMode.OFF) result.fill(0xff.toByte(), at+4, at+8)
            else {
                require(ch.mode != "AM" && ch.txFreqHz in 136000000L..174000000L ||
                    ch.mode != "AM" && ch.txFreqHz in 400000000L..480000000L) { "Channel ${ch.number} (${ch.name}): Mini TX ${ch.txFreqHz / 1000000.0} MHz / ${ch.mode} unsupported; RX ${ch.rxFreqHz / 1000000.0} MHz" }
                frequency(at+4, ch.txFreqHz)
            }
            tone(at+8, ch.rxTone)
            tone(at+10, ch.txTone)
            val oldLow = (original[at+14].toInt() and 3) == 1
            if (original[at] == 0xff.toByte() || oldLow != (ch.power == PowerLevel.LOW)) result[at+14] = ((result[at+14].toInt() and 0xfc) or if(ch.power == PowerLevel.LOW) 1 else 0).toByte()
            if(ch.mode != "AM") result[at+15] = ((result[at+15].toInt() and 0xbf) or if(ch.mode == "FM") 0x40 else 0).toByte()
            val oldName = original.copyOfRange(at+20,at+32).takeWhile { it != 0.toByte() && it != 0xff.toByte() }.map { (it.toInt() and 255).toChar() }.joinToString("").trim().ifBlank { "CH $slot" }
            if (ch.name != oldName || original[at] == 0xff.toByte()) {
                result.fill(0xff.toByte(),at+20,at+32)
                ch.name.toByteArray(Charsets.US_ASCII).copyInto(result,at+20)
            }
        }
        return result
    }
}
