package com.example.domain

import com.example.data.model.DuplexMode
import com.example.data.model.PowerLevel
import com.example.data.model.RadioChannel
import com.example.data.model.SafetyProfile
import kotlin.math.abs

enum class TransportInterface(val label: String) {
    USB_OTG("USB OTG Serial (FTDI / CH340 / PL2303)"),
    BLUETOOTH_LE("Bluetooth LE Wireless (Native / Dongle)"),
    BOTH("USB OTG & Bluetooth LE")
}

data class classSupportedRadioModel(
    val id: String,
    val vendor: String,
    val model: String,
    val maxChannels: Int,
    val bandsSummary: String,
    val supportedBandsMhz: List<ClosedFloatingPointRange<Double>>,
    val transport: TransportInterface,
    val supportsNativeRxOnly: Boolean,
    val eepromIdent: String,
    val protocolNotes: String
) {
    val zoneSize: Int get() = when (id) {
        "baofeng_uv32", "baofeng_uv17_pro_gps" -> 100
        "radtel_rt950_pro" -> 99
        else -> 0
    }

    fun slotsForZone(zone: Int): IntRange = if (zoneSize > 0 && zone in 1..10)
        ((zone - 1) * zoneSize + 1)..(zone * zoneSize) else 1..maxChannels
}

