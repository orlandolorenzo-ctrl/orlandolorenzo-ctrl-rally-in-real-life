package com.rallyinreallife.app.pace

import com.rallyinreallife.app.nav.DirectionsStep
import kotlin.math.abs

/**
 * Motore di note copilota in stile roadbook da rally.
 *
 * Usa il sistema numerico ufficiale 1-6, quello adottato nei libretti delle
 * note ufficiali dei rally (ogni equipaggio lo personalizza; qui usiamo la
 * convenzione standard):
 *   1 = curva strettissima / tornante (quasi fermo)
 *   2 = molto stretta
 *   3 = stretta
 *   4 = media
 *   5 = veloce
 *   6 = molto veloce, quasi in linea
 *
 * Modificatori: "si stringe", "si apre", "lunga", "corta", "doppia",
 * "attenzione", "tornante", "rotonda", "immissione", "traghetto".
 */
object PaceNoteEngine {

    private const val STRAIGHT_THRESHOLD_DEG = 15.0

    fun generate(steps: List<DirectionsStep>): List<PaceNote> {
        val notes = mutableListOf<PaceNote>()
        for (i in steps.indices) {
            val step = steps[i]
            val inBearing = if (i > 0) bearing(steps[i - 1]) else bearing(step)
            val outBearing = bearing(step)
            val turn = signedTurn(inBearing, outBearing)
            notes.add(buildNote(i, step, turn, steps.getOrNull(i + 1)))
        }
        return notes
    }

    private fun buildNote(
        index: Int,
        step: DirectionsStep,
        turnDeg: Double,
        next: DirectionsStep?
    ): PaceNote {
        val maneuver = step.maneuver
        val absTurn = abs(turnDeg)
        val isStraight = absTurn < STRAIGHT_THRESHOLD_DEG && maneuver.isNullOrStraight()

        val severity = if (isStraight) 6 else severityFromAngle(absTurn, maneuver)
        val isLeft = turnDeg < 0 || maneuver?.contains("left") == true

        val modifiers = mutableListOf<String>()

        if (!isStraight) {
            if (absTurn >= 160 || maneuver?.contains("uturn") == true) {
                modifiers.add("tornante")
            }
            if (step.distanceMeters > 400) modifiers.add("lunga")
            if (step.distanceMeters < 60) modifiers.add("corta")

            if (next != null) {
                val nextTurn = signedTurn(bearing(step), bearing(next))
                val nextSeverity = severityFromAngle(abs(nextTurn), next.maneuver)
                val nextIsLeft = nextTurn < 0 || next.maneuver?.contains("left") == true
                when {
                    nextSeverity < severity && nextIsLeft == isLeft -> modifiers.add("si stringe")
                    nextSeverity > severity && nextIsLeft == isLeft -> modifiers.add("si apre")
                    nextSeverity <= severity && nextIsLeft == isLeft -> modifiers.add("doppia")
                }
                if (nextSeverity <= 2 && severity >= 5) modifiers.add("attenzione")
            }
        }

        if (maneuver?.contains("roundabout") == true) modifiers.add("rotonda")
        if (maneuver?.contains("ferry") == true) modifiers.add("traghetto")
        if (maneuver?.contains("ramp") == true || maneuver?.contains("keep") == true) {
            modifiers.add("immissione")
        }

        val dirWord = when {
            isStraight -> "dritto"
            isLeft -> "sinistra"
            else -> "destra"
        }
        val modsText = modifiers.joinToString(" ")
        val spoken = if (isStraight) {
            "Dritto per ${formatDistance(step.distanceMeters)}"
        } else {
            buildString {
                append("$dirWord $severity")
                if (modsText.isNotEmpty()) append(", $modsText")
                append(", per ${formatDistance(step.distanceMeters)}")
            }
        }
        val display = if (isStraight) {
            "DRITTO ${formatDistance(step.distanceMeters)}"
        } else {
            val arrow = if (isLeft) "<" else ">"
            buildString {
                append("$arrow $severity $dirWord".uppercase())
                if (modsText.isNotEmpty()) append(" \u00b7 ${modsText.uppercase()}")
            }
        }

        return PaceNote(
            index = index,
            severity = severity,
            isLeft = isLeft,
            isStraight = isStraight,
            distanceMeters = step.distanceMeters,
            modifiers = modifiers,
            spokenText = spoken,
            displayText = display,
            maneuver = maneuver
        )
    }

    fun severityFromAngle(absTurnDeg: Double, maneuver: String?): Int {
        var severity = when {
            absTurnDeg >= 160 -> 1
            absTurnDeg >= 135 -> 2
            absTurnDeg >= 100 -> 3
            absTurnDeg >= 70 -> 4
            absTurnDeg >= 40 -> 5
            else -> 6
        }
        when {
            maneuver?.startsWith("sharp") == true -> severity = minOf(severity, 2)
            maneuver?.startsWith("slight") == true -> severity = maxOf(severity, 5)
            maneuver?.contains("uturn") == true -> severity = 1
            maneuver?.contains("straight") == true -> severity = 6
        }
        return severity.coerceIn(1, 6)
    }

    private fun String?.isNullOrStraight(): Boolean =
        this == null || contains("straight") || contains("continue") || contains("merge") ||
            contains("keep") || contains("ramp")

    private fun bearing(step: DirectionsStep): Double =
        bearingDeg(step.startLat, step.startLng, step.endLat, step.endLng)

    fun bearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dLon = Math.toRadians(lon2 - lon1)
        val y = Math.sin(dLon) * Math.cos(phi2)
        val x = Math.cos(phi1) * Math.sin(phi2) - Math.sin(phi1) * Math.cos(phi2) * Math.cos(dLon)
        return (Math.toDegrees(Math.atan2(y, x)) + 360.0) % 360.0
    }

    fun signedTurn(inBearing: Double, outBearing: Double): Double {
        var d = outBearing - inBearing
        while (d > 180.0) d -= 360.0
        while (d < -180.0) d += 360.0
        return d
    }

    fun formatDistance(meters: Double): String = when {
        meters >= 1000 -> String.format(java.util.Locale.ITALIAN, "%.1f chilometri", meters / 1000.0)
        else -> "${Math.round(meters / 10.0) * 10} metri"
    }
}
