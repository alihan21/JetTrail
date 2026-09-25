package com.jettrail.app.domain

import kotlin.math.abs

object PhaseDetector {
    fun classify(speedMps: Double?, verticalSpeedMps: Double?, altitudeM: Double?): FlightPhase {
        if (speedMps == null || !speedMps.isFinite()) return FlightPhase.UNKNOWN
        val vertical = verticalSpeedMps ?: 0.0
        val altitude = altitudeM ?: 0.0
        return when {
            speedMps < 12.0 && abs(vertical) < 1.5 -> FlightPhase.GROUND
            speedMps >= 55.0 && vertical > 2.5 && altitude < 2_000.0 -> FlightPhase.TAKEOFF
            vertical > 1.5 -> FlightPhase.CLIMB
            speedMps >= 55.0 && vertical < -2.5 && altitude < 2_000.0 -> FlightPhase.LANDING
            vertical < -1.5 -> FlightPhase.DESCENT
            speedMps >= 80.0 && abs(vertical) <= 1.5 -> FlightPhase.CRUISE
            else -> FlightPhase.UNKNOWN
        }
    }

    /** Suggestions require sustained evidence to avoid prompting on a single noisy fix. */
    fun suggestedTransition(recent: List<ProcessedFlightSample>): FlightPhase? {
        if (recent.size < 4) return null
        val phases = recent.takeLast(4).map { it.phase }
        return phases.firstOrNull()?.takeIf { candidate -> candidate in setOf(FlightPhase.TAKEOFF, FlightPhase.LANDING) && phases.all { it == candidate } }
    }
}
