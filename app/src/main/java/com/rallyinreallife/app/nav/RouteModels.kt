package com.rallyinreallife.app.nav

data class LatLng(val lat: Double, val lng: Double)

data class DirectionsStep(
    val maneuver: String?,
    val distanceMeters: Double,
    val startLat: Double,
    val startLng: Double,
    val endLat: Double,
    val endLng: Double,
    val polyline: List<LatLng>
)

data class Route(
    val steps: List<DirectionsStep>,
    val overviewPolyline: List<LatLng>,
    val totalDistanceMeters: Double
)
