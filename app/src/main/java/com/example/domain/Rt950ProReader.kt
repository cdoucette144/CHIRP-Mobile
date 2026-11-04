package com.example.domain

import com.example.data.model.DuplexMode
import com.example.data.model.PowerLevel
import com.example.data.model.RadioChannel
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.abs

/** RT-950 Pro Bluetooth read protocol. Only clone entry, reads and clone exit are supported. */
internal object Rt950ProReader {
    val unlockFrame: ByteArray
        get() = byteArrayOf(0x3f, 0x3f, 0x3f, 0x3f, 0x02, 0x2e, 0x17, 0x1d, 0x5e, 0x57,
            0x25, 0x2f, 0x57, 0x13, 0x62, 0x56, 0x04, 0x4b, 0x23, 0x42)

    private data class Region(val command: Int, val start: Int, val length: Int)

    private val regions = listOf(
        Region(0x52, 0, 0x7c00), Region(0x52, 0x8000, 0x100), Region(0x52, 0x9000, 0x100),
        Region(0x52, 0xa000, 0x200), Region(0x52, 0xb000, 0x200), Region(0x52, 0xd000, 0x300), Region(0x52, 0xc000, 0x100),
        Region(0x54, 0, 0x80)
    )

    suspend fun download(transport: RadioSerialTransport, progress: (Float, String) -> Unit): ByteArray {
        fun ascii(text: String) = text.toByteArray(Charsets.US_ASCII)
        suspend fun expect(count: Int, label: String): ByteArray {
            val reply = transport.readBytes(count, 3000)
            if (reply.size != count) throw IOException("RT-950 Pro $label received ${reply.size}/$count bytes.")
            return reply
        }
        suspend fun ack(label: String) {
            if (expect(1, label)[0] != 6.toByte()) throw IOException("RT-950 Pro $label rejected.")
        }
        transport.clearRxBuffer()
        transport.writeBytes(ascii("PROGRAMBT9000U"))
        ack("handshake")
        try {
            transport.writeBytes(ascii("F"))
            var firmware = expect(16, "firmware identification")
            repeat(4) {
                if (firmware.first() == 6.toByte()) firmware = firmware.copyOfRange(1, 16) + expect(1, "firmware tail")
            }
            transport.writeBytes(ascii("M"))
            val model = expect(12, "model identification").toString(Charsets.US_ASCII).trim { it <= ' ' }
            val normalized = model.uppercase().filter { it.isLetterOrDigit() }
            if (normalized !in setOf("RT950", "RT950PRO", "RT950PROBLE")) throw IOException("Unexpected RT-950 Pro identifier: $model")
            progress(0.12f, "[RT-950 PRO] Identified $model")
            // Selector 0x10 uses byte 5 as the table index. Index 1 selects CO 7.
            val init = ByteArray(25)
            ascii("SEND").copyInto(init)
            init[4] = 0x10
            init[5] = 1
            transport.writeBytes(init)
            ack("session initialization")
            val key = ascii("CO 7")
            val image = ByteArrayOutputStream()
            val total = regions.sumOf { it.length }
            for ((command, start, length) in regions) {
                for (address in start until start + length step 128) {
                    val request = byteArrayOf(command.toByte(), (address shr 8).toByte(), address.toByte(), 0x80.toByte())
                    transport.writeBytes(request)
                    val reply = expect(132, "read %02X:%04X".format(command, address))
                    if (!reply.copyOfRange(0, 4).contentEquals(request)) {
                        throw IOException("Unexpected RT-950 Pro response header at %02X:%04X.".format(command, address))
                    }
                    for (index in 0 until 128) {
                        val value = reply[index + 4].toInt() and 255
                        val k = key[index % 4].toInt()
                        image.write(if (k != 32 && value != 0 && value != 255 && value != k && value != (k xor 255)) value xor k else value)
                    }
                    progress(0.12f + 0.85f * image.size() / total,
                        "[RT-950 PRO READ %02X:%04X] %d / %d bytes".format(command, address, image.size(), total))
                }
            }
            return image.toByteArray()
        } finally {
            try {
                transport.writeBytes(ascii("E"))
            } catch (error: IOException) {
                android.util.Log.w("ChirpBluetooth", "RT-950 Pro clone exit could not be sent", error)
            }
        }
    }