object RadioCatalog {
    val allRadios: List<classSupportedRadioModel> = listOf(
        // --- BAOFENG ---
        classSupportedRadioModel(
            id = "baofeng_uv32",
            vendor = "Baofeng",
            model = "Baofeng UV-32 (Tri-Band 10W)",
            maxChannels = 1000,
            bandsSummary = "144 / 222 / 430 MHz Tri-Band TX • 108-136 Airband RX (10 Zones)",
            supportedBandsMhz = listOf(108.0..136.0, 136.0..174.0, 220.0..260.0, 400.0..520.0),
            transport = TransportInterface.BOTH,
            supportsNativeRxOnly = true,
            eepromIdent = "BF-UV32",
            protocolNotes = "1000 channel slots across 10 zones. Enable Wireless CPS on the radio for the Bluetooth test download."
        ),
        classSupportedRadioModel(
            id = "radtel_rt950_pro",
            vendor = "Radtel",
            model = "RT-950 Pro",
            maxChannels = 990,
            bandsSummary = "Multiband analog · Bluetooth or USB download",
            supportedBandsMhz = listOf(18.0..64.0, 70.0..580.0),
            transport = TransportInterface.BOTH,
            supportsNativeRxOnly = true,
            eepromIdent = "RT-950PRO",
            protocolNotes = "Bluetooth and USB downloads for firmware 0.24 and later. USB upload supports settings and zone names."
        ),
        classSupportedRadioModel(
            id = "baofeng_uv5_mini",
            vendor = "Baofeng",
            model = "Baofeng UV-5R Mini",
            maxChannels = 999,
            bandsSummary = "VHF 136-174 / UHF 400-520 / Airband RX",
            supportedBandsMhz = listOf(108.0..136.0, 136.0..174.0, 220.0..260.0, 400.0..520.0),
            transport = TransportInterface.BOTH,
            supportsNativeRxOnly = false,
            eepromIdent = "BF5MINI",
            protocolNotes = "Built-in Bluetooth LE & USB-C OTG. Lacks native hardware RX-only lock — ChirpField routes TX to FRS Ch 1 (462.5625 MHz @ 0.5W Low) fallback in Scanner Safe mode."
        ),
        classSupportedRadioModel(
            id = "baofeng_uv5r",
            vendor = "Baofeng",
            model = "Baofeng UV-5R / BF-F8HP",
            maxChannels = 128,
            bandsSummary = "VHF 136-174 / UHF 400-520 MHz",
            supportedBandsMhz = listOf(136.0..174.0, 400.0..520.0),
            transport = TransportInterface.BOTH,
            supportsNativeRxOnly = true,
            eepromIdent = "BFP3BFF",
            protocolNotes = "Standard CHIRP N75/BFB297 16-byte EEPROM layout. Supports K-Plug USB OTG cable or clip-on BLE programmer."
        ),
        classSupportedRadioModel(
            id = "baofeng_uv17_pro_gps",
            vendor = "Baofeng",
            model = "Baofeng UV-17 Pro GPS",
            maxChannels = 1000,
            bandsSummary = "6-Band RX / VHF & UHF TX (10 Zones)",
            supportedBandsMhz = listOf(108.0..136.0, 136.0..174.0, 220.0..260.0, 350.0..390.0, 400.0..520.0),
            transport = TransportInterface.USB_OTG,
            supportsNativeRxOnly = true,
            eepromIdent = "BF17PRO",
            protocolNotes = "USB programming at 115200 baud. Ten zones with 100 slots each."
        ),
        classSupportedRadioModel(
            id = "baofeng_uv82",
            vendor = "Baofeng",
            model = "Baofeng UV-82 / UV-82HP",
            maxChannels = 128,
            bandsSummary = "VHF 136-174 / UHF 400-520 MHz (Dual PTT)",
            supportedBandsMhz = listOf(136.0..174.0, 400.0..520.0),
            transport = TransportInterface.BOTH,
            supportsNativeRxOnly = true,
            eepromIdent = "US82HP",
            protocolNotes = "Dual-PTT commercial/responder favorite. Supports Duplex=OFF hardware RX-only or FRS1 low-power fallback."
        ),
        classSupportedRadioModel(
            id = "baofeng_uv5x3",
            vendor = "Baofeng",
            model = "Baofeng UV-5X3 Tri-Band",
            maxChannels = 128,
            bandsSummary = "144 MHz / 222 MHz / 430 MHz Tri-Band",
            supportedBandsMhz = listOf(136.0..174.0, 220.0..225.0, 400.0..520.0),
            transport = TransportInterface.BOTH,
            supportsNativeRxOnly = true,
            eepromIdent = "BFUV5X3",
            protocolNotes = "Dedicated 1.25m amateur band support. Supports standard CHIRP UV-5X3 memory layout."
        ),
        classSupportedRadioModel(
            id = "baofeng_bf888s",
            vendor = "Baofeng",
            model = "Baofeng BF-888S / H-777",
            maxChannels = 16,
            bandsSummary = "UHF 400-470 MHz (16 Channels)",
            supportedBandsMhz = listOf(400.0..470.0),
            transport = TransportInterface.USB_OTG,
            supportsNativeRxOnly = true,
            eepromIdent = "BF888S",
            protocolNotes = "16-channel voice prompt handheld. Programmed via K-Plug USB OTG serial cable."
        ),
        classSupportedRadioModel(
            id = "baofeng_gt5r",
            vendor = "Baofeng",
            model = "Baofeng GT-5R / GT-3TP",
            maxChannels = 128,
            bandsSummary = "VHF 144-148 / UHF 420-450 MHz (FCC Compliant)",
            supportedBandsMhz = listOf(144.0..148.0, 420.0..450.0),
            transport = TransportInterface.BOTH,
            supportsNativeRxOnly = true,
            eepromIdent = "BFGT5R",
            protocolNotes = "FCC part 97 clean spurious harmonics variant. CHIRP compatible clone."
        ),

        // --- QUANSHENG ---
        classSupportedRadioModel(
            id = "quansheng_uv_k5",
            vendor = "Quansheng",
            model = "Quansheng UV-K5 / UV-K5(8) / UV-K6",
            maxChannels = 200,
            bandsSummary = "50-600 MHz Wideband RX / VHF & UHF TX",
            supportedBandsMhz = listOf(50.0..600.0),
            transport = TransportInterface.USB_OTG,
            supportsNativeRxOnly = true,
            eepromIdent = "QSK5EGZ",
            protocolNotes = "Supports Stock, Egzumer, and IJV firmwares over K-Plug USB OTG serial (38400 baud XOR-obfuscated packet protocol)."
        ),
        classSupportedRadioModel(
            id = "quansheng_tg_uv2",
            vendor = "Quansheng",
            model = "Quansheng TG-UV2 Plus",
            maxChannels = 200,
            bandsSummary = "VHF 136-174 / UHF 400-470 MHz (10W)",
            supportedBandsMhz = listOf(136.0..174.0, 400.0..470.0),
            transport = TransportInterface.USB_OTG,
            supportsNativeRxOnly = true,
            eepromIdent = "QSTG2P",
            protocolNotes = "High power analog handheld. Programmed over standard Kenwood 2-pin serial cable."
        ),

        // --- TIDRADIO ---
        classSupportedRadioModel(
            id = "tidradio_td_h3",
            vendor = "Tidradio",
            model = "Tidradio TD-H3 (Native BLE + USB-C)",
            maxChannels = 199,
            bandsSummary = "VHF / 1.25m / UHF / Airband RX",
            supportedBandsMhz = listOf(108.0..136.0, 136.0..174.0, 220.0..260.0, 400.0..520.0),
            transport = TransportInterface.BOTH,
            supportsNativeRxOnly = true,
            eepromIdent = "TDH3BLE",
            protocolNotes = "Built-in Bluetooth LE GATT programmer and direct USB-C serial port."
        ),
        classSupportedRadioModel(
            id = "tidradio_td_h8",
            vendor = "Tidradio",
            model = "Tidradio TD-H8 (10W High Power BLE)",
            maxChannels = 199,
            bandsSummary = "VHF 136-174 / UHF 400-520 MHz (10W)",
            supportedBandsMhz = listOf(136.0..174.0, 400.0..520.0),
            transport = TransportInterface.BOTH,
            supportsNativeRxOnly = true,
            eepromIdent = "TDH8BLE",
            protocolNotes = "10W wireless programming handheld. Supports Odmaster BLE profile and CHIRP serial clone."
        ),

        // --- BTECH ---
        classSupportedRadioModel(
            id = "btech_uv_pro",
            vendor = "BTech",
            model = "BTech UV-Pro / GMRS-Pro",
            maxChannels = 192,
            bandsSummary = "VHF 2m / 1.25m 220 / UHF 70cm",
            supportedBandsMhz = listOf(136.0..174.0, 220.0..225.0, 400.0..520.0),
            transport = TransportInterface.BOTH,
            supportsNativeRxOnly = true,
            eepromIdent = "BTUVPRO",
            protocolNotes = "Multi-zone Bluetooth LE & USB-C field radio."
        ),
        classSupportedRadioModel(
            id = "btech_uv5x3",
            vendor = "BTech",
            model = "BTech UV-5X3",
            maxChannels = 128,
            bandsSummary = "144 / 222 / 430 MHz Tri-Band Transceiver",
            supportedBandsMhz = listOf(136.0..174.0, 222.0..225.0, 400.0..520.0),
            transport = TransportInterface.BOTH,
            supportsNativeRxOnly = true,
            eepromIdent = "BT5X3",
            protocolNotes = "BTech tri-band firmware with display synchronization and DTMF audio gain adjustments."
        ),

        // --- WOUXUN ---
        classSupportedRadioModel(
            id = "wouxun_kg_uv9d",
            vendor = "Wouxun",
            model = "Wouxun KG-UV9D / KG-UV9D Plus",
            maxChannels = 999,
            bandsSummary = "7-Band RX / Cross-Band Full Duplex VHF/UHF",
            supportedBandsMhz = listOf(108.0..174.0, 320.0..512.0),
            transport = TransportInterface.USB_OTG,
            supportsNativeRxOnly = true,
            eepromIdent = "WXUV9D",
            protocolNotes = "CHIRP Wouxun clone driver over K-Plug FTDI OTG."
        ),
        classSupportedRadioModel(
            id = "wouxun_kg_935g",
            vendor = "Wouxun",
            model = "Wouxun KG-935G Plus (GMRS)",
            maxChannels = 999,
            bandsSummary = "GMRS 30 Repeater/Simplex + 969 Scanner Channels",
            supportedBandsMhz = listOf(136.0..174.0, 400.0..512.0),
            transport = TransportInterface.USB_OTG,
            supportsNativeRxOnly = true,
            eepromIdent = "WX935G",
            protocolNotes = "High-tier superheterodyne GMRS radio. Programmed via Red/Clear USB OTG cable."
        ),
        classSupportedRadioModel(
            id = "wouxun_kg_uv8d",
            vendor = "Wouxun",
            model = "Wouxun KG-UV8D / UV8D Plus",
            maxChannels = 999,
            bandsSummary = "VHF 136-174 / UHF 400-520 MHz Cross-Band",
            supportedBandsMhz = listOf(136.0..174.0, 400.0..520.0),
            transport = TransportInterface.USB_OTG,
            supportsNativeRxOnly = true,
            eepromIdent = "WXUV8D",
            protocolNotes = "Full duplex cross-band repeater handheld."
        ),

        // --- RADIODDITY ---
        classSupportedRadioModel(
            id = "radioddity_ga510",
            vendor = "Radioddity",
            model = "Radioddity GA-510 (10W)",
            maxChannels = 128,
            bandsSummary = "VHF 136-174 / UHF 400-480 MHz (10W High)",
            supportedBandsMhz = listOf(136.0..174.0, 400.0..480.0),
            transport = TransportInterface.USB_OTG,
            supportsNativeRxOnly = true,
            eepromIdent = "RDGA510",
            protocolNotes = "10W tri-power amateur handheld with CHIRP driver support."
        ),
        classSupportedRadioModel(
            id = "radioddity_gm30",
            vendor = "Radioddity",
            model = "Radioddity GM-30 (GMRS)",
            maxChannels = 250,
            bandsSummary = "GMRS Channels 1-30 + 220 DIY Scanner Memory",
            supportedBandsMhz = listOf(136.0..174.0, 400.0..520.0),
            transport = TransportInterface.USB_OTG,
            supportsNativeRxOnly = true,
            eepromIdent = "RDGM30",
            protocolNotes = "USB-C direct programming port using standard USB CDC serial."
        ),

        // --- YAESU ---
        classSupportedRadioModel(
            id = "yaesu_ft60r",
            vendor = "Yaesu",
            model = "Yaesu FT-60R",
            maxChannels = 1000,
            bandsSummary = "108-999 MHz Wide RX / 2m & 70cm TX",
            supportedBandsMhz = listOf(108.0..999.0),
            transport = TransportInterface.USB_OTG,
            supportsNativeRxOnly = true,
            eepromIdent = "YSFT60",
            protocolNotes = "Standard Yaesu ADMS clone mode over FTDI USB OTG serial cable."
        ),
        classSupportedRadioModel(
            id = "yaesu_ft65r",
            vendor = "Yaesu",
            model = "Yaesu FT-65R / FT-4XR",
            maxChannels = 200,
            bandsSummary = "VHF 136-174 / UHF 400-480 MHz (5W)",
            supportedBandsMhz = listOf(136.0..174.0, 400.0..480.0),
            transport = TransportInterface.USB_OTG,
            supportsNativeRxOnly = true,
            eepromIdent = "YSFT65",
            protocolNotes = "Compact rugged amateur handheld. Programmed via SCU-35 USB OTG cable."
        ),
        classSupportedRadioModel(
            id = "yaesu_ft70dr",
            vendor = "Yaesu",
            model = "Yaesu FT-70DR (C4FM/FM)",
            maxChannels = 900,
            bandsSummary = "108-579 MHz Wide RX / 144 & 430 MHz TX",
            supportedBandsMhz = listOf(108.0..580.0),
            transport = TransportInterface.USB_OTG,
            supportsNativeRxOnly = true,
            eepromIdent = "YSFT70",
            protocolNotes = "Standard Yaesu mini-USB programming interface."
        ),

        // --- KENWOOD ---
        classSupportedRadioModel(
            id = "kenwood_th_d74",
            vendor = "Kenwood",
            model = "Kenwood TH-D74A / TH-D72A",
            maxChannels = 1000,
            bandsSummary = "2m / 1.25m / 70cm Tri-Band with APRS",
            supportedBandsMhz = listOf(136.0..174.0, 216.0..260.0, 410.0..470.0),
            transport = TransportInterface.BOTH,
            supportsNativeRxOnly = true,
            eepromIdent = "KWTHD74",
            protocolNotes = "Supports serial memory read/write over USB OTG and Bluetooth SPP."
        ),
        classSupportedRadioModel(
            id = "kenwood_tm_v71a",
            vendor = "Kenwood",
            model = "Kenwood TM-V71A / TM-D710G",
            maxChannels = 1000,
            bandsSummary = "VHF 144-148 / UHF 430-450 MHz (50W Mobile)",
            supportedBandsMhz = listOf(118.0..524.0),
            transport = TransportInterface.USB_OTG,
            supportsNativeRxOnly = true,
            eepromIdent = "KWTMV71",
            protocolNotes = "Kenwood PG-5G serial interface protocol over USB OTG."
        ),

        // --- ICOM ---
        classSupportedRadioModel(
            id = "icom_id51a",
            vendor = "Icom",
            model = "Icom ID-51A / ID-52A",
            maxChannels = 1000,
            bandsSummary = "VHF 144-148 / UHF 430-450 MHz (Airband AM RX)",
            supportedBandsMhz = listOf(108.0..174.0, 382.0..479.0),
            transport = TransportInterface.BOTH,
            supportsNativeRxOnly = true,
            eepromIdent = "ICID51",
            protocolNotes = "Icom CI-V / clone serial framing over OPC-478UC USB OTG or Bluetooth SPP."
        ),
        classSupportedRadioModel(
            id = "icom_ic_v80",
            vendor = "Icom",
            model = "Icom IC-V80 / IC-T70A",
            maxChannels = 207,
            bandsSummary = "VHF 136-174 MHz (5.5W Rugged)",
            supportedBandsMhz = listOf(136.0..174.0),
            transport = TransportInterface.USB_OTG,
            supportsNativeRxOnly = true,
            eepromIdent = "ICV80",
            protocolNotes = "Rugged field favorite with OPC-478 USB programming adapter."
        ),

        // --- RETEVIS / TYT ---
        classSupportedRadioModel(
            id = "tyt_th_uv88",
            vendor = "TYT / Retevis",
            model = "TYT TH-UV88 / Retevis RT85",
            maxChannels = 200,
            bandsSummary = "VHF 136-174 / UHF 400-480 MHz",
            supportedBandsMhz = listOf(136.0..174.0, 400.0..480.0),
            transport = TransportInterface.USB_OTG,
            supportsNativeRxOnly = false,
            eepromIdent = "TYTUV88",
            protocolNotes = "Uses FRS Ch 1 (462.5625 MHz @ 0.5W) TX fallback when Scanner Safe mode is active."
        ),
        classSupportedRadioModel(
            id = "retevis_ra79",
            vendor = "TYT / Retevis",
            model = "Retevis RA79 / RA89 (IP68)",
            maxChannels = 200,
            bandsSummary = "VHF / UHF Multi-Band Airband Reception",
            supportedBandsMhz = listOf(108.0..136.0, 136.0..174.0, 400.0..520.0),
            transport = TransportInterface.USB_OTG,
            supportsNativeRxOnly = true,
            eepromIdent = "RTRA79",
            protocolNotes = "Standard K-Plug CHIRP serial clone."
        )
    )

