package com.rallyinreallife.app.nav

import com.rallyinreallife.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class DirectionsService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun fetchRoute(origin: String, destination: String): Result<Route> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = buildString {
                    append("https://maps.googleapis.com/maps/api/directions/json")
                    append("?origin=").append(URLEncoder.encode(origin, "UTF-8"))
                    append("&destination=").append(URLEncoder.encode(destination, "UTF-8"))
                    append("&mode=driving&units=metric&language=it")
                    append("&key=").append(BuildConfig.DIRECTIONS_API_KEY)
                }
                val body = client.newCall(Request.Builder().url(url).build())
                    .execute().use { response ->
                        require(response.isSuccessful) { "HTTP ${response.code}" }
                        response.body?.string() ?: error("Risposta vuota dal server")
                    }

                val json = JSONObject(body)
                require(json.optString("status") == "OK") {
                    "Directions API: ${json.optString("status")} - ${json.optString("error_message")}"
                }

                val route = json.getJSONArray("routes").getJSONObject(0)
                val legs = route.getJSONArray("legs")
                val steps = mutableListOf<DirectionsStep>()
                var totalDistance = 0.0

                for (l in 0 until legs.length()) {
                    val leg = legs.getJSONObject(l)
                    totalDistance += leg.getJSONObject("distance").getDouble("value")
                    val legSteps = leg.getJSONArray("steps")
                    for (s in 0 until legSteps.length()) {
                        val st = legSteps.getJSONObject(s)
                        val start = st.getJSONObject("start_location")
                        val end = st.getJSONObject("end_location")
                        steps.add(
                            DirectionsStep(
                                maneuver = st.optString("maneuver", "").ifEmpty { null },
                                distanceMeters = st.getJSONObject("distance").getDouble("value"),
                                startLat = start.getDouble("lat"),
                                startLng = start.getDouble("lng"),
                                endLat = end.getDouble("lat"),
                                endLng = end.getDouble("lng"),
                                polyline = decodePolyline(st.getJSONObject("polyline").getString("points"))
                            )
                        )
                    }
                }

                Route(
                    steps = steps,
                    overviewPolyline = decodePolyline(route.getJSONObject("overview_polyline").getString("points")),
                    totalDistanceMeters = totalDistance
                )
            }
        }

    companion object {
        /**
         * Decoder della polyline encodata di Google
         * (algoritmo ufficiale: https://developers.google.com/maps/documentation/utilities/polylinealgorithm)
         */
        fun decodePolyline(encoded: String): List<LatLng> {
            val points = mutableListOf<LatLng>()
            var index = 0
            var lat = 0
            var lng = 0

            while (index < encoded.length) {
                var result = 0
                var shift = 0
                var b: Int
                do {
                    b = encoded[index++].code - 63
                    result = result or ((b and 0x1f) shl shift)
                    shift += 5
                } while (b >= 0x20)
                lat += if (result and 1 != 0) (result shr 1).inv() else (result shr 1)

                result = 0
                shift = 0
                do {
                    b = encoded[index++].code - 63
                    result = result or ((b and 0x1f) shl shift)
                    shift += 5
                } while (b >= 0x20)
                lng += if (result and 1 != 0) (result shr 1).inv() else (result shr 1)

                points.add(LatLng(lat / 1e5, lng / 1e5))
            }
            return points
        }
    }
}
