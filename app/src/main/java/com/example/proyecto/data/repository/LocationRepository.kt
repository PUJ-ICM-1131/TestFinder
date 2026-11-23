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
                val users = snapshot.documents.mapNotNull { doc ->
                    try {
                        val rawUser = doc.toObject(UserLocation::class.java)
                        val uid = if (rawUser != null && rawUser.uid.isNotBlank()) rawUser.uid else doc.id
                        val lat = rawUser?.latitude ?: parseDouble(doc, "latitude", "lat")
                        val lng = rawUser?.longitude ?: parseDouble(doc, "longitude", "lng")

                        if (lat != null && lng != null) {
                            val name = rawUser?.name?.takeIf { it.isNotBlank() }
                                ?: doc.getString("name")
                                ?: doc.getString("nombre")
                                ?: "Usuario"

                            val email = rawUser?.email?.takeIf { it.isNotBlank() }
                                ?: doc.getString("email")
                                ?: doc.getString("correo")
                                ?: ""

                            val photoUrl = rawUser?.photoUrl?.takeIf { it.isNotBlank() }
                                ?: doc.getString("photoUrl")
                                ?: doc.getString("fotoUrl")

                            val isConn = parseBoolean(doc, "isConnected", "connected")
                                ?: (rawUser?.isConnected == true)

                            if (!isConn) return@mapNotNull null

                            UserLocation(
                                uid = uid,
                                name = name,
                                email = email,
                                photoUrl = photoUrl,
                                latitude = lat,
                                longitude = lng,
                                isConnected = isConn,
                                lastUpdated = rawUser?.lastUpdated ?: doc.getLong("lastUpdated") ?: System.currentTimeMillis()
                            )
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
                "connected" to true,
                "locationHistory" to FieldValue.arrayUnion(point),
                "lastUpdated" to System.currentTimeMillis()
            )
            photoUrl?.let {
                updates["photoUrl"] = it
                updates["fotoUrl"] = it
            }

            firestore.collection("users").document(uid)
                .set(updates, SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setConnectedState(uid: String, isConnected: Boolean): Result<Unit> {
        return try {
            val updates = mapOf<String, Any>(
                "isConnected" to isConnected,
                "connected" to isConnected,
                "lastUpdated" to System.currentTimeMillis()
            )
            firestore.collection("users").document(uid)
                .set(updates, SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseDouble(doc: com.google.firebase.firestore.DocumentSnapshot, vararg keys: String): Double? {
        for (key in keys) {
            val valObj = doc.get(key)
            if (valObj is Number) return valObj.toDouble()
            if (valObj is String) {
                val d = valObj.toDoubleOrNull()
                if (d != null) return d
            }
        }
        return null
    }

    private fun parseBoolean(doc: com.google.firebase.firestore.DocumentSnapshot, vararg keys: String): Boolean? {
        for (key in keys) {
            val valObj = doc.get(key)
            if (valObj is Boolean) return valObj
            if (valObj is String) return valObj.toBooleanStrictOrNull()
        }
        return null
    }
}
