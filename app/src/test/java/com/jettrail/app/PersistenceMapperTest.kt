package com.jettrail.app

import com.jettrail.app.data.toDomain
import com.jettrail.app.data.toEntity
import com.jettrail.app.domain.*
import org.junit.Assert.*
import org.junit.Test

class PersistenceMapperTest {
    @Test fun rawAndProcessingProvenanceRoundTripWithoutLoss() {
        val raw = RawFlightSample(
            123, 50.0, 4.0, 200.0, 270.0, 10_000.0, 15.0, 30.0, 8,
            800.0, 2_000.0, 1.2, ValueProvenance.SIMULATED, ValueProvenance.SIMULATED, true,
        )
        val original = ProcessedFlightSample(
            raw, false, setOf(SampleRejection.POOR_ACCURACY, SampleRejection.INVALID_SPEED),
            42.0, 3.0, FlightPhase.CLIMB, TurbulenceEstimate.MODERATE,
        )
        val restored = original.toEntity(99).toDomain()
        assertEquals(original, restored)
    }
}
