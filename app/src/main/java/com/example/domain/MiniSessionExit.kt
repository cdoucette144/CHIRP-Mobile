package com.example.domain

import kotlinx.coroutines.delay

/** Programming exit observed in Mini BLE reference tools; do not assume disconnect commits. */
object MiniSessionExit {
    suspend fun finish(transport: RadioSerialTransport, reconnectWaitMs: Long = 5000,
                       pause: suspend (Long) -> Unit = { delay(it) }) {
        try {
            pause(1000)
            transport.writeBytes(byteArrayOf(0x45))
            pause(1000)
        } finally {
            transport.close()
        }
        if(reconnectWaitMs>0) pause(reconnectWaitMs)
    }
}
