package com.jettrail.app.recording

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

sealed interface RecordingStartResult {
    data object Started : RecordingStartResult
    data class Blocked(val warnings: List<RecordingWarning>) : RecordingStartResult
    data class Failed(val message: String) : RecordingStartResult
}

/** Entry point for the Start Flight button in a visible Activity. */
object RecordingController {
    fun startFromVisibleActivity(activity: Activity, flightId: Long): RecordingStartResult {
        require(flightId > 0L) { "flightId must identify a persisted flight" }
        val warnings = RecordingEnvironment.warnings(activity)
        if (warnings.any { it.severity == WarningSeverity.BLOCKING }) {
            return RecordingStartResult.Blocked(warnings)
        }
        if (activity.isFinishing || activity.isDestroyed || !activity.window.decorView.isShown) {
            return RecordingStartResult.Failed("Start Flight must be tapped while JetTrail is visible.")
        }

        val intent = FlightRecordingService.startIntent(activity, flightId)
        return try {
            RecordingRuntime.setState(RecordingState.Starting(flightId))
            ContextCompat.startForegroundService(activity, intent)
            RecordingStartResult.Started
        } catch (error: Exception) {
            val deniedByAndroid = Build.VERSION.SDK_INT >= 31 &&
                error is android.app.ForegroundServiceStartNotAllowedException
            val message = if (deniedByAndroid) {
                "Android blocked background service startup. Return to JetTrail and tap Start Flight again."
            } else {
                error.message ?: "Unable to start flight recording."
            }
            RecordingRuntime.setState(RecordingState.Error(message))
            RecordingStartResult.Failed(message)
        }
    }

    /** Reconnect an unfinished Room flight after unlock/process recreation. */
    fun resumeFromVisibleActivity(activity: Activity, flightId: Long): RecordingStartResult =
        startFromVisibleActivity(activity, flightId)

    fun serviceHeartbeatIsFresh(context: Context, flightId: Long): Boolean {
        val preferences = context.getSharedPreferences(
            FlightRecordingService.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )
        val persistedFlightId = preferences.getLong(
            FlightRecordingService.PREF_ACTIVE_FLIGHT_ID,
            -1L,
        )
        val heartbeat = preferences.getLong(
            FlightRecordingService.PREF_LAST_HEARTBEAT_MILLIS,
            0L,
        )
        return persistedFlightId == flightId &&
            System.currentTimeMillis() - heartbeat in 0..HEARTBEAT_STALE_AFTER_MILLIS
    }

    /** Ends the currently running service. Safe to call from the visible UI. */
    fun end(context: Context) {
        context.startService(FlightRecordingService.endIntent(context))
    }

    fun notificationPermissionGranted(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

    private const val HEARTBEAT_STALE_AFTER_MILLIS = 5_000L
}
