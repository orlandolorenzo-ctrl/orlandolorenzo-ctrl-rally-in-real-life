package com.rallyinreallife.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.gms.location.LocationServices
import com.rallyinreallife.app.nav.DirectionsService
import com.rallyinreallife.app.nav.NavigationTracker
import com.rallyinreallife.app.nav.RallyNavState
import com.rallyinreallife.app.nav.RallySession
import com.rallyinreallife.app.pace.PaceNoteEngine
import com.rallyinreallife.app.tts.CoDriverTts
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private lateinit var noteView: TextView
    private lateinit var nextNotesView: TextView
    private lateinit var speedView: TextView
    private lateinit var distanceView: TextView
    private lateinit var destinationInput: EditText
    private lateinit var startButton: Button
    private lateinit var stopButton: Button

    private val directions = DirectionsService()
    private lateinit var tracker: NavigationTracker
    private lateinit var coDriver: CoDriverTts
    private var trackingJob: Job? = null
    private var lastSpokenIndex: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tracker = NavigationTracker(this)
        coDriver = CoDriverTts(this)

        noteView = findViewById(R.id.currentNote)
        nextNotesView = findViewById(R.id.nextNotes)
        speedView = findViewById(R.id.speed)
        distanceView = findViewById(R.id.distanceToManeuver)
        destinationInput = findViewById(R.id.destinationInput)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)

        (supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment).getMapAsync(this)

        startButton.setOnClickListener { onStartRally() }
        stopButton.setOnClickListener { onStopRally() }

        if (!hasLocationPermission()) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1001
            )
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        map.uiSettings.isZoomControlsEnabled = true
        if (hasLocationPermission()) {
            try {
                map.isMyLocationEnabled = true
            } catch (_: SecurityException) {
            }
        }
    }

    private fun onStartRally() {
        val destination = destinationInput.text.toString().trim()
        if (destination.isEmpty()) {
            Toast.makeText(this, R.string.destination_hint, Toast.LENGTH_SHORT).show()
            return
        }
        if (!hasLocationPermission()) {
            Toast.makeText(this, R.string.waiting_gps, Toast.LENGTH_SHORT).show()
            return
        }

        startButton.isEnabled = false
        val fused = LocationServices.getFusedLocationProviderClient(this)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            startButton.isEnabled = true
            Toast.makeText(this, R.string.waiting_gps, Toast.LENGTH_SHORT).show()
            return
        }
        fused.lastLocation.addOnSuccessListener { location ->
            if (location == null) {
                startButton.isEnabled = true
                Toast.makeText(this, R.string.waiting_gps, Toast.LENGTH_SHORT).show()
                return@addOnSuccessListener
            }
            val origin = "${location.latitude},${location.longitude}"
            lifecycleScope.launch { fetchAndStart(origin, destination) }
        }.addOnFailureListener {
            startButton.isEnabled = true
            Toast.makeText(this, R.string.waiting_gps, Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun fetchAndStart(origin: String, destination: String) {
        directions.fetchRoute(origin, destination)
            .onSuccess { route ->
                val notes = PaceNoteEngine.generate(route.steps)
                tracker.attachRoute(route)
                RallySession.startWithRoute(route, notes)
                drawRoute(route.overviewPolyline.map { LatLng(it.lat, it.lng) })
                lastSpokenIndex = -1
                observeNavigation()
                startButton.isEnabled = true
                stopButton.isEnabled = true
            }
            .onFailure { error ->
                startButton.isEnabled = true
                Toast.makeText(this@MainActivity, error.message, Toast.LENGTH_LONG).show()
            }
    }

    private fun observeNavigation() {
        trackingJob?.cancel()
        trackingJob = lifecycleScope.launch {
            tracker.locationUpdates().collect { state ->
                RallySession.updateState(state)
                render(state)
            }
        }
    }

    private fun render(state: RallyNavState) {
        speedView.text = "${state.speedKmh.toInt()} km/h"
        distanceView.text = if (state.finished) {
            getString(R.string.stop_navigation)
        } else {
            PaceNoteEngine.formatDistance(state.metersToNextManeuver)
        }

        val note = state.currentNote
        noteView.text = note?.displayText ?: "PRONTI, PARTENZA\u2026"
        noteView.setTextColor(
            when {
                note == null -> Color.WHITE
                note.modifiers.any { it == "attenzione" || it == "tornante" } ->
                    getColor(R.color.caution_red)
                else -> getColor(R.color.note_green)
            }
        )

        nextNotesView.text = state.nextNotes.joinToString("\n") { it.displayText }

        if (RallySession.coDriverEnabled && note != null && note.index != lastSpokenIndex) {
            lastSpokenIndex = note.index
            coDriver.speak(note.spokenText)
        }
    }

    private fun drawRoute(points: List<LatLng>) {
        if (!::map.isInitialized || points.isEmpty()) return
        map.clear()
        map.addPolyline(
            PolylineOptions()
                .addAll(points)
                .width(12f)
                .color(getColor(R.color.rally_gold))
        )
        val bounds = LatLngBounds.Builder().apply {
            points.forEach { include(it) }
        }.build()
        map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 64))
    }

    private fun onStopRally() {
        trackingJob?.cancel()
        coDriver.shutdown()
        coDriver = CoDriverTts(this)
        RallySession.reset()
        map.clear()
        noteView.text = getString(R.string.start_navigation)
        nextNotesView.text = ""
        stopButton.isEnabled = false
        startButton.isEnabled = true
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    override fun onDestroy() {
        trackingJob?.cancel()
        coDriver.shutdown()
        super.onDestroy()
    }
}
