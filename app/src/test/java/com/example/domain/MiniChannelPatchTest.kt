package com.example.domain

import com.example.data.model.*
import org.junit.Assert.*
import org.junit.Test

class MiniChannelPatchTest {
    private val channel = RadioChannel(number=1,name="TEST",rxFreqHz=146520000,txFreqHz=146520000,power=PowerLevel.LOW)
    private fun image() = ByteArray(0x8240) { 0xff.toByte() }

    @Test fun preservesUnmodelledMemoryAndFlags() {
        val base = MiniChannelPatch.apply(image(),listOf(channel))
        base[12]=0x53; base[13]=0x23; base[16]=0x12; base[0x8100]=0x47
        val changed = MiniChannelPatch.apply(base,listOf(channel.copy(name="RENAMED")))
        assertEquals(0x53.toByte(),changed[12])
        assertEquals(0x23.toByte(),changed[13])
        assertEquals(0x12.toByte(),changed[16])
        assertArrayEquals(base.copyOfRange(32,base.size),changed.copyOfRange(32,changed.size))
    }
    @Test fun downloadedSimplexDoesNotGainRepeaterOffset() {
        val ch=channel.copy(rxFreqHz=462562500,txFreqHz=462562500,duplex=DuplexMode.SIMPLEX,rxTone="DCS(raw:101)",txTone="DCS(raw:101)",power=PowerLevel.HIGH,mode="NFM",name="Channel 1")
        val base=MiniChannelPatch.apply(image(),listOf(ch))
        assertArrayEquals(base,MiniChannelPatch.apply(base,listOf(ch)))
        assertArrayEquals(base.copyOfRange(0,4),base.copyOfRange(4,8))
    }
    @Test fun unchangedChannelsProduceIdenticalImage() {
        val base=MiniChannelPatch.apply(image(),listOf(channel))
        assertArrayEquals(base,MiniChannelPatch.apply(base,listOf(channel)))
    }
    @Test fun usesLittleEndianBcdAndCorrectFlags() {
        val result=MiniChannelPatch.apply(image(),listOf(channel.copy(mode="NFM",duplex=DuplexMode.OFF)))
        assertArrayEquals(byteArrayOf(0x00,0x20,0x65,0x14),result.copyOfRange(0,4))
        assertTrue(result.copyOfRange(4,8).all { it==0xff.toByte() })
        assertEquals(1,result[14].toInt() and 3)
        assertEquals(0,result[15].toInt() and 0x40)
    }
    @Test fun deletionPreservesSettings() {
        val base=MiniChannelPatch.apply(image(),listOf(channel))
        base[0x8000]=0x21
        val result=MiniChannelPatch.apply(base,emptyList())
        assertTrue(result.copyOfRange(0,32).all { it==0xff.toByte() })
        assertEquals(0x21.toByte(),result[0x8000])
    }
    @Test(expected=IllegalArgumentException::class) fun rejectsDuplicateSlots() {
        MiniChannelPatch.apply(image(),listOf(channel,channel))
    }
    @Test(expected=IllegalArgumentException::class) fun rejectsDigitalModes() {
        MiniChannelPatch.apply(image(),listOf(channel.copy(mode="DMR")))
    }
    @Test(expected=IllegalArgumentException::class) fun rejectsInvalidTransmitFrequency() {
        MiniChannelPatch.apply(image(),listOf(channel.copy(txFreqHz=900000000)))
    }
}
