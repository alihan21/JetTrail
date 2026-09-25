package com.jettrail.app

import com.jettrail.app.domain.*
import org.junit.Assert.*
import org.junit.Test

class StatisticsAndPhaseTest {
    @Test fun statisticsIgnoreRejectedVisibleValuesAndTrackCoverage() {
        val raw = listOf(
            RawFlightSample(0, 50.0, 4.0, 100.0, 0.0, 1_000.0, 10.0),
            RawFlightSample(1_000, 50.0005, 4.0, 110.0, 0.0, 1_005.0, 20.0),
            RawFlightSample(2_000, -80.0, 170.0, 900.0, 0.0, 40_000.0, 2.0),
            RawFlightSample(3_000, null, null, null, null, null, null),
        )
        val stats = FlightStatisticsCalculator.calculate(FlightProcessor().processAll(raw))
        assertEquals(3, stats.acceptedSamples) // dropout is valid retained data, not a fabricated fix
        assertEquals(1, stats.rejectedSamples)
        assertEquals(110.0, stats.maximumGroundSpeedMps!!, 0.0)
        assertTrue(stats.distanceM in 50.0..60.0)
        assertEquals(.75, stats.gpsCoverageFraction, 0.0)
        assertEquals(3_000L, stats.durationMillis)
    }

    @Test fun phaseClassificationAndSustainedSuggestion() {
        assertEquals(FlightPhase.GROUND, PhaseDetector.classify(3.0, 0.0, 50.0))
        assertEquals(FlightPhase.TAKEOFF, PhaseDetector.classify(80.0, 8.0, 500.0))
        assertEquals(FlightPhase.CRUISE, PhaseDetector.classify(230.0, .2, 11_000.0))
        assertEquals(FlightPhase.LANDING, PhaseDetector.classify(70.0, -5.0, 900.0))
        val recent = List(4) { i -> ProcessedFlightSample(
            RawFlightSample(i * 1_000L, 1.0, 1.0, 80.0, 0.0, 500.0, 5.0), true, phase = FlightPhase.TAKEOFF
        ) }
        assertEquals(FlightPhase.TAKEOFF, PhaseDetector.suggestedTransition(recent))
    }
}
