package com.example.domain

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import com.example.data.model.DuplexMode
import com.example.data.model.PowerLevel
import com.example.data.model.RadioChannel
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

enum class TraceDirection {
    TX, RX, INFO, ERROR
}

data class HexTraceEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val formattedTime: String = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(timestamp)),
    val direction: TraceDirection,
    val sourceOrTarget: String,
    val hexString: String,
    val asciiString: String
)

data class DiscoveredGattChar(
    val uuid: String,
    val shortUuid: String,
    val properties: Int,
    val canRead: Boolean,
    val canWrite: Boolean,
    val canWriteNoResponse: Boolean,
    val canNotify: Boolean,
    val canIndicate: Boolean
)

data class DiscoveredGattService(
    val uuid: String,
    val shortUuid: String,
    val isKnownRadioService: Boolean,
    val serviceLabel: String,
    val characteristics: List<DiscoveredGattChar>
)

data class RadioIdentResult(
    val success: Boolean,
    val ackReceived: Boolean,
    val signatureHex: String,
    val signatureAscii: String,
    val details: String,
    val boundServiceUuid: String,
    val boundTxUuid: String,
    val boundRxUuid: String,
    val sha256BackupHash: String = ""
)

data class DetectedUsbCable(
    val deviceName: String,
    val vendorId: Int,
    val productId: Int,
    val chipsetName: String,
    val hasPermission: Boolean
)

data class DetectedBleRadio(
    val name: String,
    val address: String,
    val rssi: Int,
    val isRadioProgrammer: Boolean
)

/**
 * Abstracts a real byte-level serial transport over either Bluetooth LE GATT UART,
 * Classic Bluetooth RFCOMM SPP, or USB OTG Serial (CH340 / FTDI / PL2303 / CP210x).
 */
interface RadioSerialTransport {
    suspend fun writeBytes(data: ByteArray)
    suspend fun readBytes(expectedCount: Int, timeoutMs: Long): ByteArray
    fun clearRxBuffer()
    fun close()
}

class HardwareProgrammerManager(private val context: Context) {
    private var lastMiniImage: ByteArray? = null
    private var lastUv32Image: ByteArray? = null
    private var lastUv32Extras: ByteArray? = null
    private var uv32Baseline: Triple<String, ByteArray, List<RadioChannel>>? = null
    var lastUv32Settings: com.example.data.model.Uv32SettingsSnapshot? = null
        private set
    var lastPersistenceMessage: String? = null
        private set

    fun scanUsbOtgDevices(): List<DetectedUsbCable> {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return emptyList()
        val deviceList: HashMap<String, UsbDevice> = usbManager.deviceList ?: return emptyList()
        return deviceList.values.map { device ->
            val chipset = identifySerialChipset(device.vendorId, device.productId)
            DetectedUsbCable(
                deviceName = device.deviceName,
                vendorId = device.vendorId,
                productId = device.productId,
                chipsetName = "${device.productName ?: chipset} ($chipset)",
                hasPermission = usbManager.hasPermission(device)
            )
        }
    }

    fun requestUsbPermission(deviceName: String): Boolean {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return false
        val device = usbManager.deviceList?.get(deviceName) ?: usbManager.deviceList?.values?.firstOrNull() ?: return false
        if (usbManager.hasPermission(device)) return true
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pi = PendingIntent.getBroadcast(
            context,
            0,
            Intent("com.aistudio.chirpfield.USB_PERMISSION").setPackage(context.packageName),
            flags
        )
        usbManager.requestPermission(device, pi)
        return false
    }

    private fun identifySerialChipset(vid: Int, pid: Int): String = when (vid) {
        0x0403 -> "FTDI FT232R / FT231X K-Plug Cable"
        0x1A86 -> "QinHeng CH340 / CH341 USB-Serial"
        0x067B -> "Prolific PL2303 Programming Cable"
        0x10C4 -> "Silicon Labs CP2102 / CP210x"
        else -> String.format(Locale.US, "USB Serial Adapter (%04X:%04X)", vid, pid)
    }

    @SuppressLint("MissingPermission")
    fun startBleScan(
        onDeviceFound: (DetectedBleRadio) -> Unit,
        onError: (String) -> Unit
    ): (() -> Unit) {
        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = btManager?.adapter
        if (adapter == null || !adapter.isEnabled) {
            onError("Bluetooth is turned off or unavailable.")
            return {}
        }

        // Also surface bonded Bluetooth radios immediately so paired UV-5R Mini / dongles show up right away
        try {
            adapter.bondedDevices?.forEach { bonded ->
                val bName = bonded.name ?: "Paired-${bonded.address.takeLast(5)}"
                val isKnown = isLikelyRadioDeviceName(bName)
                onDeviceFound(
                    DetectedBleRadio(
                        name = "$bName (Paired)",
                        address = bonded.address,
                        rssi = -50,
                        isRadioProgrammer = isKnown
                    )
                )
            }
        } catch (_: SecurityException) {
        }

        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            onError("Bluetooth LE scanner unavailable.")
            return {}
        }

        val callback = object : ScanCallback() {
            @SuppressLint("MissingPermission")
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                val dev = result?.device ?: return
                val devName = dev.name
                    ?: result.scanRecord?.deviceName
                    ?: "BLE-Device-${dev.address.takeLast(5)}"
                onDeviceFound(
                    DetectedBleRadio(
                        name = devName,
                        address = dev.address,
                        rssi = result.rssi,
                        isRadioProgrammer = isLikelyRadioDeviceName(devName)
                    )
                )
            }

