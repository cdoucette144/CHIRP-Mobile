package com.example.domain

data class MiniWriteBlock(val address: Int, val payload: ByteArray, val sourceOffset: Int, val sourceLength: Int)

/** Full UV-5R Mini image layout, 128-byte BLE writes including region-tail padding. */
object MiniWritePlan {
    fun fullImage(image: ByteArray, blockSize: Int = 128): List<MiniWriteBlock> {
        require(image.size == 0x8240) { "Complete Mini image required" }
        require(blockSize == 64 || blockSize == 128)
        var offset = 0
        val blocks = mutableListOf<MiniWriteBlock>()
        for ((start,size) in listOf(0x0000 to 0x8040,0x9000 to 0x0040,0xa000 to 0x01c0)) {
            for (address in start until start+size step blockSize) {
                val length = minOf(blockSize,start+size-address)
                val payload = ByteArray(blockSize) { 0xff.toByte() }
                image.copyInto(payload,0,offset,offset+length)
                blocks += MiniWriteBlock(address,payload,offset,length)
                offset += length
            }
        }
        check(offset == image.size)
        return blocks
    }
}
