package com.example.domain
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class MiniSessionExitTest {
 @Test fun exitSettlesBeforeCloseAndReconnect() = runBlocking {
  val events=mutableListOf<String>()
  val t=object:RadioSerialTransport {
   override suspend fun writeBytes(data:ByteArray) { events += "write:"+data.joinToString(",") }
   override suspend fun readBytes(expectedCount:Int,timeoutMs:Long)=byteArrayOf()
   override fun clearRxBuffer() {}
   override fun close() { events += "close" }
  }
  MiniSessionExit.finish(t,pause={ events += "wait:$it" })
  assertEquals(listOf("wait:1000","write:69","wait:1000","close","wait:5000"),events)
 }
 @Test fun failedExitStillClosesTransport() = runBlocking {
  var closed=false
  val t=object:RadioSerialTransport {
   override suspend fun writeBytes(data:ByteArray) { throw java.io.IOException("failure") }
   override suspend fun readBytes(expectedCount:Int,timeoutMs:Long)=byteArrayOf()
   override fun clearRxBuffer() {}
   override fun close() { closed=true }
  }
  try { MiniSessionExit.finish(t,pause={});fail("Expected error") } catch(expected:java.io.IOException) {}
  assertTrue(closed)
 }
}
