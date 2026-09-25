package com.jettrail.app.domain

/** Identifies where a value came from so estimates are never presented as measurements. */
enum class ValueProvenance { MEASURED, DERIVED, ESTIMATED, SIMULATED, UNAVAILABLE }

enum class FlightPhase { GROUND, TAKEOFF, CLIMB, CRUISE, DESCENT, LANDING, UNKNOWN }

data class RawFlightSample(
    val timestampMillis: Long,
    val latitudeDeg: Double?,
    val longitudeDeg: Double?,
    val groundSpeedMps: Double?,
    val bearingDeg: Double?,
    val gpsAltitudeM: Double?,
    val horizontalAccuracyM: Double?,
    val verticalAccuracyM: Double? = null,
    val satelliteCount: Int? = null,
    val pressureHpa: Double? = null,
    /** Cabin pressure altitude estimate, never aircraft altitude. */
    val estimatedCabinAltitudeM: Double? = null,
    val linearAccelerationRmsMps2: Double? = null,
    val locationProvenance: ValueProvenance = ValueProvenance.MEASURED,
    val pressureProvenance: ValueProvenance = if (pressureHpa == null) ValueProvenance.UNAVAILABLE else ValueProvenance.MEASURED,
    val isSimulated: Boolean = false,
    /** True only when Android delivered a new GNSS fix, not a cached repeat. */
    val isNewLocationFix: Boolean = true,
    /** Monotonic GNSS measurement time, used when available for rate calculations. */
    val locationElapsedRealtimeNanos: Long? = null,
)

data class ProcessedFlightSample(
    /** The exact input is retained even when it is excluded from visible statistics. */
    val raw: RawFlightSample,
    val acceptedForStatistics: Boolean,
    val rejectionReasons: Set<SampleRejection> = emptySet(),
    val distanceFromPreviousM: Double? = null,
    val verticalSpeedMps: Double? = null,
    val phase: FlightPhase = FlightPhase.UNKNOWN,
    val turbulence: TurbulenceEstimate = TurbulenceEstimate.UNAVAILABLE,
)

enum class SampleRejection {
    INVALID_TIME, INVALID_COORDINATE, POOR_ACCURACY, INVALID_SPEED,
    IMPOSSIBLE_POSITION_JUMP, IMPOSSIBLE_ALTITUDE, INVALID_SENSOR_VALUE,
}

enum class TurbulenceEstimate { UNAVAILABLE, SMOOTH, LIGHT, MODERATE, ROUGH }

data class Airport(
    val ident: String,
    val name: String,
    val latitudeDeg: Double,
    val longitudeDeg: Double,
    val countryCode: String,
    val iataCode: String? = null,
)

data class AirportInference(
    val airport: Airport?,
    val distanceM: Double?,
    val provenance: ValueProvenance = if (airport == null) ValueProvenance.UNAVAILABLE else ValueProvenance.ESTIMATED,
)

data class FlightStatistics(
    val acceptedSamples: Int,
    val rejectedSamples: Int,
    val durationMillis: Long,
    val distanceM: Double,
    val averageGroundSpeedMps: Double?,
    val maximumGroundSpeedMps: Double?,
    val averageGpsAltitudeM: Double?,
    val maximumGpsAltitudeM: Double?,
    val gpsCoverageFraction: Double,
    val averageHorizontalAccuracyM: Double?,
    val phaseDurationsMillis: Map<FlightPhase, Long>,
)
