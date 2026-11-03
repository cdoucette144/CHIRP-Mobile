package com.example.domain

/** Restrict hardware testing to one existing channel's name. */
object MiniNameEditGate {
    fun validate(original: ByteArray, desired: ByteArray) {
        require(original.size == 0x8240 && desired.size == original.size) { "Complete images required" }
        val differences = original.indices.filter { original[it] != desired[it] }
        if (differences.isEmpty()) return
        val slots = differences.map { it / 32 }.distinct()
        require(slots.size == 1 && slots.single() in 0..998) { "Only one channel name may change in this test build" }
        val slot = slots.single()
        require(original[slot*32] != 0xff.toByte()) { "Select an existing channel" }
        require(differences.all { it % 32 in 20..31 }) { "Only the channel name may change; frequencies, tones, power and channel count must remain unchanged" }
    }
}
