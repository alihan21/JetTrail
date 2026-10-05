package com.jettrail.app.recording

import com.jettrail.app.data.FlightDao
import com.jettrail.app.data.toEntity
import com.jettrail.app.domain.FlightProcessor
import com.jettrail.app.domain.Airport
import com.jettrail.app.domain.AirportInferenceEngine
import com.jettrail.app.domain.FlightStatisticsCalculator
import com.jettrail.app.data.toDomain
import com.jettrail.app.domain.RawFlightSample
import com.jettrail.app.domain.ValueProvenance

/**
 * Bridge from platform recording into the same processor used for stored-flight reprocessing.
 * Create it with the application database DAO and install it in [RecordingRuntime].
 */
class ProcessingRecordingSink(
    private val flightDao: FlightDao,
    private val airports: List<Airport> = emptyList(),
) : RecordingSink {
    private var processorFlightId: Long? = null
    private var processor = FlightProcessor()

    override suspend fun recordRawSample(sample: RecordingSample) {
        if (processorFlightId != sample.flightId) {
            processorFlightId = sample.flightId
            processor = FlightProcessor()
        }
        val processed = processor.process(sample.toDomainRawSample())
        flightDao.insertSample(processed.toEntity(sample.flightId).copy(
            elapsedRealtimeNanos = sample.elapsedRealtimeNanos,
            locationElapsedRealtimeNanos = sample.locationElapsedRealtimeNanos,
            isNewLocationFix = sample.isNewLocationFix,
            speedAccuracyMps = sample.speedAccuracyMetersPerSecond?.toDouble(),
            bearingAccuracyDeg = sample.bearingAccuracyDegrees?.toDouble(),
            satellitesVisible = sample.satellitesVisible,
            satellitesUsedInFix = sample.satellitesUsedInFix,
            turbulencePeakMps2 = sample.turbulencePeakMetersPerSecondSquared?.toDouble(),
            handheldMotionLikely = sample.handheldMotionLikely,
            locationProvider = sample.locationProvider,
        ))
    }

    override suspend fun finishFlight(flightId: Long, endedAtMillis: Long) {
        val flight = flightDao.getFlight(flightId)
        val samples = flightDao.getSamples(flightId).map { it.toDomain() }
        if (flight != null && samples.isNotEmpty()) {
            val stats = FlightStatisticsCalculator.calculate(samples)
            val (origin, destination) = AirportInferenceEngine.inferRoute(samples, airports)
            flightDao.updateFlight(flight.copy(
                endedAtMillis = endedAtMillis, status = "COMPLETED",
                inferredOriginIdent = origin.airport?.ident,
                inferredDestinationIdent = destination.airport?.ident,
                distanceM = stats.distanceM, durationMillis = stats.durationMillis,
                averageGroundSpeedMps = stats.averageGroundSpeedMps,
                maximumGroundSpeedMps = stats.maximumGroundSpeedMps,
                maximumGpsAltitudeM = stats.maximumGpsAltitudeM,
                gpsCoverageFraction = stats.gpsCoverageFraction,
                rejectedSampleCount = stats.rejectedSamples,
            ))
        } else {
            flightDao.finishRecording(flightId, endedAtMillis)
        }
        if (processorFlightId == flightId) {
            processorFlightId = null
            processor.reset()
        }
    }
}

fun RecordingSample.toDomainRawSample(): RawFlightSample = RawFlightSample(
    timestampMillis = recordedAtMillis,
    latitudeDeg = latitudeDegrees,
    longitudeDeg = longitudeDegrees,
    groundSpeedMps = speedMetersPerSecond?.toDouble(),
    bearingDeg = bearingDegrees?.toDouble(),
    gpsAltitudeM = gpsAltitudeMeters,
    horizontalAccuracyM = horizontalAccuracyMeters?.toDouble(),
    verticalAccuracyM = verticalAccuracyMeters?.toDouble(),
    satelliteCount = satellitesUsedInFix ?: satellitesVisible,
    pressureHpa = pressureHectopascals?.toDouble(),
    estimatedCabinAltitudeM = estimatedCabinAltitudeMeters?.takeIf(Double::isFinite),
    linearAccelerationRmsMps2 = turbulenceRmsMetersPerSecondSquared?.toDouble(),
    locationProvenance = if (latitudeDegrees == null || longitudeDegrees == null) {
        ValueProvenance.UNAVAILABLE
    } else {
        ValueProvenance.MEASURED
    },
    pressureProvenance = if (pressureHectopascals == null) {
        ValueProvenance.UNAVAILABLE
    } else {
        ValueProvenance.MEASURED
    },
    isSimulated = false,
    isNewLocationFix = isNewLocationFix,
    locationElapsedRealtimeNanos = locationElapsedRealtimeNanos,
)
