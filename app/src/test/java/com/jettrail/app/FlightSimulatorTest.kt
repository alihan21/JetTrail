package com.jettrail.app

import com.jettrail.app.domain.*
import org.junit.Assert.*
import org.junit.Test

class FlightSimulatorTest {
    @Test fun simulationIsDeterministicAndExercisesFailureModes() {
        val scenario = SimulationScenario(seed = 42, sampleCount = 181)
        val first = FlightSimulator.generate(scenario)
        val second = FlightSimulator.generate(scenario)
        assertEquals(first, second)
        assertTrue(first.any { it.latitudeDeg == null })
        assertTrue(first.any { (it.groundSpeedMps ?: 0.0) > 500.0 })
        assertTrue(first.any { (it.horizontalAccuracyM ?: 0.0) > 250.0 })
        assertTrue(first.all { it.isSimulated && it.locationProvenance == ValueProvenance.SIMULATED })
    }

    @Test fun simulationRunsThroughProductionPipelineAndCoversPhases() {
        val processed = FlightSimulator.runThroughPipeline(SimulationScenario(seed = 1, sampleCount = 181))
        assertTrue(processed.any { !it.acceptedForStatistics })
        assertTrue(processed.any { it.raw.latitudeDeg == null })
        assertTrue(processed.any { it.phase == FlightPhase.CLIMB || it.phase == FlightPhase.TAKEOFF })
        assertTrue(processed.any { it.phase == FlightPhase.CRUISE })
        assertTrue(processed.any { it.phase == FlightPhase.DESCENT || it.phase == FlightPhase.LANDING })
        val stats = FlightStatisticsCalculator.calculate(processed)
        assertTrue(stats.distanceM > 100_000)
        assertTrue(stats.gpsCoverageFraction in .85..1.0)
    }
}
