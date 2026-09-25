package com.jettrail.app.recording

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

enum class WarningSeverity { INFO, WARNING, BLOCKING }

data class RecordingWarning(
    val code: String,
    val message: String,
    val severity: WarningSeverity,
)

object RecordingEnvironment {
    fun warnings(context: Context): List<RecordingWarning> = buildList {
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted) add(
            RecordingWarning(
                "fine_location_permission",
                "Precise location permission is required for flight recording.",
                WarningSeverity.BLOCKING,
            ),
        )

        val locationManager = context.getSystemService(LocationManager::class.java)
        if (!locationManager.isLocationEnabled) add(
            RecordingWarning(
                "location_disabled",
                "Location is off. Turn it on, including after enabling airplane mode.",
                WarningSeverity.BLOCKING,
            ),
        )

        val airplaneMode = Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.AIRPLANE_MODE_ON,
            0,
        ) != 0
        if (airplaneMode && locationManager.isLocationEnabled) add(
            RecordingWarning(
                "airplane_mode_location_reminder",
                "Airplane mode is on. Leave Location enabled for offline GNSS recording.",
                WarningSeverity.INFO,
            ),
        )

        val power = context.getSystemService(PowerManager::class.java)
        if (power.isPowerSaveMode) add(
            RecordingWarning(
                "battery_saver",
                "Battery saver may reduce GNSS update frequency while the screen is locked.",
                WarningSeverity.WARNING,
            ),
        )
        if (Build.VERSION.SDK_INT >= 28 &&
            power.locationPowerSaveMode != PowerManager.LOCATION_MODE_NO_CHANGE
        ) add(
            RecordingWarning(
                "location_power_save",
                "The current location power mode may pause or reduce updates in the background.",
                WarningSeverity.WARNING,
            ),
        )
        if (!power.isIgnoringBatteryOptimizations(context.packageName)) add(
            RecordingWarning(
                "battery_optimization",
                "Android battery optimization is active; keep the persistent recording notification enabled.",
                WarningSeverity.INFO,
            ),
        )
    }

    fun canStart(context: Context): Boolean = warnings(context).none {
        it.severity == WarningSeverity.BLOCKING
    }
}
