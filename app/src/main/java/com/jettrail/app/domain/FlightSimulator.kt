package com.jettrail.app.domain

import kotlin.math.sin
import kotlin.random.Random

data class SimulationScenario(
    val seed: Int = 7,
    val sampleCount: Int = 181,
    /** Samples can be injected rapidly, while their timestamps retain a physically plausible compressed flight. */
    val intervalMillis: Long = 10_000,
)

object FlightSimulator {
    /** A compressed BRU-LHR-like profile containing deterministic noise, dropouts and explicit outliers. */
    fun generate(scenario: SimulationScenario = SimulationScenario()): List<RawFlightSample> {
        require(scenario.sampleCount >= 30)
        val random = Random(scenario.seed)
        val start = 1_700_000_000_000L
        return List(scenario.sampleCount) { i ->
            val p = i.toDouble() / (scenario.sampleCount - 1)
            val altitude = when {
                p < .18 -> 60 + p / .18 * 10_800
                p < .72 -> 10_860 + sin(p * 18) * 30
                else -> 10_860 * (1 - (p - .72) / .28) + 60
            }
            val speed = when {
                p < .08 -> p / .08 * 80
                p < .18 -> 80 + (p - .08) / .10 * 160
                p < .72 -> 240.0
                p < .94 -> 240 - (p - .72) / .22 * 165
                else -> 75 * (1 - (p - .94) / .06)
            }
            val dropout = i in (scenario.sampleCount * 45 / 100)..(scenario.sampleCount * 49 / 100)
            val outlier = i == scenario.sampleCount * 2 / 3
            val lat = 50.901 + p * 0.569 + random.nextDouble(-.0003, .0003)
            val lon = 4.484 + p * -4.938 + random.nextDouble(-.0003, .0003)
            RawFlightSample(
                timestampMillis = start + i * scenario.intervalMillis,
                latitudeDeg = if (dropout) null else if (outlier) -80.0 else lat,
                longitudeDeg = if (dropout) null else if (outlier) 170.0 else lon,
                groundSpeedMps = if (outlier) 900.0 else (speed + random.nextDouble(-2.0, 2.0)).coerceAtLeast(0.0),
                bearingDeg = 286.0 + random.nextDouble(-2.0, 2.0),
                gpsAltitudeM = if (outlier) 40_000.0 else altitude + random.nextDouble(-12.0, 12.0),
                horizontalAccuracyM = if (dropout) null else if (i % 37 == 0) 320.0 else random.nextDouble(5.0, 30.0),
                verticalAccuracyM = if (dropout) null else random.nextDouble(8.0, 45.0),
                satelliteCount = if (dropout) 0 else random.nextInt(5, 18),
                pressureHpa = 1013.25 * Math.pow(1.0 - (altitude.coerceAtMost(2_400.0) / 44330.0), 5.255),
                estimatedCabinAltitudeM = altitude.coerceAtMost(2_400.0),
                linearAccelerationRmsMps2 = if (i in 80..95) 1.1 + random.nextDouble() else random.nextDouble(.1, .5),
                locationProvenance = ValueProvenance.SIMULATED,
                pressureProvenance = ValueProvenance.SIMULATED,
                isSimulated = true,
            )
        }
    }

    fun runThroughPipeline(scenario: SimulationScenario = SimulationScenario()): List<ProcessedFlightSample> =
        FlightProcessor().processAll(generate(scenario))
}
