package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.DuplexMode
import com.example.data.model.PowerLevel
import com.example.data.model.RadioChannel
import com.example.data.model.SafetyProfile
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChirpRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private val sampleChannels = listOf(
        RadioChannel(
            number = 1,
            name = "PLYM FD DISP",
            rxFreqHz = 154265000L,
            txFreqHz = 462562500L,
            duplex = DuplexMode.SPLIT,
            offsetHz = 0L,
            rxTone = "131.8",
            txTone = "None",
            mode = "FM",
            power = PowerLevel.LOW,
            rxOnly = true,
            notes = "Plymouth County Fire Dispatch"
        )
    )

    @Test
    fun createAndObserveZones_authenticatedOwner_succeeds() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = ChirpRepository(firestore)

        val createRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createZone(
                name = "Plymouth County Fire",
                region = "Plymouth County, MA",
                category = "Fire / EMS",
                safetyProfile = SafetyProfile.RX_ONLY_INHIBIT,
                targetRadioModel = "Baofeng UV-5R Mini",
                channels = sampleChannels
            )
        }
        assertTrue("Expected createZone to succeed: ${createRes.exceptionOrNull()}", createRes.isSuccess)
        val zoneId = createRes.getOrThrow()

        val emittedZones = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeUserZones(aliceUid).first { list -> list.any { it.id == zoneId } }
        }
        val createdZone = emittedZones.first { it.id == zoneId }
        assertEquals("Plymouth County Fire", createdZone.name)

        // Update zone
        val updateRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updateZone(createdZone.copy(name = "Plymouth County Fire & EMS"))
        }
        assertTrue("Expected updateZone to succeed: ${updateRes.exceptionOrNull()}", updateRes.isSuccess)
    }

    @Test
    fun getZoneById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val aliceRepo = ChirpRepository(firestore)
        val zoneId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createZone(
                name = "Plymouth County Police",
                region = "Plymouth County, MA",
                category = "Law Enforcement",
                safetyProfile = SafetyProfile.SAFE_TX_FALLBACK_FRS1,
                targetRadioModel = "Baofeng UV-5R Mini",
                channels = sampleChannels
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = ChirpRepository(firestore)
        val bobReadResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getZoneById(zoneId)
        }
        assertTrue("Expected cross-user read to fail", bobReadResult.isFailure)
        val fsEx = bobReadResult.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull("Expected FirebaseFirestoreException", fsEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx!!.code)
    }

    @Test
    fun publishAndFetchRemoteSharedCodeplug_crossUserPullSucceeds() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = ChirpRepository(firestore)
        val shareCode = "PLYM-${UUID.randomUUID().toString().take(6).uppercase()}"

        val zoneId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createZone(
                name = "Plymouth Fire Backup",
                region = "Plymouth County, MA",
                category = "Fire / EMS",
                safetyProfile = SafetyProfile.STANDARD_TX,
                targetRadioModel = "Baofeng UV-5R Mini",
                channels = sampleChannels
            ).getOrThrow()
        }

        val zoneDoc = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.getZoneById(zoneId).getOrThrow()
        }

        val publishRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.publishOrUpdateSharedCodeplug(
                zone = zoneDoc,
                shareCode = shareCode,
                authorCallsign = "W1PLY",
                releaseNotes = "Added tone 131.8 Hz"
            )
        }
        assertTrue("Expected publish to succeed: ${publishRes.exceptionOrNull()}", publishRes.isSuccess)
        assertEquals(1L, publishRes.getOrThrow().version)
        assertEquals(aliceUid, publishRes.getOrThrow().userId)

        // Bob signs in and pulls remote shared codeplug by shareCode
        signInTestUser(BOB_EMAIL)
        val bobRepo = ChirpRepository(firestore)
        val fetchedRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.fetchRemoteSharedCodeplug(shareCode)
        }
        assertTrue("Expected Bob to fetch shared codeplug: ${fetchedRes.exceptionOrNull()}", fetchedRes.isSuccess)
        assertEquals("Plymouth Fire Backup", fetchedRes.getOrThrow().name)
    }

    @Test
    fun observeUserZones_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = ChirpRepository(firestore)

        var caught: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeUserZones("unauth_uid").first()
            }
        } catch (t: Throwable) {
            caught = t
        }
        assertNotNull("Expected unauthenticated query to throw", caught)
        val fsEx = caught!!.unwrapFirestoreException()
        assertNotNull("Expected nested FirebaseFirestoreException, got $caught", fsEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx!!.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice@chirpfield.test"
        const val BOB_EMAIL = "bob@chirpfield.test"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
