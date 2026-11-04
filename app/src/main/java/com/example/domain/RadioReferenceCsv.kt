package com.example.domain

import com.example.data.model.*

data class AnalogCsvImport(val channels: List<RadioChannel>,val skipped: Int)

object RadioReferenceCsv {
    fun parse(text: String): AnalogCsvImport {
        val rows=rows(text.removePrefix("\uFEFF"))
        require(rows.isNotEmpty()) { "CSV is empty" }
        val header=rows.first().map { it.trim().lowercase() }
        fun column(vararg names:String)=names.firstNotNullOfOrNull { name -> header.indexOf(name).takeIf{it>=0} }
        val frequency=column("frequency","output","rx frequency") ?: error("Conventional frequency CSV required; trunked talkgroups are not supported")
        val alpha=column("alpha tag","alphatag","name") ?: error("CSV needs an Alpha Tag column")
        val mode=column("mode") ?: error("CSV needs a Mode column to exclude digital channels")
        val input=column("input","input frequency","tx frequency")
        val tone=column("tone")
        val description=column("description")
        var skipped=0
        val channels=rows.drop(1).filter { row -> row.any{it.isNotBlank()} }.mapNotNull { row ->
            fun cell(index:Int?)=index?.let{row.getOrNull(it)?.trim()}.orEmpty()
            val analog=when(cell(mode).uppercase()) { "FM","FMN","NFM","AM" -> if(cell(mode).uppercase()=="FMN") "NFM" else cell(mode).uppercase();else -> { skipped++;return@mapNotNull null } }
            val rx=cell(frequency).toDoubleOrNull()
            if(rx==null || !rx.isFinite() || rx<=0) { skipped++;return@mapNotNull null }
            val name=cell(alpha)
            if(name.isBlank()) { skipped++;return@mapNotNull null }
            val sourceTone=cell(tone)
            val parsedTone=when {
                sourceTone.isBlank() || sourceTone.uppercase() in listOf("CSQ","NONE","SEARCH") -> "None"
                Regex("\\d{2,3}(\\.\\d+)?\\s*PL",RegexOption.IGNORE_CASE).matches(sourceTone) -> sourceTone.substringBefore(" ").trim()
                Regex("[0-7]{3}\\s*DPL",RegexOption.IGNORE_CASE).matches(sourceTone) -> "D"+sourceTone.substringBefore(" ").padStart(3,'0')+"N"
                Regex("D[0-7]{3}[NR]",RegexOption.IGNORE_CASE).matches(sourceTone) -> sourceTone.uppercase()
                sourceTone.toDoubleOrNull()!=null -> sourceTone
                else -> { skipped++;return@mapNotNull null }
            }
            val tx=cell(input).toDoubleOrNull()?.takeIf{it.isFinite() && it>0} ?: rx
            val rxHz=RadioChannel.mhzToHz(rx);val txHz=RadioChannel.mhzToHz(tx)
            RadioChannel(number=0,name=name,rxFreqHz=rxHz,txFreqHz=txHz,
                duplex=if(txHz==rxHz) DuplexMode.SIMPLEX else DuplexMode.SPLIT,
                offsetHz=kotlin.math.abs(txHz-rxHz),rxTone=parsedTone,txTone=parsedTone,
                mode=analog,power=PowerLevel.LOW,notes=cell(description))
        }.mapIndexed { index,ch -> ch.copy(number=index+1) }
        return AnalogCsvImport(channels,skipped)
    }
    private fun rows(text:String):List<List<String>> {
        val rows=mutableListOf<List<String>>();val row=mutableListOf<String>();val field=StringBuilder()
        var quoted=false;var index=0
        fun endField(){row+=field.toString();field.setLength(0)}
        fun endRow(){endField();rows+=row.toList();row.clear()}
        while(index<text.length) {
            val c=text[index]
            when {
                c=='"' && quoted && index+1<text.length && text[index+1]=='"' -> {field.append('"');index++}
                c=='"' -> quoted=!quoted
                c==',' && !quoted -> endField()
                c=='\n' && !quoted -> endRow()
                c=='\r' && !quoted -> {if(index+1<text.length && text[index+1]=='\n')index++;endRow()}
                else -> field.append(c)
            }
            index++
        }
        require(!quoted) { "Unclosed CSV quote" }
        if(field.isNotEmpty() || row.isNotEmpty())endRow()
        return rows
    }
}
