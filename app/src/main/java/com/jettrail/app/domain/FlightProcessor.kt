package com.jettrail.app.domain

import kotlin.math.abs
import kotlin.math.sqrt

/** Stateful, deterministic processing shared by GNSS recording and Simulation Lab. */
class FlightProcessor(
    private val maxUsableAccuracyM: Double = 250.0,
    private val maxAircraftSpeedMps: Double = 380.0,
    private val maxVerticalSpeedMps: Double = 100.0,
) {
    private var previousAccepted: RawFlightSample? = null

    fun reset() { previousAccepted = null }

    fun process(raw: RawFlightSample): ProcessedFlightSample {
        val reasons = linkedSetOf<SampleRejection>()
        val hasPosition = raw.latitudeDeg != null || raw.longitudeDeg != null
        if (hasPosition && !GeoMath.isCoordinateValid(raw.latitudeDeg, raw.longitudeDeg)) {
            reasons += SampleRejection.INVALID_COORDINATE
        }
        raw.horizontalAccuracyM?.let {
            if (!it.isFinite() || it < 0.0 || it > maxUsableAccuracyM) reasons += SampleRejection.POOR_ACCURACY
        }
        raw.groundSpeedMps?.let {
            if (!it.isFinite() || it < 0.0 || it > maxAircraftSpeedMps) reasons += SampleRejection.INVALID_SPEED
        }
        raw.gpsAltitudeM?.let {
            if (!it.isFinite() || it !in -600.0..25_000.0) reasons += SampleRejection.IMPOSSIBLE_ALTITUDE
        }
        if (raw.pressureHpa != null && (!raw.pressureHpa.isFinite() || raw.pressureHpa !in 100.0..1_100.0)) {
            reasons += SampleRejection.INVALID_SENSOR_VALUE
        }

        var distance: Double? = null
        var verticalSpeed: Double? = null
        previousAccepted?.takeIf { raw.isNewLocationFix }?.let { previous ->
            val dt = elapsedSeconds(previous, raw)
            if (dt <= 0.0) {
                reasons += SampleRejection.INVALID_TIME
            } else {
                if (GeoMath.isCoordinateValid(previous.latitudeDeg, previous.longitudeDeg) &&
                    GeoMath.isCoordinateValid(raw.latitudeDeg, raw.longitudeDeg)
                ) {
                    distance = GeoMath.distanceM(previous.latitudeDeg!!, previous.longitudeDeg!!, raw.latitudeDeg!!, raw.longitudeDeg!!)
                    if (distance!! / dt > maxAircraftSpeedMps * 1.15) reasons += SampleRejection.IMPOSSIBLE_POSITION_JUMP
                }
                if (previous.gpsAltitudeM?.isFinite() == true && raw.gpsAltitudeM?.isFinite() == true) {
                    verticalSpeed = (raw.gpsAltitudeM - previous.gpsAltitudeM) / dt
                    if (abs(verticalSpeed!!) > maxVerticalSpeedMps) reasons += SampleRejection.IMPOSSIBLE_ALTITUDE
                }
            }
        }

        // A GNSS dropout is retained but is not an outlier; it contributes to coverage, not numeric stats.
        val accepted = reasons.isEmpty()
        val phase = PhaseDetector.classify(raw.groundSpeedMps, verticalSpeed, raw.gpsAltitudeM)
        val processed = ProcessedFlightSample(
            raw = raw,
            acceptedForStatistics = accepted,
            rejectionReasons = reasons,
            distanceFromPreviousM = distance?.takeIf { accepted },
            verticalSpeedMps = verticalSpeed?.takeIf { accepted },
            phase = phase,
            turbulence = TurbulenceClassifier.classify(raw.linearAccelerationRmsMps2),
        )
        if (accepted && raw.isNewLocationFix && GeoMath.isCoordinateValid(raw.latitudeDeg, raw.longitudeDeg)) {
            previousAccepted = raw
        }
        return processed
    }

    private fun elapsedSeconds(previous: RawFlightSample, current: RawFlightSample): Double {
        val previousElapsed = previous.locationElapsedRealtimeNanos
        val currentElapsed = current.locationElapsedRealtimeNanos
        return if (previousElapsed != null && currentElapsed != null) {
            (currentElapsed - previousElapsed) / 1_000_000_000.0
        } else {
            (current.timestampMillis - previous.timestampMillis) / 1000.0
        }
    }

    fun processAll(samples: Iterable<RawFlightSample>): List<ProcessedFlightSample> = samples.map(::process)
}

object TurbulenceClassifier {
    /** A deliberately coarse comfort indicator; handheld motion can dominate the signal. */
    fun classify(linearAccelerationRmsMps2: Double?): TurbulenceEstimate = when {
        linearAccelerationRmsMps2 == null || !linearAccelerationRmsMps2.isFinite() || linearAccelerationRmsMps2 < 0 -> TurbulenceEstimate.UNAVAILABLE
        linearAccelerationRmsMps2 < 0.35 -> TurbulenceEstimate.SMOOTH
        linearAccelerationRmsMps2 < 0.85 -> TurbulenceEstimate.LIGHT
        linearAccelerationRmsMps2 < 1.6 -> TurbulenceEstimate.MODERATE
        else -> TurbulenceEstimate.ROUGH
    }

    fun rms(values: Iterable<Double>): Double {
        var sum = 0.0
        var n = 0
        values.forEach { if (it.isFinite()) { sum += it * it; n++ } }
        return if (n == 0) Double.NaN else sqrt(sum / n)
    }
}
