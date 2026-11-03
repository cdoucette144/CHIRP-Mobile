package com.example.domain

import org.junit.Assert.*
import org.junit.Test

class MiniWritePlanTest {
    @Test fun everyImageByteIsSentExactlyOnce() {
        val image=ByteArray(0x8240) { ((it*17+3)%256).toByte() }
        val plan=MiniWritePlan.fullImage(image)
        val restored=ByteArray(image.size)
        val counts=IntArray(image.size)
        plan.forEach { block ->
            assertEquals(128,block.payload.size)
            block.payload.copyInto(restored,block.sourceOffset,0,block.sourceLength)
            for(i in block.sourceOffset until block.sourceOffset+block.sourceLength) counts[i]++
            assertTrue(block.payload.drop(block.sourceLength).all { it==0xff.toByte() })
        }
        assertArrayEquals(image,restored)
        assertTrue(counts.all { it==1 })
        assertEquals(262,plan.size)
        assertEquals(0x8000,plan[256].address)
        assertEquals(0x9000,plan[257].address)
        assertEquals(0xa180,plan.last().address)
    }
    @Test fun fullPlanRepopulatesErasedFlashPages() {
        val image=ByteArray(0x8240) { ((it*3+5)%256).toByte() }
        val flash=ByteArray(0xb000) { 0xff.toByte() }
        val erased=mutableSetOf<Int>()
        MiniWritePlan.fullImage(image).forEach { block ->
            val page=block.address/4096
            if(erased.add(page)) flash.fill(0xff.toByte(),page*4096,(page+1)*4096)
            block.payload.copyInto(flash,block.address)
        }
        assertArrayEquals(image.copyOfRange(0,0x8040),flash.copyOfRange(0,0x8040))
        assertArrayEquals(image.copyOfRange(0x8040,0x8080),flash.copyOfRange(0x9000,0x9040))
        assertArrayEquals(image.copyOfRange(0x8080,0x8240),flash.copyOfRange(0xa000,0xa1c0))
    }
    @Test(expected=IllegalArgumentException::class) fun rejectsIncompleteImage() {
        MiniWritePlan.fullImage(ByteArray(128))
    }
}
