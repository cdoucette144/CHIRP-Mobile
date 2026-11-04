package com.example.domain
import org.junit.Assert.*
import org.junit.Test
class MiniToneCodecTest {
 @Test fun normalDcsMatchesMiniTable() { assertEquals(52,MiniToneCodec.encode("D306N"));assertEquals(81,MiniToneCodec.encode("D506N"));assertEquals(88,MiniToneCodec.encode("D606N")) }
 @Test fun everyDcsValueRoundTrips() { for(value in 1..210) assertEquals(value,MiniToneCodec.encode(MiniToneCodec.decode(value))) }
 @Test fun ctcssAndDisabledRoundTrip() { assertEquals(1413,MiniToneCodec.encode("141.3"));assertEquals("141.3",MiniToneCodec.decode(1413));assertEquals(0,MiniToneCodec.encode("None")) }
 @Test(expected=IllegalArgumentException::class) fun rejectsUnsupportedDcs() { MiniToneCodec.encode("D777N") }
}
