package com.example.domain
import org.junit.Assert.*
import org.junit.Test
class RadioReferenceCsvTest {
 @Test fun actualExportHeadersPreserveSeparateTonesAndExactFrequency() {
  val csv="\"Frequency Output\",\"Frequency Input\",\"Alpha Tag\",\"PL Output Tone\",\"PL Input Tone\",Mode\n453.45000,458.45000,\"Ply Cty MA-1\",306 DPL,141.3 PL,FMN\n154.31000,0.00000,\"Brockton FD1\",CSQ,,FMN"
  val r=RadioReferenceCsv.parse(csv)
  assertEquals(2,r.channels.size)
  assertEquals("Ply Cty MA-1",r.channels[0].name)
  assertEquals(453450000L,r.channels[0].rxFreqHz)
  assertEquals(458450000L,r.channels[0].txFreqHz)
  assertEquals("D306N",r.channels[0].rxTone)
  assertEquals("141.3",r.channels[0].txTone)
  assertEquals(154310000L,r.channels[1].txFreqHz)
  assertEquals("None",r.channels[1].txTone)
 }
 @Test fun preservesAlphaTagsAndCombinesAgencies() {
  val csv="Frequency,Input,Alpha Tag,Description,Mode,Tone\n154.310,154.310,Brockton FD1,Fire,FMN,94.8 PL\n482.7125,485.7125,Brockton PD,Police,NFM,203.5 PL"
  val r=RadioReferenceCsv.parse(csv)
  assertEquals(listOf("Brockton FD1","Brockton PD"),r.channels.map{it.name})
  assertEquals(485712500L,r.channels[1].txFreqHz)
  assertEquals("NFM",r.channels[0].mode)
 }
 @Test fun excludesDigitalAndParsesDcsAndQuotedFields() {
  val csv="Frequency,Alpha Tag,Description,Mode,Tone\n453.45,Fire MA1,\"Fire, mutual aid\",FMN,306 DPL\n155.0,Digital,Police,P25,123 NAC"
  val r=RadioReferenceCsv.parse(csv)
  assertEquals(1,r.channels.size);assertEquals(1,r.skipped)
  assertEquals("D306N",r.channels[0].rxTone);assertEquals("Fire, mutual aid",r.channels[0].notes)
 }
 @Test(expected=IllegalStateException::class) fun rejectsTrunkedTalkgroupCsv() {
  RadioReferenceCsv.parse("Decimal,Alpha Tag,Mode\n123,Talkgroup,D")
 }
}
