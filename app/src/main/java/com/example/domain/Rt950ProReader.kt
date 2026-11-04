package com.example.domain

import com.example.data.model.DuplexMode
import com.example.data.model.PowerLevel
import com.example.data.model.RadioChannel
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.abs

/** RT-950 Pro clone protocol for Bluetooth downloads and USB settings transfers. */
internal object Rt950ProReader {
    class MissingHandshakeAck(message: String) : IOException(message)

    val unlockFrame: ByteArray
        get() = byteArrayOf(0x3f, 0x3f, 0x3f, 0x3f, 0x02, 0x2e, 0x17, 0x1d, 0x5e, 0x57,
            0x25, 0x2f, 0x57, 0x13, 0x62, 0x56, 0x04, 0x4b, 0x23, 0x42)

    private data class Region(val command: Int, val start: Int, val length: Int)

    private val regions = listOf(
        Region(0x52, 0, 0x7c00), Region(0x52, 0x8000, 0x100), Region(0x52, 0x9000, 0x100),
        Region(0x52, 0xa000, 0x200), Region(0x52, 0xb000, 0x200), Region(0x52, 0xd000, 0x300), Region(0x52, 0xc000, 0x100),
        Region(0x54, 0, 0x80)
    )

    fun settingsBlocks(original: ByteArray, desired: ByteArray): Map<Int, ByteArray> =
        mapOf(0x9000 to 0x7d00, 0xc000 to 0x8500, 0xc080 to 0x8580).mapNotNull { (address, offset) ->
            val data = desired.copyOfRange(offset, offset + 128)
            if (data.contentEquals(original.copyOfRange(offset, offset + 128))) null else address to data
        }.toMap()

    suspend fun download(transport: RadioSerialTransport, progress: (Float, String) -> Unit): ByteArray =
        transfer(transport, emptyMap(), progress)

    suspend fun writeSettings(
        transport: RadioSerialTransport,
        blocks: Map<Int, ByteArray>,
        progress: (Float, String) -> Unit
    ) {
        require(blocks.isNotEmpty() && blocks.all { (address, data) ->
            address in setOf(0x9000, 0xc000, 0xc080) && data.size == 128
        })
        transfer(transport, blocks, progress)
    }

