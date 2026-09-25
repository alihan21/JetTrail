package com.jettrail.app.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.jettrail.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class FlightRecordingService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var locations: GnssLocationSampler
    private lateinit var sensors: SensorSampler
    private lateinit var notificationManager: NotificationManager
    private var tickerJob: Job? = null
    private var activeFlightId: Long? = null
    private var samplingStarted = false
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        locations = GnssLocationSampler(this)
        sensors = SensorSampler(this)
        notificationManager = getSystemService(NotificationManager::class.java)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_END) {
            finishUserRequestedFlight()
            return START_NOT_STICKY
        }

        val requestedId = intent?.takeIf {
            it.action == ACTION_START && it.getBooleanExtra(EXTRA_VISIBLE_USER_ACTION, false)
        }?.getLongExtra(EXTRA_FLIGHT_ID, INVALID_FLIGHT_ID)
            ?.takeIf { it > 0L }
        val restoredId = preferences().getLong(PREF_ACTIVE_FLIGHT_ID, INVALID_FLIGHT_ID)
            .takeIf { it > 0L }
        val flightId = requestedId ?: restoredId
        if (flightId == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val previousFlightId = activeFlightId
        activeFlightId = flightId
        preferences().edit()
            .putLong(PREF_ACTIVE_FLIGHT_ID, flightId)
            .putLong(PREF_LAST_HEARTBEAT_MILLIS, System.currentTimeMillis())
            .apply()
        promoteToForeground(flightId)
        holdCpuForOneSecondTicks()
        if (samplingStarted && previousFlightId != null && previousFlightId != flightId) stopSampling()
        if (!samplingStarted) startSampling(flightId)
        // Ask Android to redeliver the concrete flight id if it has to recreate us.
        return START_REDELIVER_INTENT
    }

    private fun promoteToForeground(flightId: Long) {
        val notification = buildNotification(flightId, null)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startSampling(flightId: Long) {
        try {
            locations.start()
            sensors.start()
            samplingStarted = true
        } catch (error: SecurityException) {
            val message = "Precise location permission was removed; recording stopped."
            RecordingRuntime.setState(RecordingState.Error(message))
            serviceScope.launch { RecordingRuntime.sink().recordingError(flightId, message) }
            stopSampling()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        RecordingRuntime.setState(RecordingState.Active(flightId))
        tickerJob = serviceScope.launch {
            var tickCount = 0L
            while (isActive) {
                val tickStartedNanos = SystemClock.elapsedRealtimeNanos()
                val gnss = locations.snapshot(tickStartedNanos)
                val sensor = sensors.snapshotAndResetMotion()
                val sample = gnss.location?.toRecordingSample(
                    flightId = flightId,
                    tickElapsedNanos = tickStartedNanos,
                    isNewFix = gnss.isNewFix,
                    satellitesVisible = gnss.satellitesVisible,
                    satellitesUsed = gnss.satellitesUsed,
                    sensorSnapshot = sensor,
                ) ?: dropoutRecordingSample(
                    flightId = flightId,
                    tickElapsedNanos = tickStartedNanos,
                    satellitesVisible = gnss.satellitesVisible,
                    satellitesUsed = gnss.satellitesUsed,
                    sensorSnapshot = sensor,
                )

                try {
                    RecordingRuntime.sink().recordRawSample(sample)
                    preferences().edit()
                        .putLong(PREF_LAST_HEARTBEAT_MILLIS, System.currentTimeMillis())
                        .putLong(PREF_LAST_SAMPLE_MILLIS, sample.recordedAtMillis)
                        .apply()
                    RecordingRuntime.publish(sample)
                    RecordingRuntime.setState(RecordingState.Active(flightId, sample))
                } catch (error: Exception) {
                    val message = error.message ?: "Could not persist the latest raw sample."
                    RecordingRuntime.setState(RecordingState.Error(message))
                    runCatching { RecordingRuntime.sink().recordingError(flightId, message) }
                }

                tickCount++
                if (tickCount % NOTIFICATION_REFRESH_TICKS == 0L) {
                    notificationManager.notify(NOTIFICATION_ID, buildNotification(flightId, sample))
                }
                val elapsedMillis = (SystemClock.elapsedRealtimeNanos() - tickStartedNanos) / 1_000_000L
                delay((SAMPLE_INTERVAL_MILLIS - elapsedMillis).coerceAtLeast(0L))
            }
        }
    }

    private fun holdCpuForOneSecondTicks() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:flight-recording")
            .apply {
                setReferenceCounted(false)
                acquire()
            }
    }

    private fun finishUserRequestedFlight() {
        val flightId = activeFlightId
            ?: preferences().getLong(PREF_ACTIVE_FLIGHT_ID, INVALID_FLIGHT_ID).takeIf { it > 0L }
        stopSampling()
        preferences().edit()
            .remove(PREF_ACTIVE_FLIGHT_ID)
            .remove(PREF_LAST_HEARTBEAT_MILLIS)
            .remove(PREF_LAST_SAMPLE_MILLIS)
            .apply()
        serviceScope.launch {
            if (flightId != null) {
                runCatching { RecordingRuntime.sink().finishFlight(flightId, System.currentTimeMillis()) }
            }
            RecordingRuntime.setState(RecordingState.Idle)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun stopSampling() {
        tickerJob?.cancel()
        tickerJob = null
        if (samplingStarted) {
            locations.stop()
            sensors.stop()
            samplingStarted = false
        }
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Flight recording",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Keeps offline flight recording active while the screen is locked"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(flightId: Long, sample: RecordingSample?): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentIntent = launchIntent?.let {
            PendingIntent.getActivity(
                this,
                REQUEST_OPEN,
                it.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val endIntent = PendingIntent.getService(
            this,
            REQUEST_END,
            endIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val quality = when {
            sample == null -> "Starting GNSS…"
            sample.latitudeDegrees == null -> "Waiting for GNSS • raw dropout saved"
            sample.satellitesUsedInFix != null -> "Recording • ${sample.satellitesUsedInFix} satellites used"
            else -> "Recording offline"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_jettrail)
            .setContentTitle("JetTrail flight in progress")
            .setContentText(quality)
            .setSubText("Flight $flightId")
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setWhen(System.currentTimeMillis())
            .setUsesChronometer(true)
            .addAction(0, "End flight", endIntent)
            .apply { if (contentIntent != null) setContentIntent(contentIntent) }
            .build()
    }

    private fun preferences() = getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun onDestroy() {
        stopSampling()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // The recording belongs to the foreground service, not the recent-apps card.
        // Keeping the persisted id lets START_REDELIVER_INTENT or the next visible
        // Activity resume reconnect to the exact same Room flight.
        if (activeFlightId != null) {
            preferences().edit()
                .putLong(PREF_LAST_HEARTBEAT_MILLIS, System.currentTimeMillis())
                .apply()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val ACTION_START = "com.jettrail.app.action.START_RECORDING"
        private const val ACTION_END = "com.jettrail.app.action.END_RECORDING"
        private const val EXTRA_FLIGHT_ID = "flight_id"
        private const val EXTRA_VISIBLE_USER_ACTION = "visible_user_action"
        private const val INVALID_FLIGHT_ID = -1L
        private const val CHANNEL_ID = "flight_recording"
        private const val NOTIFICATION_ID = 4107
        private const val REQUEST_OPEN = 4108
        private const val REQUEST_END = 4109
        private const val SAMPLE_INTERVAL_MILLIS = 1_000L
        private const val NOTIFICATION_REFRESH_TICKS = 10L
        internal const val PREFERENCES_NAME = "recording_service_state"
        internal const val PREF_ACTIVE_FLIGHT_ID = "active_flight_id"
        internal const val PREF_LAST_HEARTBEAT_MILLIS = "last_heartbeat_millis"
        internal const val PREF_LAST_SAMPLE_MILLIS = "last_sample_millis"

        internal fun startIntent(context: Context, flightId: Long) =
            Intent(context, FlightRecordingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_FLIGHT_ID, flightId)
                putExtra(EXTRA_VISIBLE_USER_ACTION, true)
            }

        internal fun endIntent(context: Context) =
            Intent(context, FlightRecordingService::class.java).apply { action = ACTION_END }
    }
}
