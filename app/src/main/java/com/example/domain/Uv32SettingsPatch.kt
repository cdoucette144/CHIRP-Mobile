package com.example.domain

import com.example.data.model.RadioSettingsState
import com.example.data.model.Uv32SettingsSnapshot

/** Patch only settings changed since the UV-32 download that populated the editor. */
internal object Uv32SettingsPatch {
    fun apply(image: ByteArray, settings: RadioSettingsState): ByteArray {
        val baseline = requireNotNull(settings.uv32Downloaded) { "Download the UV-32 before editing its settings." }
        val fresh = Uv32SettingsSnapshot.fromImage(image)
        val updated = image.copyOf()
        if (baseline.squelch != null && settings.squelchLevel != baseline.squelch) {
            require(fresh.squelch == baseline.squelch) { "Radio squelch changed since the last download." }
            require(settings.squelchLevel in 0..9)
            updated[0x8040] = settings.squelchLevel.toByte()
        }
        if (baseline.keypadBeep != null && settings.keypadBeep != baseline.keypadBeep) {
            require(fresh.keypadBeep == baseline.keypadBeep) { "Radio keypad beep changed since the last download." }
            updated[0x8046] = if (settings.keypadBeep) 1 else 0
        }
        require(settings.uv32ZoneNames.size == 10)
        settings.uv32ZoneNames.forEachIndexed { index, name ->
            if (name != baseline.zoneNames[index]) {
                require(fresh.zoneNames[index] == baseline.zoneNames[index]) { "Zone ${index + 1} changed since the last download." }
                require(name.length <= 16 && name.all { it in ' '..'~' }) { "Zone names allow up to 16 printable ASCII characters." }
                val offset = 0x8280 + index * 16
                updated.fill(0xff.toByte(), offset, offset + 16)
                name.toByteArray(Charsets.US_ASCII).copyInto(updated, offset)
            }
        }
        return updated
    }
}
