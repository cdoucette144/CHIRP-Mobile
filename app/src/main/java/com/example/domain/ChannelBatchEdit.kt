package com.example.domain
import com.example.data.model.*
object ChannelBatchEdit {
 fun delete(channels:List<RadioChannel>,numbers:Set<Int>)=channels.filterNot{it.number in numbers}.mapIndexed{index,ch->ch.copy(number=index+1)}
 fun update(channels:List<RadioChannel>,numbers:Set<Int>,mode:String?,power:PowerLevel?):List<RadioChannel> {
  require(mode==null || mode in listOf("FM","NFM"))
  return channels.map { ch -> if(ch.number in numbers) ch.copy(mode=mode ?: ch.mode,power=power ?: ch.power) else ch }
 }
}