    private suspend fun transfer(
        transport: RadioSerialTransport,
        settingsWrites: Map<Int, ByteArray>,
        progress: (Float, String) -> Unit
    ): ByteArray {
        fun ascii(text: String) = text.toByteArray(Charsets.US_ASCII)
        suspend fun expect(count: Int, label: String, timeout: Long = 3000): ByteArray {
            val reply = transport.readBytes(count, timeout)
            if (reply.size != count) throw IOException("RT-950 Pro $label received ${reply.size}/$count bytes.")
            return reply
        }
        suspend fun ack(label: String, timeout: Long = 3000) {
            val reply = expect(1, label, timeout)[0]
            if (reply != 6.toByte()) throw IOException("RT-950 Pro $label rejected (reply %02X).".format(reply))
        }
        transport.clearRxBuffer()
        val magic = ascii("PROGRAMBT9000U")
        transport.writeBytes(magic)
        var reply = transport.readBytes(1, 3000)
        var echoed = 0
        while (echoed < magic.size && reply.firstOrNull() == magic[echoed]) {
            echoed++
            reply = transport.readBytes(1, 3000)
        }
        if (echoed > 0) {
            android.util.Log.i("ChirpBluetooth", "RT-950 Pro handshake echoed $echoed/${magic.size} command bytes.")
        }
        if (reply.firstOrNull() != 6.toByte()) {
            val detail = reply.firstOrNull()?.let { "reply %02X".format(it) } ?: "no ACK"
            val message = "RT-950 Pro handshake failed ($detail after $echoed echoed bytes)."
            if (reply.isEmpty()) throw MissingHandshakeAck(message)
            throw IOException(message)
        }
        suspend fun send(command: ByteArray) {
            transport.writeBytes(command)
            if (echoed == magic.size && !expect(command.size, "command echo").contentEquals(command)) {
                throw IOException("RT-950 Pro serial echo did not match the command.")
            }
        }
        try {
            // The initial ACK is the entire reply. Discard any trailing startup bytes
            // before asking for the separately framed firmware identification.
            transport.clearRxBuffer()
            send(ascii("F"))
            var firmware = expect(16, "firmware identification")
            repeat(4) {
                if (firmware.first() == 6.toByte()) firmware = firmware.copyOfRange(1, 16) + expect(1, "firmware tail")
            }
            send(ascii("M"))
            val model = expect(12, "model identification").toString(Charsets.US_ASCII).trim { it <= ' ' }
            val normalized = model.uppercase().filter { it.isLetterOrDigit() }
            if (normalized !in setOf("RT950", "RT950PRO", "RT950PROBLE")) throw IOException("Unexpected RT-950 Pro identifier: $model")
            progress(0.12f, "[RT-950 PRO] Identified $model")
            // Selector 0x10 uses byte 5 as the table index. Index 1 selects CO 7.
            val init = ByteArray(25)
            ascii("SEND").copyInto(init)
            init[4] = 0x10
            init[5] = 1
            send(init)
            ack("session initialization")
            val key = ascii("CO 7")
            fun crypt(byte: Byte, index: Int): Int {
                val value = byte.toInt() and 255
                val k = key[index % 4].toInt()
                return if (k != 32 && value != 0 && value != 255 && value != k && value != (k xor 255)) value xor k else value
            }
            if (settingsWrites.isNotEmpty()) {
                for ((address, data) in settingsWrites) {
                    val header = byteArrayOf(0x57, (address shr 8).toByte(), address.toByte(), 0x80.toByte())
                    send(header + ByteArray(data.size) { crypt(data[it], it).toByte() })
                    ack("settings write %04X".format(address), 10_000)
                    kotlinx.coroutines.delay(50)
                    val message = "[RT-950 PRO WRITE %04X] acknowledged".format(address)
                    android.util.Log.i("ChirpBluetooth", message)
                    progress(0.65f, message)
                }
                return byteArrayOf()
            }
            val image = ByteArrayOutputStream()
            val total = regions.sumOf { it.length }
            for ((command, start, length) in regions) {
                for (address in start until start + length step 128) {
                    val request = byteArrayOf(command.toByte(), (address shr 8).toByte(), address.toByte(), 0x80.toByte())
                    send(request)
                    val reply = expect(132, "read %02X:%04X".format(command, address))
                    if (!reply.copyOfRange(0, 4).contentEquals(request)) {
                        throw IOException("Unexpected RT-950 Pro response header at %02X:%04X.".format(command, address))
                    }
                    for (index in 0 until 128) {
                        image.write(crypt(reply[index + 4], index))
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

    private data class Setting(val offset: Int, val label: String, val options: List<String>)

    private val offOn = listOf("Off", "On")
    private val levels = (0..9).map(Int::toString)
    private val displays = listOf("Channel", "Frequency", "Name")
    private val functionSettings = listOf(
        Setting(0, "Squelch", levels),
        Setting(1, "Battery Save", (0..3).map(Int::toString)),
        Setting(2, "VOX Gain", levels),
        Setting(3, "Auto Backlight", levels),
        Setting(4, "Dual Watch", offOn),
        Setting(5, "Transmit Timeout", levels),
        Setting(6, "Key Beep", offOn),
        Setting(7, "Voice Prompt", listOf("Off", "English", "Chinese")),
        Setting(8, "Menu Language", listOf("English", "Chinese", "Other")),
        Setting(9, "DTMF Mode", listOf("Off", "DT-ST", "ANI-ID", "DTMF")),
        Setting(10, "Scan Resume", listOf("Time", "Carrier", "Search")),
        Setting(11, "PTT ID", listOf("Off", "Start", "End", "Both")),
        Setting(13, "Display A", displays),
        Setting(14, "Display B", displays),
        Setting(15, "Display C", displays),
        Setting(16, "Auto Key Lock", offOn),
        Setting(17, "Alarm Mode", listOf("Local", "Remote", "Tone")),
        Setting(18, "Alarm Sound", listOf("Off", "Type 1", "Type 2")),
        Setting(20, "Tail Noise Clear", offOn),
        Setting(23, "Roger Beep", offOn),
        Setting(25, "FM Radio", offOn),
        Setting(27, "Keypad Lock", offOn),
        Setting(28, "Power-on Display", listOf("Full", "Message", "Voltage")),
        Setting(32, "VOX Delay", levels),
        Setting(0x26, "Channel Divide", listOf("Zones", "All Channels")),
        Setting(0x28, "VOX Switch", offOn)
    )

    val settingOptions: Map<String, List<String>> = functionSettings.associate { it.label to it.options }

    fun settings(image: ByteArray): Map<String, String> = buildMap {
        for ((offset, label, options) in functionSettings) {
            val raw = image[0x7d00 + offset].toInt() and 255
            if (raw != 255) options.getOrNull(raw and 15)?.let { put(label, it) }
        }
    }

    fun patchSettings(image: ByteArray, values: Map<String, String>, names: List<String>): ByteArray {
        val original = settings(image)
        require(values.keys == original.keys) { "Download the RT-950 Pro settings again before uploading." }
        val result = image.copyOf()
        for ((offset, label, options) in functionSettings) {
            val value = values[label] ?: continue
            if (original[label] == value) continue
            val index = options.indexOf(value)
            require(index >= 0) { "Invalid RT-950 Pro setting: $label." }
            val address = 0x7d00 + offset
            result[address] = ((result[address].toInt() and 0xf0) or index).toByte()
        }
        val originalNames = zoneNames(image)
        require(names.size == originalNames.size)
        names.forEachIndexed { index, name ->
            if (name == originalNames[index]) return@forEachIndexed
            require(name.length <= 16 && name.all { it in ' '..'~' }) { "Zone names allow up to 16 printable ASCII characters." }
            val offset = 0x8500 + index * 16
            result.fill(0xff.toByte(), offset, offset + 16)
            name.toByteArray(Charsets.US_ASCII).copyInto(result, offset)
        }
        return result
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
