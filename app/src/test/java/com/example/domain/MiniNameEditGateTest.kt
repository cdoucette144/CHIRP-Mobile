package com.example.domain

import org.junit.Test

class MiniNameEditGateTest {
    private fun image()=ByteArray(0x8240) { 0xff.toByte() }.apply { this[0]=0;this[32]=0 }
    @Test fun acceptsOneExistingName() { val a=image();val b=a.copyOf();b[20]=65;MiniNameEditGate.validate(a,b) }
    @Test fun acceptsNoChanges() { val a=image();MiniNameEditGate.validate(a,a.copyOf()) }
    @Test(expected=IllegalArgumentException::class) fun rejectsFrequencyChanges() { val a=image();val b=a.copyOf();b[4]=1;MiniNameEditGate.validate(a,b) }
    @Test(expected=IllegalArgumentException::class) fun rejectsTwoNames() { val a=image();val b=a.copyOf();b[20]=65;b[52]=66;MiniNameEditGate.validate(a,b) }
    @Test(expected=IllegalArgumentException::class) fun rejectsDeletedChannel() { val a=image();val b=a.copyOf();b.fill(0xff.toByte(),0,32);MiniNameEditGate.validate(a,b) }
    @Test(expected=IllegalArgumentException::class) fun rejectsSettingsChanges() { val a=image();val b=a.copyOf();b[0x8000]=0;MiniNameEditGate.validate(a,b) }
}
