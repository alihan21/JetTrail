package com.jettrail.app.recording

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.pow
import kotlin.math.sqrt

internal data class SensorSnapshot(
    val pressureHectopascals: Float?,
    val estimatedCabinAltitudeMeters: Double?,
    val turbulenceRms: Float?,
    val turbulencePeak: Float?,
    val handheldMotionLikely: Boolean,
)

/** Collects optional pressure and orientation-independent rough motion intensity. */
internal class SensorSampler(context: Context) : SensorEventListener {
    private val manager = context.getSystemService(SensorManager::class.java)
    private val pressureSensor = manager.getDefaultSensor(Sensor.TYPE_PRESSURE)
    private val linearSensor = manager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val accelerationSensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val motionSensor = linearSensor ?: accelerationSensor

    private val lock = Any()
    private var pressure: Float? = null
    private var gravity = FloatArray(3)
    private var sumSquares = 0.0
    private var peak = 0f
    private var motionCount = 0
    private var largeJerkCount = 0
    private var previousMagnitude = 0f

    val hasPressureSensor: Boolean get() = pressureSensor != null

    fun start() {
        pressureSensor?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        motionSensor?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stop() {
        manager.unregisterListener(this)
    }

    fun snapshotAndResetMotion(): SensorSnapshot = synchronized(lock) {
        val rms = if (motionCount > 0) sqrt(sumSquares / motionCount).toFloat() else null
        val currentPressure = pressure
        SensorSnapshot(
            pressureHectopascals = currentPressure,
            estimatedCabinAltitudeMeters = currentPressure?.let(::standardPressureAltitudeMeters),
            turbulenceRms = rms,
            turbulencePeak = if (motionCount > 0) peak else null,
            // Repeated sharp changes are much more typical of manipulation than airframe motion.
            handheldMotionLikely = largeJerkCount >= 2 || peak > 5f,
        ).also {
            sumSquares = 0.0
            peak = 0f
            motionCount = 0
            largeJerkCount = 0
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_PRESSURE -> synchronized(lock) { pressure = event.values.firstOrNull() }
            Sensor.TYPE_LINEAR_ACCELERATION, Sensor.TYPE_ACCELEROMETER -> recordMotion(event)
        }
    }

    private fun recordMotion(event: SensorEvent) = synchronized(lock) {
        val values = FloatArray(3)
        for (index in 0..2) {
            val raw = event.values.getOrElse(index) { 0f }
            if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                gravity[index] = GRAVITY_ALPHA * gravity[index] + (1f - GRAVITY_ALPHA) * raw
                values[index] = raw - gravity[index]
            } else {
                values[index] = raw
            }
        }
        val magnitude = sqrt(values.sumOf { it.toDouble().pow(2.0) }).toFloat()
        sumSquares += magnitude.toDouble().pow(2.0)
        peak = maxOf(peak, magnitude)
        if (kotlin.math.abs(magnitude - previousMagnitude) > 2.5f) largeJerkCount++
        previousMagnitude = magnitude
        motionCount++
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    companion object {
        private const val GRAVITY_ALPHA = 0.8f

        // Standard-atmosphere pressure altitude. In a pressurized aircraft this estimates
        // cabin pressure altitude; it is deliberately never called aircraft/GPS altitude.
        private fun standardPressureAltitudeMeters(pressureHpa: Float): Double? {
            if (!pressureHpa.isFinite() || pressureHpa <= 0f) return null
            return 44_330.0 * (1.0 - (pressureHpa / 1013.25).toDouble().pow(0.190294957))
        }
    }
}