    fun zoneNames(image: ByteArray): List<String> = List(10) { index ->
        val offset = 0x8500 + index * 16
        image.copyOfRange(offset, offset + 16)
            .takeWhile { it != 0.toByte() && it != 0xff.toByte() }.toByteArray()
            .toString(charset("GB2312")).trim()
    }

    fun settings(image: ByteArray): Map<String, String> = buildMap {
        fun show(offset: Int, label: String, options: List<String> = emptyList()) {
            val value = image[0x7d00 + offset].toInt() and 255
            if (value != 255) put(label, options.getOrNull(value) ?: value.toString())
        }
        val offOn = listOf("Off", "On")
        val displays = listOf("Channel", "Frequency", "Name")
        show(0, "Squelch")
        show(1, "Battery Save")
        show(2, "VOX Gain")
        show(3, "Auto Backlight")
        show(4, "Dual Watch", offOn)
        show(5, "Transmit Timeout")
        show(6, "Key Beep", offOn)
        show(7, "Voice Prompt", listOf("Off", "English", "Chinese"))
        show(8, "Menu Language", listOf("English", "Chinese", "Other"))
        show(9, "DTMF Mode", listOf("Off", "DT-ST", "ANI-ID", "DTMF"))
        show(10, "Scan Resume", listOf("Time", "Carrier", "Search"))
        show(11, "PTT ID", listOf("Off", "Start", "End", "Both"))
        show(13, "Display A", displays)
        show(14, "Display B", displays)
        show(15, "Display C", displays)
        show(16, "Auto Key Lock", offOn)
        show(0x26, "Channel Divide", listOf("Zones", "All Channels"))
        show(0x28, "VOX Switch", offOn)
    }

    fun decodeChannels(image: ByteArray): List<RadioChannel> = (0 until 990).mapNotNull { index ->
        val start = index * 32
        fun byte(offset: Int) = image[start + offset].toInt() and 255
        fun frequency(offset: Int): Long? {
            var value = 0L
            for (i in 3 downTo 0) {
                val part = byte(offset + i)
                val high = part shr 4
                val low = part and 15
                if (high > 9 || low > 9) return null
                value = value * 100 + high * 10 + low
            }
            return (value * 10).takeIf { it > 0 }
        }
        fun tone(offset: Int): String {
            val value = byte(offset) or (byte(offset + 1) shl 8)
            return if (value in 600..3000) (value / 10.0).toString() else ""
        }
        val rx = frequency(0) ?: return@mapNotNull null
        val tx = frequency(4)
        val flags = byte(15)
        val rxOnly = tx == null || flags and 2 == 0
        val name = image.copyOfRange(start + 20, start + 32)
            .takeWhile { it != 0.toByte() && it != 0xff.toByte() }.toByteArray()
            .toString(charset("GB2312")).trim().ifBlank { "CH ${index + 1}" }
        RadioChannel(
            number = index + 1, name = name, rxFreqHz = rx, txFreqHz = tx ?: rx,
            duplex = if (rxOnly) DuplexMode.OFF else if (tx == rx) DuplexMode.SIMPLEX else DuplexMode.SPLIT,
            offsetHz = abs((tx ?: rx) - rx), rxTone = tone(8), txTone = tone(10),
            mode = when {
                flags and 1 != 0 -> "AM"
                flags and 0x40 != 0 -> "NFM"
                else -> "FM"
            },
            power = when (byte(14) and 15) { 0 -> PowerLevel.HIGH; 1 -> PowerLevel.MID; else -> PowerLevel.LOW },
            rxOnly = rxOnly, scanIncluded = flags and 4 != 0, busyChannelLockout = flags and 8 != 0
        )
    }
}
