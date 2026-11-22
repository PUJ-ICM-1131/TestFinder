package com.example.proyecto.data.repository

import com.example.proyecto.data.model.UserLocation
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class LocationRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    fun getOnlineUsers(): Flow<List<UserLocation>> = callbackFlow {
        val listener = firestore.collection("users")
            .limit(100)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val now = System.currentTimeMillis()
                val users = snapshot.documents.mapNotNull { doc ->
                    try {
                        val user = doc.toObject(UserLocation::class.java)
                        if (user != null && user.latitude != null && user.longitude != null) {
                            val diff = Math.abs(now - user.lastUpdated)
                            val isRecent = diff < 24 * 60 * 60 * 1000L
                            if (user.isConnected || isRecent) {
                                user
                            } else null
                        } else null
                    } catch (_: Exception) {
                        null
                    }
                }
                trySend(users)
            }
        awaitClose { listener.remove() }
    }

    suspend fun updateLocation(
        uid: String,
        name: String,
        email: String,
        lat: Double,
        lng: Double,
        photoUrl: String? = null
    ): Result<Unit> {
        return try {
            val point = mapOf("lat" to lat, "lng" to lng)
            val updates = mutableMapOf<String, Any>(
                "uid" to uid,
                "name" to name.ifEmpty { "Usuario" },
                "email" to email,
                "latitude" to lat,
                "longitude" to lng,
                "isConnected" to true,
                "locationHistory" to FieldValue.arrayUnion(point),
                "lastUpdated" to System.currentTimeMillis()
            )
            photoUrl?.let { updates["photoUrl"] = it }

            firestore.collection("users").document(uid)
                .set(updates, SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setConnectedState(uid: String, isConnected: Boolean): Result<Unit> {
        return try {
            val updates = if (isConnected) {
                mapOf<String, Any>(
                    "isConnected" to true,
                    "lastUpdated" to System.currentTimeMillis()
                )
            } else {
                mapOf<String, Any>(
                    "isConnected" to false,
                    "lastUpdated" to System.currentTimeMillis()
                )
            }
            firestore.collection("users").document(uid)
                .set(updates, SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
