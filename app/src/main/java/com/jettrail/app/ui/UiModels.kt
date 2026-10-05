package com.jettrail.app.ui

import androidx.compose.runtime.Immutable

enum class AppSection(val label: String, val shortLabel: String) {
    LIVE("Live", "LIVE"),
    LOGBOOK("Logbook", "LOG"),
    EXPLORER("Explorer", "XP")
}

enum class CabinTheme { DARK, RED_AMBER }

enum class ValueConfidence { MEASURED, ESTIMATED, UNAVAILABLE }

@Immutable
data class TrackPoint(
    val latitude: Double,
    val longitude: Double,
    val startsNewSegment: Boolean = false,
)

@Immutable
data class InstrumentValue(
    val value: String,
    val unit: String,
    val label: String,
    val confidence: ValueConfidence = ValueConfidence.MEASURED,
    val hint: String = "GNSS"
)

@Immutable
data class LiveUiState(
    val isRecording: Boolean = false,
    val flightPhase: String = "Ready on ground",
    val origin: String = "BRU",
    val destination: String = "—",
    val elapsed: String = "00:00:00",
    val distance: String = "0.0 km",
    val instruments: List<InstrumentValue> = listOf(
        InstrumentValue("—", "km/h", "Ground speed", ValueConfidence.UNAVAILABLE),
        InstrumentValue("—", "m", "GPS altitude", ValueConfidence.UNAVAILABLE),
        InstrumentValue("—", "m/min", "Vertical speed", ValueConfidence.UNAVAILABLE),
        InstrumentValue("—", "°", "Track", ValueConfidence.UNAVAILABLE)
    ),
    val route: List<TrackPoint> = emptyList(),
    val accuracyMetres: Int? = null,
    val satellites: Int? = null,
    val coveragePercent: Int = 0,
    val locationEnabled: Boolean = true,
    val permissionReady: Boolean = true,
    val batteryOptimized: Boolean = false,
    val cabinAltitude: String? = null,
    val pressure: String? = null,
    val turbulence: String = "Waiting for motion samples",
    val rawSamples: Int = 0,
    val acceptedSamples: Int = 0,
    val maxSpeed: String = "—",
    val maxAltitude: String = "—",
    val signalStatus: String = "Waiting for GNSS",
    val longestDropout: String = "—",
    val rejectedSamples: Int = 0,
)

@Immutable
data class FlightSummary(
    val id: Long,
    val origin: String,
    val destination: String,
    val date: String,
    val duration: String,
    val distanceKm: Int,
    val maxSpeedKmh: Int,
    val maxGpsAltitudeM: Int,
    val averageSpeedKmh: Int,
    val qualityPercent: Int,
    val phases: String,
    val route: List<TrackPoint>,
    /** Null entries are real gaps and must not be joined by the chart. */
    val speedSeries: List<Float?>,
    val altitudeSeries: List<Float?>,
    val isSimulation: Boolean = false,
)

@Immutable
data class ExplorerUiState(
    val level: Int = 1,
    val title: String = "New Explorer",
    val xp: Int = 0,
    val nextLevelXp: Int = 500,
    val flights: Int = 0,
    val countries: Int = 0,
    val airports: Int = 0,
    val routes: Int = 0,
    val longestDistanceKm: Int = 0,
    val highestGroundSpeedKmh: Int = 0,
    val highestGpsAltitudeM: Int = 0,
    val bestGnssCoveragePercent: Int = 0,
    val badges: List<BadgeUi> = defaultBadges.map { it.copy(unlocked = false) },
)

@Immutable
data class BadgeUi(val name: String, val description: String, val unlocked: Boolean, val glyph: String)

val demoRoute = listOf(
    TrackPoint(50.901, 4.484), TrackPoint(51.1, 3.7), TrackPoint(51.3, 2.4),
    TrackPoint(51.6, 0.2), TrackPoint(51.8, -1.5), TrackPoint(52.0, -3.8),
    TrackPoint(52.5, -6.3), TrackPoint(53.0, -8.5)
)

val demoFlights = listOf(
    FlightSummary(1, "BRU", "DUB", "23 Aug 2026", "01:27:18", 790, 914, 11_420, 682, 94,
        "Taxi 12m • Airborne 1h 08m • Taxi 7m", demoRoute,
        listOf(0f, 42f, 220f, 610f, 835f, 881f, 900f, 862f, 610f, 180f, 20f),
        listOf(56f, 800f, 3600f, 8200f, 10800f, 11420f, 11220f, 7800f, 2400f, 180f)),
    FlightSummary(2, "AMS", "CPH", "16 Aug 2026", "01:18:42", 637, 872, 10_980, 641, 88,
        "Taxi 15m • Airborne 52m • Taxi 11m", demoRoute.reversed(),
        listOf(0f, 100f, 490f, 810f, 872f, 850f, 790f, 410f, 30f),
        listOf(10f, 1800f, 6800f, 10500f, 10980f, 10100f, 5300f, 240f)),
    FlightSummary(3, "BRU", "MAD", "02 Aug 2026", "02:12:05", 1318, 936, 12_060, 715, 97,
        "Taxi 10m • Airborne 1h 52m • Taxi 10m", demoRoute,
        listOf(0f, 180f, 720f, 918f, 936f, 910f, 680f, 210f, 0f),
        listOf(40f, 2800f, 9200f, 12060f, 11700f, 9400f, 3100f, 60f))
)

val defaultBadges = listOf(
    BadgeUi("First lift-off", "Record a complete flight", true, "01"),
    BadgeUi("Night owl", "Complete an evening journey", true, "◐"),
    BadgeUi("Clean signal", "Maintain 90% GNSS coverage", true, "◎"),
    BadgeUi("Five countries", "Visit five unique countries", true, "05"),
    BadgeUi("Long haul", "Record a flight over 5,000 km", false, "∞"),
    BadgeUi("Polar arc", "Cross 66° latitude", false, "N")
)
