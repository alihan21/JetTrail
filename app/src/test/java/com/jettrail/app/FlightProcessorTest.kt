package com.jettrail.app

import com.jettrail.app.domain.*
import org.junit.Assert.*
import org.junit.Test

class FlightProcessorTest {
    private fun sample(t: Long, lat: Double = 50.0, lon: Double = 4.0, speed: Double = 200.0, altitude: Double = 10_000.0) =
        RawFlightSample(t, lat, lon, speed, 270.0, altitude, 12.0, satelliteCount = 9)

    @Test fun impossibleJumpIsExcludedButRawIsRetained() {
        val processor = FlightProcessor()
        assertTrue(processor.process(sample(1_000)).acceptedForStatistics)
        val impossible = sample(2_000, lat = 55.0, lon = 30.0, speed = 200.0, altitude = 10_000.0)
        val result = processor.process(impossible)
        assertFalse(result.acceptedForStatistics)
        assertSame(impossible, result.raw)
        assertTrue(SampleRejection.IMPOSSIBLE_POSITION_JUMP in result.rejectionReasons)
    }

    @Test fun gpsDropoutIsCoverageLossNotInventedOutlier() {
        val result = FlightProcessor().process(RawFlightSample(1_000, null, null, null, null, null, null))
        assertTrue(result.acceptedForStatistics)
        assertTrue(result.rejectionReasons.isEmpty())
        assertEquals(FlightPhase.UNKNOWN, result.phase)
    }

    @Test fun badAccuracyAndBackwardsTimeAreRejected() {
        val processor = FlightProcessor()
        processor.process(sample(2_000))
        assertTrue(SampleRejection.POOR_ACCURACY in processor.process(sample(3_000).copy(horizontalAccuracyM = 500.0)).rejectionReasons)
        assertTrue(SampleRejection.INVALID_TIME in processor.process(sample(1_000)).rejectionReasons)
    }

    @Test fun turbulenceEstimateIsExplicitlyCoarse() {
        assertEquals(TurbulenceEstimate.UNAVAILABLE, TurbulenceClassifier.classify(null))
        assertEquals(TurbulenceEstimate.SMOOTH, TurbulenceClassifier.classify(.2))
        assertEquals(TurbulenceEstimate.MODERATE, TurbulenceClassifier.classify(1.2))
        assertEquals(5.0, TurbulenceClassifier.rms(listOf(3.0, 4.0, 0.0, Math.sqrt(75.0))), 1e-9)
    }

    @Test fun cachedFixDoesNotEraseOrCorruptVerticalSpeed() {
        val processor = FlightProcessor()
        processor.process(sample(1_000, altitude = 1_000.0))
        val climb = processor.process(sample(11_000, altitude = 1_100.0))
        assertEquals(10.0, climb.verticalSpeedMps!!, 1e-9)

        val cached = processor.process(sample(12_000, altitude = 1_100.0).copy(isNewLocationFix = false))
        assertNull(cached.verticalSpeedMps)
        assertNull(cached.distanceFromPreviousM)

        val continuedClimb = processor.process(sample(21_000, altitude = 1_200.0))
        assertEquals(10.0, continuedClimb.verticalSpeedMps!!, 1e-9)
    }

    @Test fun verticalSpeedUsesTrendInsteadOfFlippingWithAltitudeNoise() {
        val processor = FlightProcessor()
        processor.process(sample(1_000, altitude = 1_000.0))
        val estimates = listOf(
            processor.process(sample(6_000, altitude = 1_047.0)).verticalSpeedMps,
            processor.process(sample(11_000, altitude = 1_104.0)).verticalSpeedMps,
            processor.process(sample(16_000, altitude = 1_148.0)).verticalSpeedMps,
            processor.process(sample(21_000, altitude = 1_203.0)).verticalSpeedMps,
        )

        assertTrue(estimates.filterNotNull().all { it > 0.0 })
        assertEquals(10.0, estimates.last()!!, 0.5)
    }

    @Test fun firstFixAfterLongOutageIsQuarantinedUntilConfirmed() {
        val processor = FlightProcessor()
        assertTrue(processor.process(sample(1_000, lat = 50.0, lon = 4.0)).acceptedForStatistics)

        val tentative = processor.process(sample(61_000, lat = 48.0, lon = 2.0))
        assertFalse(tentative.acceptedForStatistics)
        assertTrue(SampleRejection.UNCONFIRMED_REACQUISITION in tentative.rejectionReasons)

        val confirmed = processor.process(sample(62_000, lat = 48.001, lon = 2.002))
        assertTrue(confirmed.acceptedForStatistics)
        assertTrue(confirmed.startsNewSegment)
        assertNotNull(confirmed.distanceFromPreviousM)
    }

    @Test fun nullIslandAndNonEuropeanFixesCannotDistortEuropeanRoute() {
        val processor = FlightProcessor()
        val nullIsland = processor.process(sample(1_000, lat = 0.0, lon = 0.0))
        assertFalse(nullIsland.acceptedForStatistics)
        assertTrue(SampleRejection.OUTSIDE_SUPPORTED_REGION in nullIsland.rejectionReasons)
    }

    @Test fun pressureAltitudeIsCabinEstimateAndHandlesInvalidInput() {
        assertEquals(0.0, AtmosphereMath.pressureAltitudeM(1013.25)!!, 1e-6)
        assertTrue(AtmosphereMath.pressureAltitudeM(750.0)!! in 2_300.0..2_600.0)
        assertNull(AtmosphereMath.pressureAltitudeM(-1.0))
    }
}