            override fun onScanFailed(errorCode: Int) {
                onError("BLE scan failed (code $errorCode).")
            }
        }

        val stopAction: () -> Unit = {
            try {
                scanner.stopScan(callback)
            } catch (_: Exception) {
            }
        }
        val noopAction: () -> Unit = {}

        return try {
            scanner.startScan(callback)
            stopAction
        } catch (e: SecurityException) {
            onError("Bluetooth scan permission required: ${e.localizedMessage}")
            noopAction
        } catch (e: Exception) {
            onError("Unable to start BLE scan: ${e.localizedMessage}")
            noopAction
        }
    }

    private fun isLikelyRadioDeviceName(name: String): Boolean {
        val upper = name.uppercase(Locale.US)
        return upper.contains("UV") ||
            upper.contains("BF") ||
            upper.contains("MINI") ||
            upper.contains("BAOFENG") ||
            upper.contains("OLA") ||
            upper.contains("TD-H") ||
            upper.contains("TID") ||
            upper.contains("ODMASTER") ||
            upper.contains("BTECH") ||
            upper.contains("QUANSHENG") ||
            upper.contains("BLE")
    }

    /**
     * Opens a live BLE GATT transparent UART or Classic SPP connection to [bleAddress].
     * Discovers all GATT services/characteristics, logs their exact UUIDs for transparency,
     * subscribes to CCCD 0x2902 notifications, and returns a live [RadioSerialTransport].
     */
    @SuppressLint("MissingPermission")
    private suspend fun openBleOrSppTransport(
        bleAddress: String,
        configureUv32Baud: Boolean = false,
        onLog: (String) -> Unit
    ): RadioSerialTransport = withContext(Dispatchers.IO) {
        if (bleAddress.isBlank()) {
            throw IOException("No Bluetooth radio selected. Tap Find radios, then select your radio.")
        }
        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter: BluetoothAdapter = btManager?.adapter
            ?: throw IOException("Bluetooth adapter unavailable on this device.")
        if (!adapter.isEnabled) {
            throw IOException("Bluetooth is turned off. Enable Bluetooth and try again.")
        }

        val device: BluetoothDevice = try {
            adapter.getRemoteDevice(bleAddress)
        } catch (e: IllegalArgumentException) {
            throw IOException("Invalid Bluetooth MAC address: $bleAddress")
        }

        android.util.Log.i("ChirpBluetooth", "[BLE] Opening programming transport")
        onLog("[BLE] Connecting to ${device.name ?: bleAddress} ($bleAddress)...")

        val rxQueue = LinkedBlockingQueue<Byte>()
        val servicesReady = CompletableDeferred<BluetoothGatt>()
        val descriptorReady = CompletableDeferred<Boolean>()
        val mtuReady = CompletableDeferred<Int>()
        val writeAckSignal = LinkedBlockingQueue<Int>()

        val gattCallback = object : BluetoothGattCallback() {
            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                mtuReady.complete(if (status == BluetoothGatt.GATT_SUCCESS) mtu else 23)
            }

            @SuppressLint("MissingPermission")
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    onLog("[BLE] Connected to GATT server. Discovering UART services...")
                    try {
                        gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                    } catch (_: Exception) {
                    }
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    if (!servicesReady.isCompleted) {
                        servicesReady.completeExceptionally(
                            IOException("Radio disconnected during GATT setup (status=$status). Ensure the UV-5R Mini is awake and in Bluetooth mode.")
                        )
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    servicesReady.complete(gatt)
                } else {
                    servicesReady.completeExceptionally(
                        IOException("GATT service discovery failed with status $status")
                    )
                }
            }

            override fun onDescriptorWrite(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int
            ) {
                descriptorReady.complete(status == BluetoothGatt.GATT_SUCCESS)
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                writeAckSignal.offer(status)
            }

            @Deprecated("Deprecated in API 33")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                @Suppress("DEPRECATION")
                val bytes = characteristic.value ?: return
                for (b in bytes) {
                    rxQueue.offer(b)
                }
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                for (b in value) {
                    rxQueue.offer(b)
                }
            }
        }

        val gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, gattCallback)
        } ?: throw IOException("Failed to initialize BluetoothGatt connection.")

        val connectedGatt = withTimeoutOrNull(8000L) {
            servicesReady.await()
        }
        if (connectedGatt == null) {
            try {
                gatt.disconnect()
                gatt.close()
            } catch (_: Exception) {
            }
            // Attempt Classic RFCOMM SPP fallback in case the adapter uses SPP
            return@withContext openClassicSppFallback(device, onLog)
        }

        // Inspect all discovered GATT services and locate UART Write + Notify characteristics
        var writeChar: BluetoothGattCharacteristic? = null
        var notifyChar: BluetoothGattCharacteristic? = null

        val customServices = connectedGatt.services.filter { svc ->
            val u = svc.uuid.toString().lowercase(Locale.US)
            !u.startsWith("00001800-") && !u.startsWith("00001801-") && !u.startsWith("0000180a-")
        }

        for (svc in customServices) {
            for (ch in svc.characteristics) {
                android.util.Log.i("ChirpBluetooth", "service=${svc.uuid} char=${ch.uuid} properties=${ch.properties}")
                onLog("[GATT] service=${svc.uuid} char=${ch.uuid} properties=0x${ch.properties.toString(16)}")
            }
        }
        // UV-5R Mini programming UART: FFE0 / FFE1. Vendor control
        // services (such as AE00) are not interchangeable serial ports.
        val uart = connectedGatt.getService(UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb"))
            ?.getCharacteristic(UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb"))
        if (uart != null) {
            val props = uart.properties
            if ((props and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0 &&
                (props and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE)) != 0) {
                writeChar = uart
                notifyChar = uart
            }
        }
        if (writeChar == null || notifyChar == null) {
            connectedGatt.disconnect()
            connectedGatt.close()
            throw IOException("Selected Bluetooth device does not expose the expected programming UART (FFE0/FFE1). Select the radio's programming device, not its audio/control connection. Discovered services: " + customServices.joinToString { it.uuid.toString().take(8) })
        }

        val finalWriteChar = writeChar!!
        val finalNotifyChar = notifyChar!!
        onLog(
            "[GATT UART] Bound TX=${finalWriteChar.uuid.toString().uppercase(Locale.US).take(8)} / RX=${finalNotifyChar.uuid.toString().uppercase(Locale.US).take(8)}"
        )

        // Enable notifications on finalNotifyChar
        if (!connectedGatt.setCharacteristicNotification(finalNotifyChar, true)) throw IOException("Unable to enable Mini UART notifications.")
        val cccd = finalNotifyChar.getDescriptor(CCCD_UUID)
        if (cccd != null) {
            @Suppress("DEPRECATION")
            cccd.value = if ((finalNotifyChar.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            @Suppress("DEPRECATION")
            if (!connectedGatt.writeDescriptor(cccd)) throw IOException("Bluetooth notification subscription could not start.")
            if (withTimeoutOrNull(2500L) { descriptorReady.await() } != true) {
                connectedGatt.disconnect()
                connectedGatt.close()
                throw IOException("Bluetooth notification subscription failed. Reconnect the radio.")
            }
            onLog("[GATT CCCD] Subscribed to UART RX notifications (0x2902).")
        }

        if (configureUv32Baud) {
            try {
                val baudControl = connectedGatt.getService(finalWriteChar.service.uuid)
                    ?.getCharacteristic(UUID.fromString("0000ff31-0000-1000-8000-00805f9b34fb"))
                    ?: throw IOException("UV-32 Bluetooth baud control (FF31) is missing.")
                if (baudControl.properties and BluetoothGattCharacteristic.PROPERTY_WRITE == 0) {
                    throw IOException("UV-32 Bluetooth baud control does not support acknowledged writes.")
                }
                // The FFE0 bridge uses a 20-byte configuration frame.
                // Byte 4 (zero-based) selects the baud rate: 2 means 115200.
                val baudBytes = ByteArray(20).apply { this[4] = 0x02 }
                writeAckSignal.clear()
                @Suppress("DEPRECATION")
                baudControl.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                @Suppress("DEPRECATION")
                baudControl.value = baudBytes
                @Suppress("DEPRECATION")
                val started = connectedGatt.writeCharacteristic(baudControl)
                if (!started) throw IOException("Could not start the UV-32 Bluetooth baud setup.")
                val status = writeAckSignal.poll(2500, TimeUnit.MILLISECONDS)
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    throw IOException("UV-32 Bluetooth baud setup failed (status=$status).")
                }
                android.util.Log.i("ChirpBluetooth", "UV-32 FF31 GATT write accepted (20-byte frame, baud selector 02)")
                onLog("[UV-32] Bluetooth bridge configured for 115200 baud.")
            } catch (error: Exception) {
                connectedGatt.disconnect()
                connectedGatt.close()
                throw error
            }
        }

        val mtu = if (configureUv32Baud && connectedGatt.requestMtu(247)) {
            withTimeoutOrNull(2500L) { mtuReady.await() } ?: 23
        } else 23
        if (configureUv32Baud) android.util.Log.i("ChirpBluetooth", "UV-32 BLE MTU: $mtu")

        delay(500)

        object : RadioSerialTransport {
            @SuppressLint("MissingPermission")
            override suspend fun writeBytes(data: ByteArray) {
                // Ola fragments UV-32 writes at the negotiated MTU minus seven.
                val uv32Block = configureUv32Baud && data.size == 132 && data[0] == 0x57.toByte()
                val chunkSize = if (uv32Block) (mtu - 7).coerceAtLeast(16) else 20
                var offset = 0
                while (offset < data.size) {
                    val end = (offset + chunkSize).coerceAtMost(data.size)
                    val chunk = data.copyOfRange(offset, end)
                    writeAckSignal.clear()
                    val useNoResponse =
                        (finalWriteChar.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0 &&
                            (!uv32Block || (finalWriteChar.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) == 0)
                    @Suppress("DEPRECATION")
                    finalWriteChar.writeType = if (useNoResponse) {
                        BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    } else {
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    }
                    @Suppress("DEPRECATION")
                    finalWriteChar.value = chunk
                    @Suppress("DEPRECATION")
                    val started = connectedGatt.writeCharacteristic(finalWriteChar)
                    if (!started) {
                        throw IOException("BLE GATT writeCharacteristic failed at byte offset $offset.")
                    }
                    if (!useNoResponse) {
                        withContext(Dispatchers.IO) {
                            val status = writeAckSignal.poll(1500, TimeUnit.MILLISECONDS)
                            if (status != BluetoothGatt.GATT_SUCCESS) throw IOException("Bluetooth write failed or timed out (status=$status)")
                        }
                    } else {
                        delay(18)
                    }
                    offset = end
                }
            }

            override suspend fun readBytes(expectedCount: Int, timeoutMs: Long): ByteArray =
                withContext(Dispatchers.IO) {
                    val out = ByteArrayOutputStream(expectedCount)
                    val deadline = System.currentTimeMillis() + timeoutMs
                    while (out.size() < expectedCount && System.currentTimeMillis() < deadline) {
                        val remaining = (deadline - System.currentTimeMillis()).coerceAtLeast(10L)
                        val b = rxQueue.poll(remaining.coerceAtMost(200L), TimeUnit.MILLISECONDS)
                        if (b != null) {
                            out.write(b.toInt() and 0xFF)
                        }
                    }
                    out.toByteArray()
                }

            override fun clearRxBuffer() {
                rxQueue.clear()
            }

            @SuppressLint("MissingPermission")
            override fun close() {
                try {
                    connectedGatt.disconnect()
                    connectedGatt.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun openClassicSppFallback(
        device: BluetoothDevice,
        onLog: (String) -> Unit
    ): RadioSerialTransport {
        onLog("[SPP] Attempting Bluetooth Classic RFCOMM Serial Port Profile (0x1101)...")
        val socket = try {
            device.createRfcommSocketToServiceRecord(SPP_UUID)
        } catch (e: Exception) {
            throw IOException("Failed to create Bluetooth SPP socket: ${e.localizedMessage}")
        }
        try {
            socket.connect()
        } catch (e: Exception) {
            try {
                socket.close()
            } catch (_: Exception) {
            }
            throw IOException(
                "Could not connect to ${device.name ?: device.address} via BLE GATT or Classic SPP: ${e.localizedMessage}"
            )
        }
        onLog("[SPP] Connected via RFCOMM Serial Socket.")
        val input = socket.inputStream
        val output = socket.outputStream

        return object : RadioSerialTransport {
            override suspend fun writeBytes(data: ByteArray) = withContext(Dispatchers.IO) {
                output.write(data)
                output.flush()
            }

            override suspend fun readBytes(expectedCount: Int, timeoutMs: Long): ByteArray =
                withContext(Dispatchers.IO) {
                    val out = ByteArrayOutputStream(expectedCount)
                    val deadline = System.currentTimeMillis() + timeoutMs
                    val temp = ByteArray(64)
                    while (out.size() < expectedCount && System.currentTimeMillis() < deadline) {
                        if (input.available() > 0) {
                            val toRead = (expectedCount - out.size()).coerceAtMost(temp.size)
                            val n = input.read(temp, 0, toRead)
                            if (n > 0) out.write(temp, 0, n)
                        } else {
                            delay(15)
                        }
                    }
                    out.toByteArray()
                }

            override fun clearRxBuffer() {
                try {
                    while (input.available() > 0) {
                        input.skip(input.available().toLong())
                    }
                } catch (_: Exception) {
                }
            }

            override fun close() {
                try {
                    socket.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    /**
     * Opens a real USB OTG Serial connection (CH340, FTDI, PL2303, CP210x) at 9600 baud 8N1.
     */
    private fun openUsbOtgTransport(
        usbDeviceName: String,
        onLog: (String) -> Unit
    ): RadioSerialTransport {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
            ?: throw IOException("USB Host Manager unavailable on this device.")
        val devices = usbManager.deviceList
        if (devices.isNullOrEmpty()) {
            throw IOException("No USB OTG programming cable detected. Connect your K-Plug USB OTG cable and try again.")
        }
        val device = devices[usbDeviceName] ?: devices.values.first()
        if (!usbManager.hasPermission(device)) {
            requestUsbPermission(device.deviceName)
            throw IOException("USB permission requested for ${identifySerialChipset(device.vendorId, device.productId)}. Approve the system prompt and tap again.")
        }

        val connection: UsbDeviceConnection = usbManager.openDevice(device)
            ?: throw IOException("Failed to open USB OTG device ${device.deviceName}.")

        var dataInterface: UsbInterface? = null
        var epIn: UsbEndpoint? = null
        var epOut: UsbEndpoint? = null

        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            var foundIn: UsbEndpoint? = null
            var foundOut: UsbEndpoint? = null
            for (j in 0 until intf.endpointCount) {
                val ep = intf.getEndpoint(j)
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (ep.direction == UsbConstants.USB_DIR_IN) foundIn = ep
                    if (ep.direction == UsbConstants.USB_DIR_OUT) foundOut = ep
                }
            }
            if (foundIn != null && foundOut != null) {
                dataInterface = intf
                epIn = foundIn
                epOut = foundOut
                break
            }
        }

        val claimedInterface = dataInterface
            ?: run {
                connection.close()
                throw IOException("No USB Bulk IN/OUT serial endpoints found on connected USB device.")
            }
        val bulkIn = epIn!!
        val bulkOut = epOut!!

        if (!connection.claimInterface(claimedInterface, true)) {
            connection.close()
            throw IOException("Could not claim USB serial interface.")
        }

        configureUsbSerial9600Baud(connection, device.vendorId)
        onLog("[USB OTG] Opened ${identifySerialChipset(device.vendorId, device.productId)} @ 9600 8N1.")

        return object : RadioSerialTransport {
            override suspend fun writeBytes(data: ByteArray) = withContext(Dispatchers.IO) {
                val written = connection.bulkTransfer(bulkOut, data, data.size, 1500)
                if (written < 0) {
                    throw IOException("USB bulkTransfer write failed (result=$written).")
                }
            }

            override suspend fun readBytes(expectedCount: Int, timeoutMs: Long): ByteArray =
                withContext(Dispatchers.IO) {
                    val out = ByteArrayOutputStream(expectedCount)
                    val deadline = System.currentTimeMillis() + timeoutMs
                    val buf = ByteArray(64)
                    while (out.size() < expectedCount && System.currentTimeMillis() < deadline) {
                        val n = connection.bulkTransfer(bulkIn, buf, buf.size, 250)
                        if (n > 0) {
                            // FTDI prepends 2 modem status bytes to each bulk IN packet
                            val startIdx = if (device.vendorId == 0x0403 && n >= 2) 2 else 0
                            if (n > startIdx) {
                                out.write(buf, startIdx, n - startIdx)
                            }
                        }
                    }
                    out.toByteArray()
                }

            override fun clearRxBuffer() {
                val buf = ByteArray(64)
                while (connection.bulkTransfer(bulkIn, buf, buf.size, 30) > 0) {
                    // Drain stale bytes
                }
            }

            override fun close() {
                try {
                    connection.releaseInterface(claimedInterface)
                    connection.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun configureUsbSerial9600Baud(conn: UsbDeviceConnection, vendorId: Int) {
        when (vendorId) {
            0x1A86 -> {
                // QinHeng CH340 / CH341 init @ 9600 baud 8N1
                conn.controlTransfer(0x40, 0xA1, 0, 0, null, 0, 500)
                conn.controlTransfer(0x40, 0x9A, 0x1312, 0xB282, null, 0, 500)
                conn.controlTransfer(0x40, 0x9A, 0x0F2C, 0x0008, null, 0, 500)
                conn.controlTransfer(0x40, 0xA4, 0x009F, 0, null, 0, 500)
            }
            0x0403 -> {
                // FTDI FT232R reset + 9600 baud (divisor 0x4138) + 8N1
                conn.controlTransfer(0x40, 0x00, 0, 0, null, 0, 500)
                conn.controlTransfer(0x40, 0x03, 0x4138, 0, null, 0, 500)
                conn.controlTransfer(0x40, 0x04, 0x0008, 0, null, 0, 500)
            }
            0x10C4 -> {
                // Silicon Labs CP210x enable UART + 9600 baud
                conn.controlTransfer(0x41, 0x00, 0x0001, 0, null, 0, 500)
                val baudBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(9600).array()
                conn.controlTransfer(0x41, 0x1E, 0, 0, baudBytes, 4, 500)
            }
            0x067B -> {
                // Prolific PL2303 9600 8N1 CDC SetLineCoding (0x20)
                val lineCoding = byteArrayOf(
                    0x80.toByte(), 0x25, 0x00, 0x00, // 9600 baud little-endian
                    0x00, // 1 stop bit
                    0x00, // No parity
                    0x08  // 8 data bits
                )
                conn.controlTransfer(0x21, 0x20, 0, 0, lineCoding, lineCoding.size, 500)
                conn.controlTransfer(0x21, 0x22, 0x0003, 0, null, 0, 500)
            }
        }
    }

    /**
     * Performs the real CHIRP Clone Handshake with the connected radio.
     * Probes the Baofeng UV-5R / UV-5R Mini / UV-17Pro / BF-888S handshake magic byte sequences
     * and requires a genuine `0x06` ACK or valid identification response from the radio.
     */
    private suspend fun performChirpHandshake(
        transport: RadioSerialTransport,
        radio: classSupportedRadioModel,
        onLog: (String) -> Unit
    ) {
        val candidateMagics: List<Pair<String, ByteArray>> = listOf(
            "UV-5R / UV-5R Mini (291)" to byteArrayOf(0x50, 0xBB.toByte(), 0xFF.toByte(), 0x20, 0x12, 0x07, 0x25),
            "UV-5R Original" to byteArrayOf(0x50, 0xBB.toByte(), 0xFF.toByte(), 0x01, 0x25, 0x98.toByte(), 0x4D),
            "UV-5R Mini / UV-17Pro (PSEARCH)" to "PSEARCH".toByteArray(Charsets.US_ASCII),
            "Baofeng PROGRAM ASCII" to "PROGRAM".toByteArray(Charsets.US_ASCII),
            "UV-82 / GMRS" to byteArrayOf(0x50, 0xBB.toByte(), 0xFF.toByte(), 0x20, 0x13, 0x01, 0x05)
        )

        var handshakeAcked = false
        var lastRxHex = "none"

        for ((label, magic) in candidateMagics) {
            transport.clearRxBuffer()
            val magicHex = magic.joinToString(" ") { String.format(Locale.US, "%02X", it) }
            onLog("[HANDSHAKE TX] Trying $label: $magicHex")
            transport.writeBytes(magic)

            val resp = transport.readBytes(expectedCount = 1, timeoutMs = 900L)
            if (resp.isNotEmpty()) {
                lastRxHex = resp.joinToString(" ") { String.format(Locale.US, "%02X", it) }
                onLog("[HANDSHAKE RX] Received byte: $lastRxHex")
                if (resp[0] == 0x06.toByte()) {
                    handshakeAcked = true
                    break
                }
            }
        }

        if (!handshakeAcked) {
            throw IOException(
                "Radio did not acknowledge CHIRP clone handshake (expected 0x06 ACK, received: $lastRxHex). " +
                    "Verify the radio is powered on, volume is turned up (if using K-Plug), and no channel is actively receiving."
            )
        }

        // Send 0x02 to request 8-byte firmware identification string
        transport.writeBytes(byteArrayOf(0x02))
        val identBytes = transport.readBytes(expectedCount = 8, timeoutMs = 1200L)
        if (identBytes.isNotEmpty()) {
            val identHex = identBytes.joinToString(" ") { String.format(Locale.US, "%02X", it) }
            val identAscii = identBytes.map { b ->
                val c = b.toInt() and 0xFF
                if (c in 32..126) c.toChar() else '.'
            }.joinToString("")
            onLog("[IDENT RX] Firmware Ident: '$identAscii' [$identHex]")
        }

        // Send 0x06 ACK to confirm ident and enter memory R/W mode
        transport.writeBytes(byteArrayOf(0x06))
        val enterAck = transport.readBytes(expectedCount = 1, timeoutMs = 800L)
        if (enterAck.isNotEmpty()) {
            onLog("[CLONE READY] Radio confirmed clone mode (0x${String.format(Locale.US, "%02X", enterAck[0])}).")
        }
    }

    /**
     * Reads the real EEPROM channel memory blocks (`0x52` 'R') and alpha tags from the connected radio
     * and decodes them into a list of [RadioChannel] objects.
     */
    suspend fun readCodeplugFromRadio(
        radio: classSupportedRadioModel,
        transportMode: String,
        bleAddress: String,
        usbDeviceName: String,
        onProgress: (Float, String) -> Unit
    ): List<RadioChannel> = withContext(Dispatchers.IO) {
        val uv32 = radio.id == "baofeng_uv32"
        lastPersistenceMessage = null
        if (uv32 && transportMode != "Bluetooth LE") throw IOException("Use Bluetooth for the UV-32 test download.")
        onProgress(0.04f, "[CONNECT] Opening $transportMode link for ${radio.model}...")
        val transport = if (transportMode == "Bluetooth LE") {
            openBleOrSppTransport(bleAddress, configureUv32Baud = uv32) { line -> onProgress(0.08f, line) }
        } else {
            openUsbOtgTransport(usbDeviceName) { line -> onProgress(0.08f, line) }
        }

        try {
            if (uv32) {
                val channels = readColorClone(transport, onProgress, uv32 = true)
                MiniSessionExit.finish(transport, reconnectWaitMs = 0)
                uv32Baseline = Triple(bleAddress, checkNotNull(lastUv32Image).copyOf(), channels)
                return@withContext channels
            }
            if (radio.model.contains("UV-5R Mini")) {
                lastPersistenceMessage = null
                val result = readColorClone(transport,onProgress)
                val pending = java.io.File(context.filesDir,"mini-pending-verification.bin")
                if (pending.exists()) {
                    val matches = pending.readBytes().contentEquals(checkNotNull(lastMiniImage))
                    lastPersistenceMessage = if(matches) "Persistence check matched the uploaded image. Confirm this download followed a radio power-cycle." else "Persistence check FAILED: radio memory differs from the uploaded image. Backup retained."
                    android.util.Log.i("ChirpBluetooth", if(matches) "[PERSISTENCE MATCH] Full image matched" else "[PERSISTENCE FAILED] Full image differs")
                    onProgress(1f,checkNotNull(lastPersistenceMessage))
                }
                MiniSessionExit.finish(transport,reconnectWaitMs=0)
                return@withContext result
            }
            performChirpHandshake(transport, radio) { line -> onProgress(0.15f, line) }

            // Read first 32 channels (32 * 16 bytes = 512 bytes = 8 blocks of 0x40 bytes from 0x0000..0x01C0)
            val channelCountToRead = 32
            val blockSize = 0x40
            val totalBlocks = (channelCountToRead * 16) / blockSize
            val rawChannelData = ByteArray(channelCountToRead * 16)

            for (blockIndex in 0 until totalBlocks) {
                val addr = blockIndex * blockSize
                val cmd = byteArrayOf(
                    0x52, // 'R' Read block
                    ((addr shr 8) and 0xFF).toByte(),
                    (addr and 0xFF).toByte(),
                    blockSize.toByte()
                )
                transport.writeBytes(cmd)

                // Radio responds with 4-byte header ('X' / 'R', hi, lo, len) + 64 bytes payload
                val response = transport.readBytes(expectedCount = 4 + blockSize, timeoutMs = 1500L)
                if (response.size < 4 + blockSize) {
                    throw IOException(
                        "Incomplete EEPROM read at 0x${String.format(Locale.US, "%04X", addr)} " +
                            "(got ${response.size} of ${4 + blockSize} bytes)."
                    )
                }
                System.arraycopy(response, 4, rawChannelData, addr, blockSize)
                // Send 0x06 ACK
                transport.writeBytes(byteArrayOf(0x06))
                transport.readBytes(expectedCount = 1, timeoutMs = 200L)

                val pct = 0.20f + (0.55f * ((blockIndex + 1).toFloat() / totalBlocks))
                val sampleHex = response.drop(4).take(8).joinToString(" ") { String.format(Locale.US, "%02X", it) }
                onProgress(
                    pct,
                    "[READ 0x${String.format(Locale.US, "%04X", addr)}] 64B OK [$sampleHex...]"
                )
            }

            // Read channel alpha names at 0x1000 (16 bytes per channel, 7-char ASCII name)
            val rawNameData = ByteArray(channelCountToRead * 16)
            for (blockIndex in 0 until totalBlocks) {
                val addr = 0x1000 + (blockIndex * blockSize)
                val cmd = byteArrayOf(
                    0x52,
                    ((addr shr 8) and 0xFF).toByte(),
                    (addr and 0xFF).toByte(),
                    blockSize.toByte()
                )
                transport.writeBytes(cmd)
                val response = transport.readBytes(expectedCount = 4 + blockSize, timeoutMs = 1500L)
                if (response.size >= 4 + blockSize) {
                    System.arraycopy(response, 4, rawNameData, blockIndex * blockSize, blockSize)
                    transport.writeBytes(byteArrayOf(0x06))
                    transport.readBytes(expectedCount = 1, timeoutMs = 200L)
                }
                val pct = 0.75f + (0.20f * ((blockIndex + 1).toFloat() / totalBlocks))
                onProgress(pct, "[NAMES 0x${String.format(Locale.US, "%04X", addr)}] Read alpha tags...")
            }

            // Decode the 16-byte channel blocks + 16-byte name blocks
            val decodedChannels = mutableListOf<RadioChannel>()
            for (chIdx in 0 until channelCountToRead) {
                val chOffset = chIdx * 16
                val chSlice = rawChannelData.copyOfRange(chOffset, chOffset + 16)
                val nameSlice = rawNameData.copyOfRange(chOffset, chOffset + 16)
                val decoded = decodeChirpEepromChannel(chIdx + 1, chSlice, nameSlice)
                if (decoded != null) {
                    decodedChannels.add(decoded)
                }
            }

            onProgress(1.0f, "[COMPLETE] Downloaded ${decodedChannels.size} programmed channels from ${radio.model}.")
            decodedChannels
        } finally {
            transport.close()
        }
    }

    /**
     * Writes the user's codeplug channels to the connected radio over USB OTG or Bluetooth LE
     * using real CHIRP `0x57` ('W') 64-byte EEPROM block writes and `0x06` ACK verification.
     */
    suspend fun executeProgrammingSequence(
        radio: classSupportedRadioModel,
        transportMode: String,
        bleAddress: String,
        usbDeviceName: String,
        channels: List<RadioChannel>,
        settings: com.example.data.model.RadioSettingsState,
        onProgress: (Float, String) -> Unit
    ) = withContext(Dispatchers.IO) {
        if (radio.id == "baofeng_uv32") {
            require(transportMode == "Bluetooth LE") { "Use Bluetooth to upload to the UV-32." }
            uploadUv32(bleAddress, channels, settings, onProgress)
            return@withContext
        }
        onProgress(0.04f, "[CONNECT] Opening $transportMode link for ${radio.model}...")
        val transport = if (transportMode == "Bluetooth LE") {
            openBleOrSppTransport(bleAddress) { line -> onProgress(0.08f, line) }
        } else {
            openUsbOtgTransport(usbDeviceName) { line -> onProgress(0.08f, line) }
        }

        try {
            if (radio.model.contains("UV-5R Mini")) {
                readColorClone(transport, { pct, message -> onProgress(pct * 0.35f, "[BACKUP] $message") })
                val original = checkNotNull(lastMiniImage).copyOf()
                val desired = MiniChannelPatch.apply(original, channels)
                if (original.contentEquals(desired)) {
                    android.util.Log.i("ChirpBluetooth","[VERIFIED] No channel changes; no write commands sent.")
                    onProgress(1f,"[VERIFIED] No channel changes; no write commands sent.")
                    return@withContext
                }
                val blocks = MiniWritePlan.fullImage(desired)
                android.util.Log.i("ChirpBluetooth","Mini full-image upload: ${blocks.size} blocks")
                for ((index,block) in blocks.withIndex()) {
                    val address = block.address
                    val payload = block.payload.copyOf()
                    val key = byteArrayOf(0x43,0x4f,0x20,0x37)
                    payload.indices.forEach { i ->
                        val value = payload[i].toInt() and 255
                        val k = key[i%4].toInt()
                        if(k != 32 && value != 0 && value != 255 && value != k && value != (k xor 255)) payload[i]=(value xor k).toByte()
                    }
                    val frame = byteArrayOf(0x57,(address shr 8).toByte(),address.toByte(),0x80.toByte()) + payload
                    android.util.Log.i("ChirpBluetooth","Mini WRITE %04X".format(address))
                    transport.writeBytes(frame)
                    val ack = transport.readBytes(1,4000)
                    if (ack.size != 1 || ack[0] != 6.toByte()) throw IOException("Mini write acknowledgment failed at %04X. Backup retained; read the radio before retrying.".format(address))
                    onProgress(0.35f + 0.25f*(index+1)/blocks.size,"[WRITE %04X] acknowledged".format(address))
                }
                java.io.File(context.filesDir,"mini-pending-verification.bin").writeBytes(desired)
                onProgress(0.60f,"[FINALIZE] Sending programming exit and waiting for radio...")
                MiniSessionExit.finish(transport)
                val verifyTransport = if(transportMode=="Bluetooth LE") openBleOrSppTransport(bleAddress) { android.util.Log.i("ChirpBluetooth",it) } else openUsbOtgTransport(usbDeviceName) { android.util.Log.i("ChirpBluetooth",it) }
                try {
                    readColorClone(verifyTransport, { pct, message -> onProgress(0.60f + pct * 0.39f,"[RECONNECT VERIFY] $message") })
                    val actual = checkNotNull(lastMiniImage)
                    val matches = desired.contentEquals(actual)
                    MiniSessionExit.finish(verifyTransport,reconnectWaitMs=0)
                    if(!matches) throw IOException("Post-exit read-back differs from uploaded image. Backups retained.")
                    android.util.Log.i("ChirpBluetooth","[SESSION VERIFIED] Post-exit reconnect image matches. Manual power-cycle verification pending.")
                    onProgress(1f,"[SESSION VERIFIED] Power-cycle radio, then Download to verify persistence.")
                } finally {
                    verifyTransport.close()
                }
                return@withContext
            }
            performChirpHandshake(transport, radio) { line -> onProgress(0.16f, line) }

            val paddedSlotCount = ((channels.size + 3) / 4).coerceAtLeast(1) * 4
            val blockSize = 0x40 // 64 bytes = 4 channels per block
            val totalBlocks = (paddedSlotCount * 16) / blockSize

            for (blockIndex in 0 until totalBlocks) {
                val addr = blockIndex * blockSize
                val payload = ByteArray(blockSize) { 0xFF.toByte() }
                for (sub in 0 until 4) {
                    val chIdx = blockIndex * 4 + sub
                    if (chIdx < channels.size) {
                        val encoded = encodeChirpEepromBlock(channels[chIdx])
                        System.arraycopy(encoded, 0, payload, sub * 16, 16)
                    }
                }

                val packet = ByteArray(4 + blockSize)
                packet[0] = 0x57 // 'W' Write block
                packet[1] = ((addr shr 8) and 0xFF).toByte()
                packet[2] = (addr and 0xFF).toByte()
                packet[3] = blockSize.toByte()
                System.arraycopy(payload, 0, packet, 4, blockSize)

                transport.writeBytes(packet)
                val ack = transport.readBytes(expectedCount = 1, timeoutMs = 1500L)
                if (ack.isEmpty() || ack[0] != 0x06.toByte()) {
                    val rxStr = if (ack.isEmpty()) "timeout" else String.format(Locale.US, "0x%02X", ack[0])
                    throw IOException(
                        "EEPROM block write at 0x${String.format(Locale.US, "%04X", addr)} failed (expected 0x06 ACK, got $rxStr)."
                    )
                }

                // Also write 7-char ASCII channel names at 0x1000 + addr
                val nameAddr = 0x1000 + addr
                val namePayload = ByteArray(blockSize) { 0xFF.toByte() }
                for (sub in 0 until 4) {
                    val chIdx = blockIndex * 4 + sub
                    if (chIdx < channels.size) {
                        val cleanName = channels[chIdx].name.uppercase(Locale.US).take(7)
                        val nameBytes = cleanName.toByteArray(Charsets.US_ASCII)
                        System.arraycopy(nameBytes, 0, namePayload, sub * 16, nameBytes.size)
                    }
                }
                val namePacket = ByteArray(4 + blockSize)
                namePacket[0] = 0x57
                namePacket[1] = ((nameAddr shr 8) and 0xFF).toByte()
                namePacket[2] = (nameAddr and 0xFF).toByte()
                namePacket[3] = blockSize.toByte()
                System.arraycopy(namePayload, 0, namePacket, 4, blockSize)
                transport.writeBytes(namePacket)
                transport.readBytes(expectedCount = 1, timeoutMs = 1000L)

                val pct = 0.20f + (0.75f * ((blockIndex + 1).toFloat() / totalBlocks))
                val hexPreview = payload.take(8).joinToString(" ") { String.format(Locale.US, "%02X", it) }
                onProgress(
                    pct,
                    "[WRITE 0x${String.format(Locale.US, "%04X", addr)}] ACK 0x06 OK [$hexPreview...]"
                )
            }

            onProgress(1.0f, "[COMPLETE] Successfully wrote ${channels.size} channels to ${radio.model}.")
        } finally {
            transport.close()
        }
    }


    /** Mini protocol reference: CHIRP baofeng_uv17Pro.py, UV5RMini.
     * Read-only: preserve the complete decrypted image before decoding channels.
     */
    private suspend fun uploadUv32(
        address: String,
        channels: List<RadioChannel>,
        settings: com.example.data.model.RadioSettingsState,
        progress: (Float, String) -> Unit
    ) {
        val baseline = requireNotNull(uv32Baseline) { "Download from the UV-32 before uploading." }
        require(address == baseline.first) { "Select the same UV-32 you downloaded from." }
        val before = baseline.third.associateBy { it.number }
        val after = channels.associateBy { it.number }
        require(after.size == channels.size) { "Duplicate channel slots." }
        val edits = (before.keys + after.keys).filter { before[it] != after[it] }.toSet()
        var transport = openBleOrSppTransport(address, configureUv32Baud = true) { progress(0.03f, it) }
        try {
            readColorClone(transport, { pct, msg -> progress(pct * 0.33f, msg) }, uv32 = true, includeUv32Extras = true)
            val original = checkNotNull(lastUv32Image)
            val extras = checkNotNull(lastUv32Extras)
            require(original.contentEquals(baseline.second)) { "Radio memory changed since download. Download again before uploading." }
            require(edits.none { it in 1..1000 && it !in before && original[(it - 1) * 32] != 0xff.toByte() }) {
                "An edited slot contains an unreadable channel. Choose a different slot."
            }
            val channelImage = MiniChannelPatch.applyUv32(original, channels, edits, before)
            val desired = Uv32SettingsPatch.apply(channelImage, settings)
            if (desired.contentEquals(original)) {
                MiniSessionExit.finish(transport, reconnectWaitMs = 0)
                progress(1f, "No changes to upload.")
                return
            }

            // Ola's FFE0 Bluetooth path writes 128 bytes per block. Preserve the
            // adjacent 64-byte regions that the packed CHIRP image omits.
            val blocks = buildList<Pair<Int, ByteArray>> {
                for (at in 0 until 0x7d00 step 128) add(at to desired.copyOfRange(at, at + 128))
                add(0x8000 to (desired.copyOfRange(0x8000, 0x8040) + extras.copyOfRange(0, 64)))
                add(0x9000 to (desired.copyOfRange(0x8040, 0x8080) + extras.copyOfRange(64, 128)))
                val upper = desired.copyOfRange(0x8080, 0x8340) + extras.copyOfRange(128, 320)
                for (offset in upper.indices step 128) {
                    add(0xa000 + offset to upper.copyOfRange(offset, offset + 128))
                }
            }
            // A backup is a complete download session. Start a fresh clone session for writing.
            MiniSessionExit.finish(transport)
            transport = openBleOrSppTransport(address, configureUv32Baud = true) { progress(0.33f, it) }
            initializeColorClone(transport, true) { pct, message -> progress(0.33f + pct * 0.02f, message) }
            uv32Baseline = null
            val key = intArrayOf(0x43, 0x4f, 0x20, 0x37)
            for ((index, block) in blocks.withIndex()) {
                val (at, payload) = block
                payload.indices.forEach { i ->
                    val value = payload[i].toInt() and 255
                    val k = key[i % 4]
                    if (k != 32 && value != 0 && value != 255 && value != k && value != (k xor 255)) {
                        payload[i] = (value xor k).toByte()
                    }
                }
                android.util.Log.i("ChirpBluetooth", "UV-32 write %04X TX 132 bytes".format(at))
                transport.writeBytes(byteArrayOf(0x57, (at shr 8).toByte(), at.toByte(), payload.size.toByte()) + payload)
                val ack = transport.readBytes(1, 4000L)
                if (!ack.contentEquals(byteArrayOf(6))) {
                    val reply = ack.joinToString(" ") { "%02X".format(it) }.ifEmpty { "no reply" }
                    android.util.Log.w("ChirpBluetooth", "UV-32 write %04X RX $reply".format(at))
                    throw IOException("UV-32 write failed at %04X. Backup retained.".format(at))
                }
                progress(0.33f + 0.33f * (index + 1) / blocks.size, "Writing UV-32 block %04X".format(at))
            }
            MiniSessionExit.finish(transport)
            val verify = openBleOrSppTransport(address, configureUv32Baud = true) { progress(0.66f, it) }
            try {
                readColorClone(verify, { pct, msg -> progress(0.66f + pct * 0.33f, msg) }, uv32 = true, includeUv32Extras = true)
                val matches = desired.contentEquals(checkNotNull(lastUv32Image)) &&
                    extras.contentEquals(checkNotNull(lastUv32Extras))
                MiniSessionExit.finish(verify, reconnectWaitMs = 0)
                if (!matches) throw IOException("UV-32 read-back differs from the upload. Backups retained.")
                progress(1f, "UV-32 upload verified after reconnecting.")
            } finally {
                verify.close()
            }
        } finally {
            transport.close()
        }
    }

    private suspend fun exchangeColorClone(transport: RadioSerialTransport, model: String, command: ByteArray, length: Int, label: String): ByteArray {
            android.util.Log.i("ChirpBluetooth", "$model $label TX=" + command.joinToString(" ") { "%02X".format(it) })
            transport.writeBytes(command)
            val reply = transport.readBytes(length, 4000L)
            android.util.Log.i("ChirpBluetooth", "$model $label RX ${reply.size}/$length bytes")
            if (reply.size != length) throw IOException("$model $label: expected $length bytes, received ${reply.size}. Close other radio apps and reconnect.")
            return reply
        }

    private suspend fun initializeColorClone(
        transport: RadioSerialTransport,
        uv32: Boolean,
        onProgress: (Float, String) -> Unit
    ) {
        val model = if (uv32) "UV-32" else "Mini"
        transport.clearRxBuffer()
        val ack = exchangeColorClone(transport, model, "PROGRAMCOLORPROU".toByteArray(Charsets.US_ASCII), 1, "program handshake")
        if (ack[0] != 0x06.toByte()) {
            val reply = "0x%02X".format(ack[0].toInt() and 0xff)
            val reason = "$model rejected the programming handshake (reply $reply, expected 0x06)."
            android.util.Log.w("ChirpBluetooth", reason)
            throw IOException(reason)
        }
        val firmware = exchangeColorClone(transport, model, byteArrayOf(0x46), 16, "firmware identification")
        onProgress(0.10f, "[$model IDENT] " + firmware.joinToString(" ") { "%02X".format(it) })
        val modelId = exchangeColorClone(transport, model, byteArrayOf(0x4d), if (uv32) 7 else 15, "model identification")
        if (uv32 && modelId.toString(Charsets.US_ASCII) != "UV32PRO") {
            throw IOException("Unexpected UV-32 model reply: " + modelId.joinToString(" ") { "%02X".format(it) })
        }
        val init = byteArrayOf(0x53,0x45,0x4e,0x44,0x21,0x05,0x0d,0x01,0x01,0x01,0x04,0x11,0x08,0x05,0x0d,0x0d,0x01,0x11,0x0f,0x09,0x12,0x09,0x10,0x04,0x00)
        if (exchangeColorClone(transport, model, init, 1, "session initialization")[0] != 0x06.toByte()) throw IOException("$model session initialization rejected.")
    }

    private suspend fun readColorClone(transport: RadioSerialTransport, onProgress: (Float, String) -> Unit, initialize: Boolean = true, uv32: Boolean = false, includeUv32Extras: Boolean = false): List<RadioChannel> {
        val model = if (uv32) "UV-32" else "Mini"
        if (initialize) initializeColorClone(transport, uv32, onProgress)
        val image = ByteArrayOutputStream()
        val key = byteArrayOf(0x43,0x4f,0x20,0x37)
        val ranges = if (uv32) listOf(0x0000 to 0x8040, 0x9000 to 0x0040, 0xa000 to 0x02c0, 0xd000 to 0x0040)
            else listOf(0x0000 to 0x8040, 0x9000 to 0x0040, 0xa000 to 0x01c0)
        val readRanges = if (includeUv32Extras) ranges + listOf(0x8040 to 64, 0x9040 to 64, 0xa2c0 to 192) else ranges
        val total = readRanges.sumOf { it.second }
        for ((start, size) in readRanges) {
            for (address in start until start + size step 64) {
                val reply = exchangeColorClone(transport, model, byteArrayOf(0x52,(address shr 8).toByte(),address.toByte(),0x40),68,"read %04X".format(address))
                if (reply[0] != 0x57.toByte() && reply[0] != 0x52.toByte()) throw IOException("Mini invalid read response header at %04X".format(address))
                if ((reply[1].toInt() and 255) != (address shr 8) || (reply[2].toInt() and 255) != (address and 255) || reply[3] != 0x40.toByte()) throw IOException("Mini read address/length mismatch.")
                val payload = reply.copyOfRange(4,68)
                payload.indices.forEach { i ->
                    val value = payload[i].toInt() and 255
                    val k = key[i % 4].toInt()
                    if (k != 32 && value != 0 && value != 255 && value != k && value != (k xor 255)) payload[i] = (value xor k).toByte()
                }
                image.write(payload)
                onProgress(0.15f + 0.80f * image.size() / total, "[$model READ %04X] %d / %d bytes".format(address, image.size(), total))
            }
        }
        val captured = image.toByteArray()
        val bytes = if (includeUv32Extras) captured.copyOf(0x8380) else captured
        if (uv32) {
            lastUv32Image = bytes
            lastUv32Extras = if (includeUv32Extras) captured.copyOfRange(0x8380, captured.size) else null
            lastUv32Settings = com.example.data.model.Uv32SettingsSnapshot.fromImage(bytes)
        } else lastMiniImage = bytes.copyOf()
        val backup = java.io.File(context.filesDir, if (uv32) "uv32-backups" else "mini-backups").apply { mkdirs() }
        val file = java.io.File(backup, "${if (uv32) "uv32" else "uv5r-mini"}-${System.currentTimeMillis()}.bin")
        file.writeBytes(bytes)
        if (includeUv32Extras) java.io.File(backup, file.nameWithoutExtension + "-extra.bin").writeBytes(checkNotNull(lastUv32Extras))
        val invalidSlots = mutableListOf<Int>()
        val channels = (0 until (if (uv32) 1000 else 999)).mapNotNull { slot ->
            val offset = slot * 32
            if (bytes[offset] == 0xff.toByte()) return@mapNotNull null
            fun frequency(at: Int): Long {
                var result = 0L
                var multiplier = 1L
                for (i in 0..3) {
                    val b = bytes[at+i].toInt() and 255
                    if ((b and 15) > 9 || (b shr 4) > 9) throw IOException("Invalid Mini frequency encoding in channel ${slot+1}")
                    result += ((b and 15) + (b shr 4)*10) * multiplier
                    multiplier *= 100
                }
                return result * 10
            }
            fun tone(at: Int): String {
                val value = (bytes[at].toInt() and 255) or ((bytes[at+1].toInt() and 255) shl 8)
                return MiniToneCodec.decode(value)
            }
            val off = (4..7).all { bytes[offset+it] == 0xff.toByte() }
            val (rx, tx) = try {
                val rx = frequency(offset)
                rx to if (off) rx else frequency(offset + 4)
            } catch (error: IOException) {
                if (!uv32) throw error
                invalidSlots.add(slot + 1)
                return@mapNotNull null
            }
            val name = bytes.copyOfRange(offset+20,offset+32).map { it.toInt() and 255 }.takeWhile { it != 0 && it != 255 }.map { it.toChar() }.joinToString("").trim().ifBlank { "CH ${slot+1}" }
            RadioChannel(number=slot+1,name=name,rxFreqHz=rx,txFreqHz=tx,
                duplex=if(off) DuplexMode.OFF else if(tx==rx) DuplexMode.SIMPLEX else DuplexMode.SPLIT,
                offsetHz=abs(tx-rx),rxTone=tone(offset+8),txTone=tone(offset+10),
                mode=if(rx in 108000000L..135999999L) "AM" else if((bytes[offset+15].toInt() and 0x40)!=0) "FM" else "NFM",
                power=when (bytes[offset+14].toInt() and 3) {
                    1 -> PowerLevel.LOW
                    2 -> if (uv32) PowerLevel.MID else PowerLevel.HIGH
                    else -> PowerLevel.HIGH
                },
                rxOnly=off,notes="$model download; complete raw backup retained on device.",
                scanIncluded=(bytes[offset+15].toInt() and 4)!=0,
                busyChannelLockout=(bytes[offset+15].toInt() and 8)!=0,
                fhssPrivacy=(bytes[offset+15].toInt() and 1)!=0,
                pttId=(bytes[offset+13].toInt() and 255).takeIf { it in 0..3 })
        }
        if (uv32) {
            lastPersistenceMessage = "UV-32: downloaded ${channels.size} channels and saved a raw backup." +
                if (invalidSlots.isEmpty()) "" else " Skipped invalid frequency slots: ${invalidSlots.joinToString()}."
        }
        onProgress(1f,"[$model COMPLETE] ${channels.size} channels; backup ${file.name}.")
        return channels
    }

    /**
     * Encodes a [RadioChannel] into a 16-byte CHIRP BCD EEPROM memory structure:
     * - Bytes 0..3: 8-digit BCD RX frequency in 10 Hz units (e.g., 146.52000 MHz -> 0x14 0x65 0x20 0x00)
     * - Bytes 4..7: 8-digit BCD TX frequency in 10 Hz units (or 0xFFFFFFFF if Duplex=OFF)
     * - Bytes 8..9: Little-endian 16-bit RX CTCSS/DCS tone (0.1 Hz units)
     * - Bytes 10..11: Little-endian 16-bit TX CTCSS/DCS tone (0.1 Hz units)
     * - Bytes 12..15: Power / Bandwidth / Scan flags
     */
    fun encodeChirpEepromBlock(channel: RadioChannel): ByteArray {
        val block = ByteArray(16)
        val rxBcd = freqHzToBcd4(channel.rxFreqHz)
        System.arraycopy(rxBcd, 0, block, 0, 4)

        if (channel.duplex == DuplexMode.OFF) {
            for (i in 4..7) block[i] = 0xFF.toByte()
        } else {
            val txBcd = freqHzToBcd4(channel.txFreqHz)
            System.arraycopy(txBcd, 0, block, 4, 4)
        }

        val rxToneTenths = ((channel.rxTone.toDoubleOrNull() ?: 0.0) * 10).toInt()
        val txToneTenths = ((channel.txTone.toDoubleOrNull() ?: 0.0) * 10).toInt()
        block[8] = (rxToneTenths and 0xFF).toByte()
        block[9] = ((rxToneTenths shr 8) and 0xFF).toByte()
        block[10] = (txToneTenths and 0xFF).toByte()
        block[11] = ((txToneTenths shr 8) and 0xFF).toByte()

        var flags = 0
        if (channel.power == PowerLevel.LOW) flags = flags or 0x04
        if (channel.mode == "NFM") flags = flags or 0x40
        block[12] = flags.toByte()
        block[13] = 0x00
        block[14] = 0x00
        block[15] = 0x00
        return block
    }

    private fun decodeChirpEepromChannel(
        channelNumber: Int,
        chBytes: ByteArray,
        nameBytes: ByteArray
    ): RadioChannel? {
        // Empty EEPROM slot starts with 0xFF
        if (chBytes[0] == 0xFF.toByte() && chBytes[1] == 0xFF.toByte()) {
            return null
        }
        val rxHz = bcd4ToFreqHz(chBytes, 0)
        if (rxHz <= 0L) return null

        val isTxOff = (0..3).all { chBytes[4 + it] == 0xFF.toByte() }
        val txHz = if (isTxOff) rxHz else bcd4ToFreqHz(chBytes, 4).takeIf { it > 0L } ?: rxHz

        val rxToneVal = ((chBytes[9].toInt() and 0xFF) shl 8) or (chBytes[8].toInt() and 0xFF)
        val rxToneStr = if (rxToneVal in 600..3000) {
            String.format(Locale.US, "%.1f", rxToneVal / 10.0)
        } else {
            "None"
        }

        val txToneVal = ((chBytes[11].toInt() and 0xFF) shl 8) or (chBytes[10].toInt() and 0xFF)
        val txToneStr = if (txToneVal in 600..3000) {
            String.format(Locale.US, "%.1f", txToneVal / 10.0)
        } else {
            "None"
        }

        val flags = chBytes[12].toInt() and 0xFF
        val isLowPower = (flags and 0x04) != 0
        val isNarrow = (flags and 0x40) != 0

        val alphaName = nameBytes.take(7)
            .map { it.toInt() and 0xFF }
            .takeWhile { it != 0xFF && it != 0x00 }
            .filter { it in 32..126 }
            .map { it.toChar() }
            .joinToString("")
            .trim()
            .ifBlank { "CH $channelNumber" }

        val isFrs1Fallback = txHz == RadioChannel.FRS1_FALLBACK_HZ && rxHz != RadioChannel.FRS1_FALLBACK_HZ && isLowPower
        val rxOnly = isTxOff || isFrs1Fallback
        val duplex = when {
            isTxOff -> DuplexMode.OFF
            isFrs1Fallback -> DuplexMode.SPLIT
            txHz > rxHz -> DuplexMode.PLUS
            txHz < rxHz -> DuplexMode.MINUS
            else -> DuplexMode.SIMPLEX
        }

        return RadioChannel(
            number = channelNumber,
            name = alphaName,
            rxFreqHz = rxHz,
            txFreqHz = txHz,
            duplex = duplex,
            offsetHz = abs(txHz - rxHz),
            rxTone = rxToneStr,
            txTone = txToneStr,
            mode = if (isNarrow) "NFM" else "FM",
            power = if (isLowPower) PowerLevel.LOW else PowerLevel.HIGH,
            rxOnly = rxOnly,
            notes = "Downloaded from radio EEPROM"
        )
    }

    private fun freqHzToBcd4(freqHz: Long): ByteArray {
        val tenHzUnits = (freqHz / 10L).coerceAtLeast(0L)
        val digits = String.format(Locale.US, "%08d", tenHzUnits).takeLast(8)
        val out = ByteArray(4)
        for (i in 0 until 4) {
            val hi = digits[i * 2].digitToIntOrNull() ?: 0
            val lo = digits[i * 2 + 1].digitToIntOrNull() ?: 0
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }

    private fun bcd4ToFreqHz(bytes: ByteArray, offset: Int): Long {
        var result = 0L
        for (i in 0 until 4) {
            val b = bytes[offset + i].toInt() and 0xFF
            val hi = (b shr 4) and 0x0F
            val lo = b and 0x0F
            if (hi > 9 || lo > 9) return 0L
            result = result * 100L + (hi * 10L) + lo
        }
        return result * 10L
    }

    companion object {
        private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")
    }
}
