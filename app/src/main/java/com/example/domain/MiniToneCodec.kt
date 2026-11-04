package com.example.domain

import java.util.Locale

/** UV-17-family tone encoding: one-based normal DCS, 0x6A-based inverted DCS. */
object MiniToneCodec {
    private val codes = listOf(23,25,26,31,32,36,43,47,51,53,54,65,71,72,73,74,114,115,116,122,125,131,132,134,143,145,152,155,156,162,165,172,174,205,212,223,225,226,243,244,245,246,251,252,255,261,263,265,266,271,274,306,311,315,325,331,332,343,346,351,356,364,365,371,411,412,413,423,431,432,445,446,452,454,455,462,464,465,466,503,506,516,523,526,532,546,565,606,612,624,627,631,632,645,654,662,664,703,712,723,731,732,734,743,754)
    fun encode(text: String): Int {
        if(text.isBlank() || text=="None") return 0
        if(text.startsWith("DCS(raw:") && text.endsWith(")")) {
            return text.removePrefix("DCS(raw:").removeSuffix(")").toInt().also { require(it in 1..210) { "Invalid raw DCS value" } }
        }
        val match=Regex("D([0-7]{3})([NR])",RegexOption.IGNORE_CASE).matchEntire(text.trim())
        if(match!=null) {
            val index=codes.indexOf(match.groupValues[1].toInt())
            require(index>=0) { "Unsupported DCS code: $text" }
            return index + if(match.groupValues[2].uppercase()=="R") 0x6a else 1
        }
        val hz=text.toDoubleOrNull() ?: error("Unsupported tone: $text")
        require(hz.isFinite() && hz in 60.0..300.0) { "Invalid CTCSS tone" }
        return (hz*10).toInt()
    }
    fun decode(value: Int): String = when {
        value==0 || value==65535 -> "None"
        value>=600 -> String.format(Locale.US,"%.1f",value/10.0)
        value in 1..105 -> "D%03dN".format(codes[value-1])
        value in 106..210 -> "D%03dR".format(codes[value-106])
        else -> error("Invalid Mini tone value: $value")
    }
}