    val allVendors: List<String> by lazy {
        allRadios.map { it.vendor }.distinct()
    }

    fun getRadiosForVendor(vendor: String): List<classSupportedRadioModel> =
        allRadios.filter { it.vendor.equals(vendor, ignoreCase = true) }

    fun findByModelName(name: String): classSupportedRadioModel =
        allRadios.find { it.model.equals(name, ignoreCase = true) || it.id.equals(name, ignoreCase = true) }
            ?: allRadios.first()
}

data class OffsetCalculationResult(
    val duplex: DuplexMode,
    val offsetHz: Long,
    val txFreqHz: Long,
    val bandName: String,
    val explanation: String
)

object RepeaterOffsetCalculator {

    /**
     * Computes standard ARRL / Public Safety / GMRS repeater offset and TX frequency
     * for any given RX frequency in Hz.
     */
    fun calculateStandardOffset(rxFreqHz: Long, forceSimplex: Boolean = false): OffsetCalculationResult {
        val mhz = rxFreqHz / 1_000_000.0
        if (forceSimplex) {
            return OffsetCalculationResult(
                duplex = DuplexMode.SIMPLEX,
                offsetHz = 0L,
                txFreqHz = rxFreqHz,
                bandName = detectBandName(mhz),
                explanation = "Simplex (TX = RX)"
            )
        }

        // 1. 6-Meter Amateur Band (51.0 - 54.0 MHz): Standard -1.000 MHz offset
        if (mhz in 51.6..54.0) {
            val offset = 1_000_000L
            return OffsetCalculationResult(
                duplex = DuplexMode.MINUS,
                offsetHz = offset,
                txFreqHz = rxFreqHz - offset,
                bandName = "6m VHF Amateur",
                explanation = "Standard 6m Repeater Shift (-1.000 MHz)"
            )
        }

        // 2. 2-Meter Amateur Band (144.0 - 148.0 MHz): Standard ±0.600 MHz offset
        if (mhz in 144.5..148.0) {
            val offset = 600_000L
            // Check national simplex calling & simplex sub-bands
            if (abs(mhz - 146.520) < 0.005 || mhz in 146.40..146.595 || mhz in 147.40..147.595) {
                return OffsetCalculationResult(
                    duplex = DuplexMode.SIMPLEX,
                    offsetHz = 0L,
                    txFreqHz = rxFreqHz,
                    bandName = "2m VHF Simplex",
                    explanation = "2m Simplex Sub-band (0.000 MHz)"
                )
            }
            val isPlus = (mhz in 146.00..146.395) || (mhz in 147.00..147.395)
            val duplex = if (isPlus) DuplexMode.PLUS else DuplexMode.MINUS
            val txHz = if (isPlus) rxFreqHz + offset else rxFreqHz - offset
            return OffsetCalculationResult(
                duplex = duplex,
                offsetHz = offset,
                txFreqHz = txHz,
                bandName = "2m VHF Amateur",
                explanation = "ARRL 2m Band Plan (${duplex.code}0.600 MHz)"
            )
        }

        // 3. 1.25-Meter Amateur Band (222.0 - 225.0 MHz): Standard -1.600 MHz offset
        if (mhz in 223.85..225.0) {
            val offset = 1_600_000L
            return OffsetCalculationResult(
                duplex = DuplexMode.MINUS,
                offsetHz = offset,
                txFreqHz = rxFreqHz - offset,
                bandName = "1.25m (220 MHz) Amateur",
                explanation = "Standard 1.25m Repeater Shift (-1.600 MHz)"
            )
        }

        // 4. 70-Centimeter Amateur Band (440.0 - 450.0 MHz): Standard ±5.000 MHz offset
        if (mhz in 440.0..450.0) {
            if (abs(mhz - 446.000) < 0.025) {
                return OffsetCalculationResult(
                    duplex = DuplexMode.SIMPLEX,
                    offsetHz = 0L,
                    txFreqHz = rxFreqHz,
                    bandName = "70cm UHF Simplex",
                    explanation = "70cm National Simplex (446.000 MHz)"
                )
            }
            val offset = 5_000_000L
            val isPlus = mhz < 445.0
            val duplex = if (isPlus) DuplexMode.PLUS else DuplexMode.MINUS
            val txHz = if (isPlus) rxFreqHz + offset else rxFreqHz - offset
            return OffsetCalculationResult(
                duplex = duplex,
                offsetHz = offset,
                txFreqHz = txHz,
                bandName = "70cm UHF Amateur",
                explanation = "Standard 70cm Repeater Shift (${duplex.code}5.000 MHz)"
            )
        }

        // 5. GMRS Repeater Channels (462.550 - 462.725 MHz): Standard +5.000 MHz offset
        if (mhz in 462.549..462.726) {
            val offset = 5_000_000L
            return OffsetCalculationResult(
                duplex = DuplexMode.PLUS,
                offsetHz = offset,
                txFreqHz = rxFreqHz + offset,
                bandName = "GMRS UHF Repeater",
                explanation = "GMRS Repeater Shift (+5.000 MHz)"
            )
        }

        // 6. UHF Public Safety / T-Band (453.0 - 460.0 MHz & 470.0 - 512.0 MHz)
        if (mhz in 453.0..454.999) {
            val offset = 5_000_000L
            return OffsetCalculationResult(
                duplex = DuplexMode.PLUS,
                offsetHz = offset,
                txFreqHz = rxFreqHz + offset,
                bandName = "UHF Public Safety",
                explanation = "UHF Public Safety Input (+5.000 MHz)"
            )
        }
        if (mhz in 470.0..512.0) {
            val offset = 3_000_000L
            return OffsetCalculationResult(
                duplex = DuplexMode.PLUS,
                offsetHz = offset,
                txFreqHz = rxFreqHz + offset,
                bandName = "UHF T-Band Public Safety",
                explanation = "UHF T-Band Repeater Input (+3.000 MHz)"
            )
        }

        // 7. NOAA Weather (162.400 - 162.550 MHz) -> Always RX Only
        if (mhz in 162.39..162.56) {
            return OffsetCalculationResult(
                duplex = DuplexMode.OFF,
                offsetHz = 0L,
                txFreqHz = RadioChannel.FRS1_FALLBACK_HZ,
                bandName = "NOAA Weather Radio",
                explanation = "NOAA Broadcast (RX-Only / TX Inhibited)"
            )
        }

        return OffsetCalculationResult(
            duplex = DuplexMode.SIMPLEX,
            offsetHz = 0L,
            txFreqHz = rxFreqHz,
            bandName = detectBandName(mhz),
            explanation = "VHF/UHF Simplex or Custom Split"
        )
    }

