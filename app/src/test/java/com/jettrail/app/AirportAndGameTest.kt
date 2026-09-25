package com.jettrail.app

import com.jettrail.app.domain.*
import org.junit.Assert.*
import org.junit.Test

class AirportAndGameTest {
    private val bru = Airport("EBBR", "Brussels", 50.901, 4.484, "BE", "BRU")
    private val lhr = Airport("EGLL", "Heathrow", 51.470, -0.454, "GB", "LHR")

    @Test fun nearestAirportUsesDistanceAndCutoff() {
        assertEquals("EBBR", AirportInferenceEngine.nearest(50.90, 4.48, listOf(lhr, bru)).airport?.ident)
        val nowhere = AirportInferenceEngine.nearest(0.0, 0.0, listOf(lhr, bru), maxDistanceM = 1_000.0)
        assertNull(nowhere.airport)
        assertEquals(ValueProvenance.UNAVAILABLE, nowhere.provenance)
    }

    @Test fun routeInferenceUsesFirstAndLastAcceptedFix() {
        val raw = listOf(
            RawFlightSample(0, bru.latitudeDeg, bru.longitudeDeg, 0.0, 0.0, 50.0, 5.0),
            RawFlightSample(1_000_000, lhr.latitudeDeg, lhr.longitudeDeg, 0.0, 0.0, 50.0, 5.0),
        )
        // A large time gap keeps the commercial route under the implied-speed threshold.
        val route = AirportInferenceEngine.inferRoute(FlightProcessor().processAll(raw), listOf(bru, lhr))
        assertEquals("EBBR", route.first.airport?.ident)
        assertEquals("EGLL", route.second.airport?.ident)
    }

    @Test fun gamificationIsPostFlightAndDeduplicatesRoutes() {
        val a = CompletedFlightSummary("EBBR", "EGLL", "BE", "GB", 5_100_000.0, 20_000, 230.0, 11_000.0, .98)
        val b = a.copy(originIdent = "EGLL", destinationIdent = "EBBR")
        val profile = GamificationEngine.build(listOf(a, b))
        assertEquals(setOf("EBBR", "EGLL"), profile.uniqueAirports)
        assertEquals(1, profile.uniqueRoutes.size)
        assertTrue("FIRST_FLIGHT" in profile.badges)
        assertTrue("LONG_HAUL" in profile.badges)
        assertTrue("CLEAR_SKIES" in profile.badges)
        assertTrue(profile.xp > 0)
    }
}
