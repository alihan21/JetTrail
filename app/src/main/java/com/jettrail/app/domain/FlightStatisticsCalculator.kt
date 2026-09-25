package com.jettrail.app.domain

object FlightStatisticsCalculator {
    fun calculate(samples: List<ProcessedFlightSample>): FlightStatistics {
        if (samples.isEmpty()) return FlightStatistics(0, 0, 0, 0.0, null, null, null, null, 0.0, null, emptyMap())
        val accepted = samples.filter { it.acceptedForStatistics }
        val speeds = accepted.mapNotNull { it.raw.groundSpeedMps?.takeIf(Double::isFinite) }
        val altitudes = accepted.mapNotNull { it.raw.gpsAltitudeM?.takeIf(Double::isFinite) }
        val accuracies = accepted.mapNotNull { it.raw.horizontalAccuracyM?.takeIf(Double::isFinite) }
        val fixes = samples.count { GeoMath.isCoordinateValid(it.raw.latitudeDeg, it.raw.longitudeDeg) }
        val sorted = samples.sortedBy { it.raw.timestampMillis }
        val phaseDurations = mutableMapOf<FlightPhase, Long>()
        sorted.zipWithNext().forEach { (a, b) ->
            val dt = (b.raw.timestampMillis - a.raw.timestampMillis).coerceAtLeast(0)
            phaseDurations[a.phase] = (phaseDurations[a.phase] ?: 0L) + dt
        }
        return FlightStatistics(
            acceptedSamples = accepted.size,
            rejectedSamples = samples.size - accepted.size,
            durationMillis = (sorted.last().raw.timestampMillis - sorted.first().raw.timestampMillis).coerceAtLeast(0),
            distanceM = accepted.sumOf { it.distanceFromPreviousM ?: 0.0 },
            averageGroundSpeedMps = speeds.averageOrNull(),
            maximumGroundSpeedMps = speeds.maxOrNull(),
            averageGpsAltitudeM = altitudes.averageOrNull(),
            maximumGpsAltitudeM = altitudes.maxOrNull(),
            gpsCoverageFraction = fixes.toDouble() / samples.size,
            averageHorizontalAccuracyM = accuracies.averageOrNull(),
            phaseDurationsMillis = phaseDurations,
        )
    }

    private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()
}
