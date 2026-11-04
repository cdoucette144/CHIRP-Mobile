package com.example.domain
import com.example.data.model.*
import org.junit.Assert.*
import org.junit.Test
class ChannelBatchEditTest {
 @Test fun deletionUsesOriginalSelectionBeforeRenumbering() {
  val rows=(1..5).map{RadioChannel(number=it,name="CH $it")}
  val result=ChannelBatchEdit.delete(rows,setOf(2,4))
  assertEquals(listOf("CH 1","CH 3","CH 5"),result.map{it.name});assertEquals(listOf(1,2,3),result.map{it.number})
 }
 @Test fun bulkEditPreservesFrequenciesTonesAndUnselectedRows() {
  val a=RadioChannel(number=1,rxTone="D306N",txTone="141.3",fhssPrivacy=true)
  val b=a.copy(number=2)
  val result=ChannelBatchEdit.update(listOf(a,b),setOf(1),"NFM",PowerLevel.LOW)
  assertEquals(a.copy(mode="NFM",power=PowerLevel.LOW),result[0]);assertEquals(b,result[1])
 }
}
