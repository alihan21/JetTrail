package com.jettrail.app.recording

import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

internal data class GnssSnapshot(
    val location: Location?,
    val isNewFix: Boolean,
    val satellitesVisible: Int?,
    val satellitesUsed: Int?,
)

/** Thin platform GNSS source; permission and Location-enabled checks happen before start. */
internal class GnssLocationSampler(context: Context) : LocationListener {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(LocationManager::class.java)
    private val lock = Any()
    private var latestLocation: Location? = null
    private var lastEmittedLocationNanos = Long.MIN_VALUE
    private var satellitesVisible: Int? = null
    private var satellitesUsed: Int? = null

    private val gnssCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) = synchronized(lock) {
            satellitesVisible = status.satelliteCount
            var used = 0
            for (index in 0 until status.satelliteCount) {
                if (status.usedInFix(index)) used++
            }
            satellitesUsed = used
        }

        override fun onStopped() = synchronized(lock) {
            satellitesVisible = null
            satellitesUsed = null
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        manager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            UPDATE_INTERVAL_MILLIS,
            0f,
            this,
            Looper.getMainLooper(),
        )
        if (Build.VERSION.SDK_INT >= 30) {
            manager.registerGnssStatusCallback(ContextCompat.getMainExecutor(appContext), gnssCallback)
        } else {
            @Suppress("DEPRECATION")
            manager.registerGnssStatusCallback(gnssCallback, Handler(Looper.getMainLooper()))
        }
    }

    fun stop() {
        manager.removeUpdates(this)
        manager.unregisterGnssStatusCallback(gnssCallback)
    }

    fun snapshot(tickElapsedNanos: Long): GnssSnapshot = synchronized(lock) {
        val location = latestLocation
        val ageNanos = location?.let { tickElapsedNanos - it.elapsedRealtimeNanos }
        val freshLocation = location?.takeIf {
            ageNanos != null && ageNanos >= 0L && ageNanos <= MAX_FIX_AGE_NANOS
        }
        val isNew = freshLocation != null &&
            freshLocation.elapsedRealtimeNanos > lastEmittedLocationNanos
        if (isNew) lastEmittedLocationNanos = freshLocation!!.elapsedRealtimeNanos
        GnssSnapshot(
            location = freshLocation?.let(::Location),
            isNewFix = isNew,
            satellitesVisible = satellitesVisible,
            satellitesUsed = satellitesUsed,
        )
    }

    override fun onLocationChanged(location: Location) = synchronized(lock) {
        latestLocation = Location(location)
    }

    @Deprecated("Deprecated in Android")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    override fun onProviderDisabled(provider: String) = synchronized(lock) {
        latestLocation = null
        satellitesVisible = null
        satellitesUsed = null
    }

    companion object {
        private const val UPDATE_INTERVAL_MILLIS = 1_000L
        private const val MAX_FIX_AGE_NANOS = 3_500_000_000L
    }
}
