package com.jettrail.app.domain

data class CompletedFlightSummary(
    val originIdent: String?, val destinationIdent: String?, val originCountry: String?, val destinationCountry: String?,
    val distanceM: Double, val durationMillis: Long, val maximumGroundSpeedMps: Double?, val maximumGpsAltitudeM: Double?,
    val gpsCoverageFraction: Double,
)

data class ExplorerProfile(
    val xp: Int,
    val level: Int,
    val uniqueAirports: Set<String>,
    val uniqueCountries: Set<String>,
    val uniqueRoutes: Set<String>,
    val badges: Set<String>,
    val longestDistanceM: Double,
    val longestDurationMillis: Long,
    val highestGpsAltitudeM: Double,
    val fastestGroundSpeedMps: Double,
)

object GamificationEngine {
    fun build(flights: Iterable<CompletedFlightSummary>): ExplorerProfile {
        val list = flights.toList()
        val airports = list.flatMap { listOfNotNull(it.originIdent, it.destinationIdent) }.toSet()
        val countries = list.flatMap { listOfNotNull(it.originCountry, it.destinationCountry) }.toSet()
        val routes = list.mapNotNull { f -> f.originIdent?.let { o -> f.destinationIdent?.let { d -> listOf(o, d).sorted().joinToString("-") } } }.toSet()
        val xp = list.sumOf { flight ->
            100 + (flight.distanceM / 100_000.0).toInt().coerceAtMost(100) + if (flight.gpsCoverageFraction >= .8) 25 else 0
        }
        val badges = buildSet {
            if (list.isNotEmpty()) add("FIRST_FLIGHT")
            if (countries.size >= 5) add("COUNTRY_HOPPER")
            if (airports.size >= 10) add("AIRPORT_COLLECTOR")
            if (list.any { it.distanceM >= 5_000_000 }) add("LONG_HAUL")
            if (list.any { it.gpsCoverageFraction >= .95 }) add("CLEAR_SKIES")
        }
        return ExplorerProfile(
            xp, 1 + xp / 500, airports, countries, routes, badges,
            list.maxOfOrNull { it.distanceM } ?: 0.0,
            list.maxOfOrNull { it.durationMillis } ?: 0,
            list.mapNotNull { it.maximumGpsAltitudeM }.maxOrNull() ?: 0.0,
            list.mapNotNull { it.maximumGroundSpeedMps }.maxOrNull() ?: 0.0,
        )
    }
}
