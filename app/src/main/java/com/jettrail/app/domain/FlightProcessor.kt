package com.jettrail.app.domain

import kotlin.math.abs
import kotlin.math.sqrt

/** Stateful, deterministic processing shared by live GNSS recording and stored-flight reprocessing. */
class FlightProcessor(
    private val maxUsableAccuracyM: Double = 250.0,
    private val maxAircraftSpeedMps: Double = 380.0,
    private val maxVerticalSpeedMps: Double = 100.0,
) {
    private data class AltitudePoint(val timeSeconds: Double, val altitudeM: Double)

    private var previousAccepted: RawFlightSample? = null
    private var pendingReacquisition: RawFlightSample? = null
    private val altitudeWindow = ArrayDeque<AltitudePoint>()

    fun reset() {
        previousAccepted = null
        pendingReacquisition = null
        altitudeWindow.clear()
    }

    fun process(raw: RawFlightSample): ProcessedFlightSample {
        val reasons = linkedSetOf<SampleRejection>()
        val hasPosition = raw.latitudeDeg != null || raw.longitudeDeg != null
        if (hasPosition && !GeoMath.isCoordinateValid(raw.latitudeDeg, raw.longitudeDeg)) {
            reasons += SampleRejection.INVALID_COORDINATE
        }
        if (GeoMath.isCoordinateValid(raw.latitudeDeg, raw.longitudeDeg) &&
            !isInsideSupportedEurope(raw.latitudeDeg!!, raw.longitudeDeg!!)
        ) {
            reasons += SampleRejection.OUTSIDE_SUPPORTED_REGION
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
        var startsNewSegment = false
        val validNewPosition = raw.isNewLocationFix &&
            GeoMath.isCoordinateValid(raw.latitudeDeg, raw.longitudeDeg) &&
            reasons.isEmpty()
        val previous = previousAccepted
        if (validNewPosition && previous == null) {
            startsNewSegment = true
        } else if (validNewPosition && previous != null) {
            val dt = elapsedSeconds(previous, raw)
            when {
                dt <= 0.0 -> reasons += SampleRejection.INVALID_TIME
                dt > MAX_CONTIGUOUS_GAP_SECONDS -> {
                    val pending = pendingReacquisition
                    if (pending == null) {
                        // A lone fix after a long outage is not trustworthy enough to bend the route.
                        pendingReacquisition = raw
                        reasons += SampleRejection.UNCONFIRMED_REACQUISITION
                    } else {
                        val confirmationSeconds = elapsedSeconds(pending, raw)
                        val confirmationDistance = GeoMath.distanceM(
                            pending.latitudeDeg!!,
                            pending.longitudeDeg!!,
                            raw.latitudeDeg!!,
                            raw.longitudeDeg!!,
                        )
                        val confirmed = confirmationSeconds in MIN_CONFIRMATION_SECONDS..MAX_CONTIGUOUS_GAP_SECONDS &&
                            confirmationDistance / confirmationSeconds <= maxAircraftSpeedMps * POSITION_SPEED_TOLERANCE &&
                            pending.horizontalAccuracyM != null && raw.horizontalAccuracyM != null
                        if (confirmed) {
                            // Keep an approximate straight-line distance across the outage, but split
                            // the visible route so it is never presented as a measured track.
                            distance = GeoMath.distanceM(
                                previous.latitudeDeg!!,
                                previous.longitudeDeg!!,
                                raw.latitudeDeg!!,
                                raw.longitudeDeg!!,
                            )
                            startsNewSegment = true
                            // Reacquisition begins a new measured segment. Do not derive vertical
                            // speed from two fixes immediately after an outage.
                            verticalSpeed = null
                            pendingReacquisition = null
                        } else {
                            pendingReacquisition = raw
                            reasons += SampleRejection.UNCONFIRMED_REACQUISITION
                        }
                    }
                }
                else -> {
                    pendingReacquisition = null
                    distance = GeoMath.distanceM(
                        previous.latitudeDeg!!,
                        previous.longitudeDeg!!,
                        raw.latitudeDeg!!,
                        raw.longitudeDeg!!,
                    )
                    if (distance / dt > maxAircraftSpeedMps * POSITION_SPEED_TOLERANCE) {
                        reasons += SampleRejection.IMPOSSIBLE_POSITION_JUMP
                    }
                    verticalSpeed = smoothedVerticalSpeed(raw)
                }
            }
        }
        if (verticalSpeed != null && abs(verticalSpeed) > maxVerticalSpeedMps) {
            reasons += SampleRejection.IMPOSSIBLE_ALTITUDE
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
            startsNewSegment = startsNewSegment && accepted,
            phase = phase,
            turbulence = TurbulenceClassifier.classify(raw.linearAccelerationRmsMps2),
        )
        if (accepted && raw.isNewLocationFix && GeoMath.isCoordinateValid(raw.latitudeDeg, raw.longitudeDeg)) {
            previousAccepted = raw
            if (startsNewSegment) altitudeWindow.clear()
            addAltitudePoint(raw)
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

    /** Least-squares altitude trend over a short window, resistant to normal GNSS altitude jitter. */
    private fun smoothedVerticalSpeed(current: RawFlightSample): Double? {
        val altitude = current.gpsAltitudeM?.takeIf(Double::isFinite) ?: return null
        val time = measurementTimeSeconds(current)
        val points = altitudeWindow
            .filter { it.timeSeconds >= time - VERTICAL_SPEED_WINDOW_SECONDS && it.timeSeconds < time }
            .plus(AltitudePoint(time, altitude))
        if (points.size < 2 || time - points.first().timeSeconds < MIN_VERTICAL_SPEED_SPAN_SECONDS) return null

        val meanTime = points.sumOf { it.timeSeconds } / points.size
        val meanAltitude = points.sumOf { it.altitudeM } / points.size
        val denominator = points.sumOf { (it.timeSeconds - meanTime) * (it.timeSeconds - meanTime) }
        if (denominator <= 0.0) return null
        return points.sumOf { (it.timeSeconds - meanTime) * (it.altitudeM - meanAltitude) } / denominator
    }

    private fun addAltitudePoint(sample: RawFlightSample) {
        val altitude = sample.gpsAltitudeM?.takeIf(Double::isFinite) ?: return
        val time = measurementTimeSeconds(sample)
        while (altitudeWindow.isNotEmpty() && altitudeWindow.first().timeSeconds < time - VERTICAL_SPEED_WINDOW_SECONDS) {
            altitudeWindow.removeFirst()
        }
        altitudeWindow.addLast(AltitudePoint(time, altitude))
    }

    private fun measurementTimeSeconds(sample: RawFlightSample): Double =
        sample.locationElapsedRealtimeNanos?.div(1_000_000_000.0) ?: sample.timestampMillis / 1000.0

    private fun isInsideSupportedEurope(latitude: Double, longitude: Double): Boolean =
        latitude in EUROPE_MIN_LAT..EUROPE_MAX_LAT && longitude in EUROPE_MIN_LON..EUROPE_MAX_LON

    fun processAll(samples: Iterable<RawFlightSample>): List<ProcessedFlightSample> = samples.map(::process)

    companion object {
        private const val POSITION_SPEED_TOLERANCE = 1.15
        private const val MAX_CONTIGUOUS_GAP_SECONDS = 15.0
        private const val MIN_CONFIRMATION_SECONDS = 0.2
        private const val VERTICAL_SPEED_WINDOW_SECONDS = 20.0
        private const val MIN_VERTICAL_SPEED_SPAN_SECONDS = 5.0
        private const val EUROPE_MIN_LAT = 25.0
        private const val EUROPE_MAX_LAT = 75.0
        private const val EUROPE_MIN_LON = -30.0
        private const val EUROPE_MAX_LON = 60.0
    }
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
