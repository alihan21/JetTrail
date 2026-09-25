package com.jettrail.app.domain

import kotlin.math.pow

object AtmosphereMath {
    /** ISA pressure altitude estimate. In a pressurized cabin this describes the cabin, not the aircraft. */
    fun pressureAltitudeM(pressureHpa: Double, seaLevelPressureHpa: Double = 1013.25): Double? {
        if (!pressureHpa.isFinite() || !seaLevelPressureHpa.isFinite() || pressureHpa <= 0 || seaLevelPressureHpa <= 0) return null
        return 44_330.0 * (1.0 - (pressureHpa / seaLevelPressureHpa).pow(1.0 / 5.255))
    }
}