    fun detectBandName(mhz: Double): String = when {
        mhz in 50.0..54.0 -> "6m VHF"
        mhz in 108.0..136.999 -> "AM Airband"
        mhz in 137.0..143.999 -> "VHF Federal/Mil"
        mhz in 144.0..148.0 -> "2m VHF Amateur"
        mhz in 150.0..161.999 -> "VHF High Public Safety / Marine"
        mhz in 162.0..174.0 -> "NOAA / VHF Gov"
        mhz in 222.0..225.0 -> "1.25m Amateur"
        mhz in 420.0..450.0 -> "70cm UHF Amateur"
        mhz in 450.0..470.0 -> "UHF Public Safety / FRS / GMRS"
        mhz in 470.0..512.0 -> "UHF T-Band Public Safety"
        else -> "Wideband RF"
    }
}

object SafetyProfileEngine {

    fun applySafetyProfileToChannels(
        channels: List<RadioChannel>,
        safetyProfile: SafetyProfile,
        targetRadio: classSupportedRadioModel
    ): List<RadioChannel> {
        return channels.map { ch ->
            applySafetyProfileToChannel(ch, safetyProfile, targetRadio)
        }
    }

    fun applySafetyProfileToChannel(
        channel: RadioChannel,
        safetyProfile: SafetyProfile,
        targetRadio: classSupportedRadioModel
    ): RadioChannel {
        return when (safetyProfile) {
            SafetyProfile.RX_ONLY_INHIBIT -> {
                if (targetRadio.supportsNativeRxOnly) {
                    channel.copy(
                        duplex = DuplexMode.OFF,
                        txFreqHz = RadioChannel.FRS1_FALLBACK_HZ,
                        offsetHz = 0L,
                        power = PowerLevel.LOW,
                        rxOnly = true
                    )
                } else {
                    // Radio lacks hardware RX-only (e.g., Baofeng UV-5R Mini) -> route TX to FRS1 @ 0.5W Low
                    channel.copy(
                        duplex = DuplexMode.SPLIT,
                        txFreqHz = RadioChannel.FRS1_FALLBACK_HZ,
                        offsetHz = 0L,
                        txTone = "None",
                        power = PowerLevel.LOW,
                        rxOnly = true
                    )
                }
            }
            SafetyProfile.SAFE_TX_FALLBACK_FRS1 -> {
                channel.copy(
                    duplex = DuplexMode.SPLIT,
                    txFreqHz = RadioChannel.FRS1_FALLBACK_HZ,
                    offsetHz = 0L,
                    txTone = "None",
                    power = PowerLevel.LOW,
                    rxOnly = true
                )
            }
            SafetyProfile.STANDARD_TX -> {
                val mhz = channel.rxFreqHz / 1_000_000.0
                if (mhz in 162.39..162.56) {
                    channel.copy(
                        duplex = DuplexMode.OFF,
                        txFreqHz = RadioChannel.FRS1_FALLBACK_HZ,
                        power = PowerLevel.LOW,
                        rxOnly = true
                    )
                } else {
                    val calc = RepeaterOffsetCalculator.calculateStandardOffset(channel.rxFreqHz)
                    val restoredTxTone = if (channel.txTone == "None" && channel.rxTone != "None") {
                        channel.rxTone
                    } else {
                        channel.txTone
                    }
                    channel.copy(
                        duplex = calc.duplex,
                        offsetHz = calc.offsetHz,
                        txFreqHz = calc.txFreqHz,
                        txTone = restoredTxTone,
                        power = if (channel.power == PowerLevel.LOW) PowerLevel.HIGH else channel.power,
                        rxOnly = false
                    )
                }
            }
        }
    }
}
