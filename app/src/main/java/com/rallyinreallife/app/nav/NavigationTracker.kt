package com.rallyinreallife.app.nav

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.rallyinreallife.app.pace.PaceNote
import com.rallyinreallife.app.pace.PaceNoteEngine
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class RallyNavState(
    val notes: List<PaceNote> = emptyList(),
    val currentIndex: Int = 0,
    val metersToNextManeuver: Double = 0.0,
    val speedKmh: Double = 0.0,
    val finished: Boolean = false
) {
    val currentNote: PaceNote? get() = notes.getOrNull(currentIndex)
    val nextNotes: List<PaceNote> get() = notes.drop(currentIndex + 1).take(3)
}

class NavigationTracker(private val context: Context) {

    private val fused = LocationServices.getFusedLocationProviderClient(context)

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun locationUpdates(): Flow<RallyNavState> = callbackFlow {
        if (!hasLocationPermission()) {
            close(SecurityException("Permesso posizione non concesso"))
            return@callbackFlow
        }

        var state = RallyNavState()
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L).build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                state = advance(state, location.latitude, location.longitude, location.speed * 3.6)
                trySend(state)
            }
        }

        fused.requestLocationUpdates(request, callback, context.mainLooper)
        awaitClose { fused.removeLocationUpdates(callback) }
    }

    /**
     * Fa avanzare l'indice della nota corrente quando il pilota supera il
     * punto della manovra (raggio di tolleranza 30 m).
     */
    fun advance(state: RallyNavState, lat: Double, lng: Double, speedKmh: Double): RallyNavState {
        if (state.notes.isEmpty() || state.finished) {
            return state.copy(speedKmh = speedKmh)
        }
        var index = state.currentIndex
        var meters = state.metersToNextManeuver

        val note = state.notes.getOrNull(index)
        if (note != null) {
            val step = stepForNote(state, index)
            if (step != null) {
                meters = distanceMeters(lat, lng, step.endLat, step.endLng)
                while (meters < 30.0 && index < state.notes.size - 1) {
                    index++
                    val nextStep = stepForNote(state, index) ?: break
                    meters = distanceMeters(lat, lng, nextStep.endLat, nextStep.endLng)
                }
                if (meters < 30.0 && index >= state.notes.size - 1) {
                    return state.copy(
                        currentIndex = index,
                        metersToNextManeuver = 0.0,
                        speedKmh = speedKmh,
                        finished = true
                    )
                }
            }
        }
        return state.copy(currentIndex = index, metersToNextManeuver = meters, speedKmh = speedKmh)
    }

    private fun stepForNote(state: RallyNavState, noteIndex: Int): DirectionsStep? {
        return state.notes.getOrNull(noteIndex)?.let { note ->
            routeSteps.getOrNull(note.index)
        }
    }

    private var routeSteps: List<DirectionsStep> = emptyList()

    fun attachRoute(route: Route) {
        routeSteps = route.steps
    }

    companion object {
        fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val results = FloatArray(1)
            android.location.Location.distanceBetween(lat1, lon1, lat2, lon2, results)
            return results[0].toDouble()
        }

        fun headingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double =
            PaceNoteEngine.bearingDeg(lat1, lon1, lat2, lon2)

        @Suppress("unused")
        private fun euclidean(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val x = (lon2 - lon1) * cos(Math.toRadians(lat1))
            val y = lat2 - lat1
            return sqrt(x * x + y * y) * 111_320.0
        }

        @Suppress("unused")
        private fun atan2Deg(y: Double, x: Double): Double = Math.toDegrees(atan2(y, x))
    }
}
