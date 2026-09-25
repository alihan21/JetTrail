package com.jettrail.app.recording

import android.location.Location
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** A raw, once-per-second platform sample. Null values mean unavailable, not zero. */
data class RecordingSample(
    val flightId: Long,
    val recordedAtMillis: Long,
    val elapsedRealtimeNanos: Long,
    val locationElapsedRealtimeNanos: Long?,
    val isNewLocationFix: Boolean,
    val latitudeDegrees: Double?,
    val longitudeDegrees: Double?,
    val horizontalAccuracyMeters: Float?,
    val speedMetersPerSecond: Float?,
    val speedAccuracyMetersPerSecond: Float?,
    val bearingDegrees: Float?,
    val bearingAccuracyDegrees: Float?,
    val gpsAltitudeMeters: Double?,
    val verticalAccuracyMeters: Float?,
    val satellitesVisible: Int?,
    val satellitesUsedInFix: Int?,
    val pressureHectopascals: Float?,
    val estimatedCabinAltitudeMeters: Double?,
    val turbulenceRmsMetersPerSecondSquared: Float?,
    val turbulencePeakMetersPerSecondSquared: Float?,
    val handheldMotionLikely: Boolean,
    val locationProvider: String?,
)

sealed interface RecordingState {
    data object Idle : RecordingState
    data class Starting(val flightId: Long) : RecordingState
    data class Active(val flightId: Long, val latestSample: RecordingSample? = null) : RecordingState
    data class Error(val message: String) : RecordingState
}

/**
 * App-layer adapter. Install the Room/pipeline implementation before starting a flight.
 * Calls are serialized by [FlightRecordingService].
 */
interface RecordingSink {
    suspend fun recordRawSample(sample: RecordingSample)
    suspend fun finishFlight(flightId: Long, endedAtMillis: Long)
    suspend fun recordingError(flightId: Long?, message: String) = Unit
}

/** Process-local bridge used by the service, UI, and app container. */
object RecordingRuntime {
    private val noOpSink = object : RecordingSink {
        override suspend fun recordRawSample(sample: RecordingSample) = Unit
        override suspend fun finishFlight(flightId: Long, endedAtMillis: Long) = Unit
    }

    @Volatile
    private var installedSink: RecordingSink = noOpSink

    private val _state = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    // Helpful for previews/debug tools. Persistence must be done by the installed sink.
    private val _rawSamples = MutableSharedFlow<RecordingSample>(extraBufferCapacity = 64)
    val rawSamples: SharedFlow<RecordingSample> = _rawSamples.asSharedFlow()

    fun installSink(sink: RecordingSink) {
        installedSink = sink
    }

    internal fun sink(): RecordingSink = installedSink
    internal fun setState(state: RecordingState) { _state.value = state }
    internal fun publish(sample: RecordingSample) { _rawSamples.tryEmit(sample) }
}

internal fun Location.toRecordingSample(
    flightId: Long,
    tickElapsedNanos: Long,
    isNewFix: Boolean,
    satellitesVisible: Int?,
    satellitesUsed: Int?,
    sensorSnapshot: SensorSnapshot,
): RecordingSample {
    return RecordingSample(
        flightId = flightId,
        recordedAtMillis = System.currentTimeMillis(),
        elapsedRealtimeNanos = tickElapsedNanos,
        locationElapsedRealtimeNanos = elapsedRealtimeNanos,
        isNewLocationFix = isNewFix,
        latitudeDegrees = latitude,
        longitudeDegrees = longitude,
        horizontalAccuracyMeters = if (hasAccuracy()) accuracy else null,
        speedMetersPerSecond = if (hasSpeed()) speed else null,
        speedAccuracyMetersPerSecond = if (android.os.Build.VERSION.SDK_INT >= 26 && hasSpeedAccuracy()) speedAccuracyMetersPerSecond else null,
        bearingDegrees = if (hasBearing()) bearing else null,
        bearingAccuracyDegrees = if (android.os.Build.VERSION.SDK_INT >= 26 && hasBearingAccuracy()) bearingAccuracyDegrees else null,
        gpsAltitudeMeters = if (hasAltitude()) altitude else null,
        verticalAccuracyMeters = if (android.os.Build.VERSION.SDK_INT >= 26 && hasVerticalAccuracy()) verticalAccuracyMeters else null,
        satellitesVisible = satellitesVisible,
        satellitesUsedInFix = satellitesUsed,
        pressureHectopascals = sensorSnapshot.pressureHectopascals,
        estimatedCabinAltitudeMeters = sensorSnapshot.estimatedCabinAltitudeMeters,
        turbulenceRmsMetersPerSecondSquared = sensorSnapshot.turbulenceRms,
        turbulencePeakMetersPerSecondSquared = sensorSnapshot.turbulencePeak,
        handheldMotionLikely = sensorSnapshot.handheldMotionLikely,
        locationProvider = provider,
    )
}

internal fun dropoutRecordingSample(
    flightId: Long,
    tickElapsedNanos: Long = SystemClock.elapsedRealtimeNanos(),
    satellitesVisible: Int?,
    satellitesUsed: Int?,
    sensorSnapshot: SensorSnapshot,
): RecordingSample = RecordingSample(
    flightId = flightId,
    recordedAtMillis = System.currentTimeMillis(),
    elapsedRealtimeNanos = tickElapsedNanos,
    locationElapsedRealtimeNanos = null,
    isNewLocationFix = false,
    latitudeDegrees = null,
    longitudeDegrees = null,
    horizontalAccuracyMeters = null,
    speedMetersPerSecond = null,
    speedAccuracyMetersPerSecond = null,
    bearingDegrees = null,
    bearingAccuracyDegrees = null,
    gpsAltitudeMeters = null,
    verticalAccuracyMeters = null,
    satellitesVisible = satellitesVisible,
    satellitesUsedInFix = satellitesUsed,
    pressureHectopascals = sensorSnapshot.pressureHectopascals,
    estimatedCabinAltitudeMeters = sensorSnapshot.estimatedCabinAltitudeMeters,
    turbulenceRmsMetersPerSecondSquared = sensorSnapshot.turbulenceRms,
    turbulencePeakMetersPerSecondSquared = sensorSnapshot.turbulencePeak,
    handheldMotionLikely = sensorSnapshot.handheldMotionLikely,
    locationProvider = null,
)
