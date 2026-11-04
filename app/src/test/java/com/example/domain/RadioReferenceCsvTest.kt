package com.example.domain
import org.junit.Assert.*
import org.junit.Test
class RadioReferenceCsvTest {
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
