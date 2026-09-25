package com.jettrail.app.domain

object AirportInferenceEngine {
    fun nearest(latitudeDeg: Double?, longitudeDeg: Double?, airports: Iterable<Airport>, maxDistanceM: Double = 50_000.0): AirportInference {
        if (!GeoMath.isCoordinateValid(latitudeDeg, longitudeDeg)) return AirportInference(null, null)
        val nearest = airports.asSequence()
            .map { it to GeoMath.distanceM(latitudeDeg!!, longitudeDeg!!, it.latitudeDeg, it.longitudeDeg) }
            .minByOrNull { it.second }
        return if (nearest == null || nearest.second > maxDistanceM) AirportInference(null, nearest?.second)
        else AirportInference(nearest.first, nearest.second)
    }

    fun inferRoute(samples: List<ProcessedFlightSample>, airports: Iterable<Airport>): Pair<AirportInference, AirportInference> {
        val fixes = samples.filter { it.acceptedForStatistics && GeoMath.isCoordinateValid(it.raw.latitudeDeg, it.raw.longitudeDeg) }
        return nearest(fixes.firstOrNull()?.raw?.latitudeDeg, fixes.firstOrNull()?.raw?.longitudeDeg, airports) to
            nearest(fixes.lastOrNull()?.raw?.latitudeDeg, fixes.lastOrNull()?.raw?.longitudeDeg, airports)
    }
}
