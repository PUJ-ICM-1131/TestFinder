package com.example.proyecto.data.model

import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class UserLocation(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val photoUrl: String? = DEFAULT_PROFILE_PICTURE,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val isConnected: Boolean = false,
    val locationHistory: List<Map<String, Double>> = emptyList(),
    val lastUpdated: Long = System.currentTimeMillis()
) {
    val effectivePhotoUrl: String
        get() = if (!photoUrl.isNullOrBlank()) photoUrl else DEFAULT_PROFILE_PICTURE

    fun toLatLngList(): List<LatLng> {
        return locationHistory.mapNotNull { pointMap ->
            val lat = pointMap["lat"]
            val lng = pointMap["lng"]
            if (lat != null && lng != null) {
                LatLng(lat, lng)
            } else null
        }
    }
}
