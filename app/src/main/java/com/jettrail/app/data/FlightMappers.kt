package com.jettrail.app.data

import com.jettrail.app.domain.*

fun ProcessedFlightSample.toEntity(flightId: Long): FlightSampleEntity = FlightSampleEntity(
    flightId = flightId,
    timestampMillis = raw.timestampMillis,
    latitudeDeg = raw.latitudeDeg,
    longitudeDeg = raw.longitudeDeg,
    groundSpeedMps = raw.groundSpeedMps,
    bearingDeg = raw.bearingDeg,
    gpsAltitudeM = raw.gpsAltitudeM,
    horizontalAccuracyM = raw.horizontalAccuracyM,
    verticalAccuracyM = raw.verticalAccuracyM,
    satelliteCount = raw.satelliteCount,
    pressureHpa = raw.pressureHpa,
    estimatedCabinAltitudeM = raw.estimatedCabinAltitudeM,
    linearAccelerationRmsMps2 = raw.linearAccelerationRmsMps2,
    locationElapsedRealtimeNanos = raw.locationElapsedRealtimeNanos,
    isNewLocationFix = raw.isNewLocationFix,
    locationProvenance = raw.locationProvenance.name,
    pressureProvenance = raw.pressureProvenance.name,
    isSimulated = raw.isSimulated,
    acceptedForStatistics = acceptedForStatistics,
    rejectionReasonsCsv = rejectionReasons.joinToString(",") { it.name },
    distanceFromPreviousM = distanceFromPreviousM,
    verticalSpeedMps = verticalSpeedMps,
    phase = phase.name,
    turbulence = turbulence.name,
)

fun FlightSampleEntity.toDomain(): ProcessedFlightSample = ProcessedFlightSample(
    raw = RawFlightSample(
        timestampMillis = timestampMillis,
        latitudeDeg = latitudeDeg,
        longitudeDeg = longitudeDeg,
        groundSpeedMps = groundSpeedMps,
        bearingDeg = bearingDeg,
        gpsAltitudeM = gpsAltitudeM,
        horizontalAccuracyM = horizontalAccuracyM,
        verticalAccuracyM = verticalAccuracyM,
        satelliteCount = satelliteCount,
        pressureHpa = pressureHpa,
        estimatedCabinAltitudeM = estimatedCabinAltitudeM,
        linearAccelerationRmsMps2 = linearAccelerationRmsMps2,
        locationProvenance = enumOr(locationProvenance, ValueProvenance.UNAVAILABLE),
        pressureProvenance = enumOr(pressureProvenance, ValueProvenance.UNAVAILABLE),
        isSimulated = isSimulated,
        isNewLocationFix = isNewLocationFix,
        locationElapsedRealtimeNanos = locationElapsedRealtimeNanos,
    ),
    acceptedForStatistics = acceptedForStatistics,
    rejectionReasons = rejectionReasonsCsv.split(',').filter(String::isNotBlank).mapNotNull { enumOrNull<SampleRejection>(it) }.toSet(),
    distanceFromPreviousM = distanceFromPreviousM,
    verticalSpeedMps = verticalSpeedMps,
    phase = enumOr(phase, FlightPhase.UNKNOWN),
    turbulence = enumOr(turbulence, TurbulenceEstimate.UNAVAILABLE),
)

private inline fun <reified T : Enum<T>> enumOr(value: String, fallback: T): T = enumOrNull<T>(value) ?: fallback
private inline fun <reified T : Enum<T>> enumOrNull(value: String): T? = enumValues<T>().firstOrNull { it.name == value }
