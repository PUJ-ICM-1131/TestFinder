package com.example.proyecto.ui.components

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.net.HttpURLConnection
import java.net.URL

data class RouteResult(
    val points: List<GeoPoint>,
    val distanceMeters: Double,
    val durationSeconds: Double
)

suspend fun fetchMapboxRoute(
    originLat: Double?,
    originLng: Double?,
    destLat: Double,
    destLng: Double
): RouteResult? = withContext(Dispatchers.IO) {

    val token = MAPBOX_ACCESS_TOKEN

    val urlString =
        "https://api.mapbox.com/directions/v5/mapbox/driving/" +
                "$originLng,$originLat;$destLng,$destLat" +
                "?alternatives=false&overview=full&geometries=geojson" +
                "&access_token=$token"

    var connection: HttpURLConnection? = null

    try {
        connection = URL(urlString)
            .openConnection() as HttpURLConnection

        connection.requestMethod = "GET"
        connection.connectTimeout = 15000
        connection.readTimeout = 15000

        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            return@withContext null
        }

        val response = connection.inputStream
            .bufferedReader()
            .use { it.readText() }

        val json = JSONObject(response)

        if (json.optString("code") != "Ok") {
            return@withContext null
        }

        val routes = json.getJSONArray("routes")

        if (routes.length() == 0) {
            return@withContext null
        }

        val route = routes.getJSONObject(0)
        val coordinates = route
            .getJSONObject("geometry")
            .getJSONArray("coordinates")

        val points = mutableListOf<GeoPoint>()

        for (i in 0 until coordinates.length()) {
            val coordinate = coordinates.getJSONArray(i)

            val longitude = coordinate.getDouble(0)
            val latitude = coordinate.getDouble(1)

            points.add(GeoPoint(latitude, longitude))
        }

        RouteResult(
            points = points,
            distanceMeters = route.getDouble("distance"),
            durationSeconds = route.getDouble("duration")
        )
    } catch (_: Exception) {
        null
    } finally {
        connection?.disconnect()
    }
}

suspend fun fetchStreetRoute(
    origin: com.google.android.gms.maps.model.LatLng,
    dest: com.google.android.gms.maps.model.LatLng
): List<com.google.android.gms.maps.model.LatLng> = withContext(Dispatchers.IO) {
    val mapboxResult = fetchMapboxRoute(
        originLat = origin.latitude,
        originLng = origin.longitude,
        destLat = dest.latitude,
        destLng = dest.longitude
    )
    if (mapboxResult != null && mapboxResult.points.isNotEmpty()) {
        return@withContext mapboxResult.points.map {
            com.google.android.gms.maps.model.LatLng(it.latitude, it.longitude)
        }
    }

    try {
        val urlString = "https://router.project-osrm.org/route/v1/driving/" +
                "${origin.longitude},${origin.latitude};${dest.longitude},${dest.latitude}" +
                "?overview=full&geometries=geojson"
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 10000
        }
        if (connection.responseCode == HttpURLConnection.HTTP_OK) {
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            if (json.optString("code") == "Ok") {
                val routes = json.getJSONArray("routes")
                if (routes.length() > 0) {
                    val route = routes.getJSONObject(0)
                    val coordinates = route.getJSONObject("geometry").getJSONArray("coordinates")
                    val points = mutableListOf<com.google.android.gms.maps.model.LatLng>()
                    for (i in 0 until coordinates.length()) {
                        val coord = coordinates.getJSONArray(i)
                        points.add(com.google.android.gms.maps.model.LatLng(coord.getDouble(1), coord.getDouble(0)))
                    }
                    if (points.isNotEmpty()) {
                        return@withContext points
                    }
                }
            }
        }
    } catch (_: Exception) {
    }

    listOf(origin, dest)
}