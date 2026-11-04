package com.example.domain

import android.content.Context
import com.example.data.model.*
import org.json.JSONObject
import kotlin.math.abs

object RadioReferenceExportCatalog {
    fun load(context: Context): List<StateDirectory> {
        val root=JSONObject(context.assets.open("radioreference/catalog.json").bufferedReader().use{it.readText()})
        val rows=root.getJSONArray("channels")
        data class Entry(val stateCode:String,val state:String,val county:String,val channel:RadioChannel)
        val entries=(0 until rows.length()).map { index ->
            val row=rows.getJSONObject(index)
            val rx=row.getLong("rxHz");val tx=row.getLong("txHz")
            val label=listOf(row.optString("agency"),row.optString("description")).filter{it.isNotBlank()}.joinToString(" • ")
            val inputNote=if(row.optBoolean("inputListed")) "" else " • TX input not supplied by source"
            Entry(row.getString("stateCode"),row.getString("state"),row.getString("county"),
                RadioChannel(number=1,name=row.getString("name"),rxFreqHz=rx,txFreqHz=tx,
                    duplex=if(rx==tx) DuplexMode.SIMPLEX else DuplexMode.SPLIT,offsetHz=abs(tx-rx),
                    rxTone=row.getString("rxTone"),txTone=row.getString("txTone"),mode=row.getString("mode"),
                    power=PowerLevel.LOW,rxOnly=true,
                    notes=label+inputNote+" • RR export: "+row.getJSONArray("sources").getString(0)))
        }
        return entries.groupBy{it.stateCode}.map { (code,stateEntries) ->
            StateDirectory(code,stateEntries.first().state,stateEntries.groupBy{it.county}.map { (county,countyEntries) ->
                val channels=countyEntries.map{it.channel}.sortedWith(compareBy({it.name.lowercase()},{it.rxFreqHz},{it.rxTone}))
                    .mapIndexed { index,ch -> ch.copy(number=index+1) }
                CountyDirectory(county,listOf(AnalogCategoryGroup(
                    "rr_${code}_${county}","$county • All analog agencies",
                    "User-supplied RR exports • Police / Fire / EMS together • Monitor import default",
                    channels)))
            }.sortedBy{it.name})
        }.sortedBy{it.name}
    }
}
