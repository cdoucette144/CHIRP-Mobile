package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.RadioChannel
import com.example.data.model.SafetyProfile
import com.example.data.model.SharedCodeplugDocument
import com.example.data.model.ZoneDocument
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ChirpRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth get() = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeUserZones(userId: String): Flow<List<ZoneDocument>> {
        val path = "zones"
        return db.collection(path)
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    ZoneDocument::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                if (error is Exception) {
                    handleFirestoreError(error, OperationType.LIST, path)
                }
                throw error
            }
    }

    fun observeUserSharedCodeplugs(userId: String): Flow<List<SharedCodeplugDocument>> {
        val path = "shared_codeplugs"
        return db.collection(path)
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    SharedCodeplugDocument::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                if (error is Exception) {
                    handleFirestoreError(error, OperationType.LIST, path)
                }
                throw error
            }
    }

    suspend fun getZoneById(zoneId: String): Result<ZoneDocument> {
        val docRef = db.collection("zones").document(zoneId)
        return try {
            val snap = docRef.get().await()
            val zone = snap.toObject(
                ZoneDocument::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: throw NoSuchElementException("Zone $zoneId not found")
            Result.success(zone)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun createZone(
        name: String,
        region: String,
        category: String,
        safetyProfile: SafetyProfile,
        targetRadioModel: String,
        channels: List<RadioChannel>,
        shareCode: String = ""
    ): Result<String> {
        val uid = requireUserId()
        val colRef = db.collection("zones")
        val docRef = colRef.document()
        val payload = mapOf(
            "userId" to uid,
            "name" to name.trim().take(80).ifEmpty { "Custom Zone" },
            "region" to region.trim().take(80).ifEmpty { "Local Region" },
            "category" to category.trim().take(40).ifEmpty { "Custom" },
            "safetyProfile" to safetyProfile.id,
            "targetRadioModel" to targetRadioModel.trim().take(80).ifEmpty { "Baofeng UV-5R Mini" },
            "shareCode" to shareCode.trim().take(32),
            "version" to 1L,
            "channelSpecs" to channels.take(100).map { it.toSpecString() },
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateZone(
        zone: ZoneDocument,
        updatedChannels: List<RadioChannel>? = null
    ): Result<Unit> {
        requireUserId()
        require(zone.id.isNotBlank()) { "Zone ID must not be blank for update" }
        val docRef = db.collection("zones").document(zone.id)
        val specs = updatedChannels?.take(100)?.map { it.toSpecString() } ?: zone.channelSpecs.take(100)
        val updateMap = mapOf(
            "name" to zone.name.trim().take(80).ifEmpty { "Custom Zone" },
            "region" to zone.region.trim().take(80).ifEmpty { "Local Region" },
            "category" to zone.category.trim().take(40).ifEmpty { "Custom" },
            "safetyProfile" to zone.safetyProfile,
            "targetRadioModel" to zone.targetRadioModel.trim().take(80).ifEmpty { "Baofeng UV-5R Mini" },
            "shareCode" to zone.shareCode.trim().take(32),
            "version" to zone.version.coerceIn(1L, 100000L),
            "channelSpecs" to specs,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.update(updateMap).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteZone(zoneId: String): Result<Unit> {
        requireUserId()
        val docRef = db.collection("zones").document(zoneId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun publishOrUpdateSharedCodeplug(
        zone: ZoneDocument,
        shareCode: String,
        authorCallsign: String,
        releaseNotes: String
    ): Result<SharedCodeplugDocument> {
        val uid = requireUserId()
        val sanitizedCode = shareCode.uppercase().replace(Regex("[^A-Z0-9_\\-]"), "").take(32)
        require(sanitizedCode.length >= 3) { "Share code must be at least 3 alphanumeric characters" }

        val docRef = db.collection("shared_codeplugs").document(sanitizedCode)
        return try {
            val existingSnap = try {
                docRef.get().await()
            } catch (unused: Exception) {
                null
            }

            val nextVersion = if (existingSnap != null && existingSnap.exists()) {
                val prevVersion = existingSnap.getLong("version") ?: 1L
                (prevVersion + 1L).coerceAtMost(100000L)
            } else {
                1L
            }

            if (existingSnap != null && existingSnap.exists()) {
                val updatePayload = mapOf(
                    "authorCallsign" to authorCallsign.trim().take(32).ifEmpty { "FIELD-OP" },
                    "name" to zone.name.trim().take(80).ifEmpty { "Shared Zone" },
                    "region" to zone.region.trim().take(80).ifEmpty { "Regional" },
                    "category" to zone.category.trim().take(40).ifEmpty { "Custom" },
                    "safetyProfile" to zone.safetyProfile,
                    "targetRadioModel" to zone.targetRadioModel.trim().take(80).ifEmpty { "Baofeng UV-5R Mini" },
                    "version" to nextVersion,
                    "releaseNotes" to releaseNotes.trim().take(300),
                    "channelSpecs" to zone.channelSpecs.take(100),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updatePayload).await()
            } else {
                val createPayload = mapOf(
                    "userId" to uid,
                    "authorCallsign" to authorCallsign.trim().take(32).ifEmpty { "FIELD-OP" },
                    "shareCode" to sanitizedCode,
                    "name" to zone.name.trim().take(80).ifEmpty { "Shared Zone" },
                    "region" to zone.region.trim().take(80).ifEmpty { "Regional" },
                    "category" to zone.category.trim().take(40).ifEmpty { "Custom" },
                    "safetyProfile" to zone.safetyProfile,
                    "targetRadioModel" to zone.targetRadioModel.trim().take(80).ifEmpty { "Baofeng UV-5R Mini" },
                    "version" to 1L,
                    "releaseNotes" to releaseNotes.trim().take(300),
                    "channelSpecs" to zone.channelSpecs.take(100),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(createPayload).await()
            }

            if (zone.id.isNotBlank()) {
                updateZone(zone.copy(shareCode = sanitizedCode, version = nextVersion))
            }

            val updatedSnap = docRef.get().await()
            val resultDoc = updatedSnap.toObject(
                SharedCodeplugDocument::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: SharedCodeplugDocument(
                id = sanitizedCode,
                userId = uid,
                authorCallsign = authorCallsign,
                shareCode = sanitizedCode,
                name = zone.name,
                region = zone.region,
                category = zone.category,
                safetyProfile = zone.safetyProfile,
                targetRadioModel = zone.targetRadioModel,
                version = nextVersion,
                releaseNotes = releaseNotes,
                channelSpecs = zone.channelSpecs
            )
            Result.success(resultDoc)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun fetchRemoteSharedCodeplug(shareCode: String): Result<SharedCodeplugDocument> {
        val sanitizedCode = shareCode.trim().uppercase().replace(Regex("[^A-Z0-9_\\-]"), "").take(32)
        if (sanitizedCode.length < 3) {
            return Result.failure(IllegalArgumentException("Enter a valid share code (at least 3 characters)."))
        }
        val docRef = db.collection("shared_codeplugs").document(sanitizedCode)
        return try {
            val snap = docRef.get().await()
            if (!snap.exists()) {
                return Result.failure(NoSuchElementException("No remote codeplug found with code '$sanitizedCode'."))
            }
            val shared = snap.toObject(
                SharedCodeplugDocument::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return Result.failure(IllegalStateException("Invalid codeplug format."))
            Result.success(shared)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }
}
