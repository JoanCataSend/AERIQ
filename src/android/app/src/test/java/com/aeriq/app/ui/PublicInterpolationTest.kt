package com.aeriq.app.ui

import org.junit.Assert.*
import org.junit.Test

class PublicInterpolationTest {
    private fun reading(lat: Double, lon: Double, value: Double, simulated: Boolean = false) =
        PublicReading("1", "campaign", "UPV", lat, lon, value, "ppb", "2026-11-02", simulated, 30, 60.0, 100.0)

    @Test fun noCoverageOrRepeatedLocationStaysWithoutData() {
        val r = reading(38.99665, -.166, 50.0)
        assertNull(interpolatedOzone(38.99665, -.166, listOf(r,r,r),30))
        assertNull(interpolatedOzone(39.0, -.16,listOf(r),30))
    }
    @Test fun compatibleNearbyPositionsInterpolateWithoutMixingSimulated() {
        val rows = listOf(reading(38.99665,-.166,50.0), reading(38.99670,-.166,70.0), reading(38.99665,-.16594,90.0))
        val value = interpolatedOzone(38.99666,-.16599,rows,30)
        assertNotNull(value); assertTrue(value!! in 50.0..90.0)
        assertNull(interpolatedOzone(38.99666,-.16599,rows.mapIndexed { i,r -> if (i==2) r.copy(simulated=true) else r },30))
        assertNull(interpolatedOzone(38.99666,-.16599,rows,51))
    }
}
