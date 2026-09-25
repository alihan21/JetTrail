package com.jettrail.app.domain

import kotlin.math.*

object GeoMath {
    private const val EARTH_RADIUS_M = 6_371_000.0

    fun isCoordinateValid(latitudeDeg: Double?, longitudeDeg: Double?): Boolean =
        latitudeDeg != null && longitudeDeg != null && latitudeDeg.isFinite() && longitudeDeg.isFinite() &&
            latitudeDeg in -90.0..90.0 && longitudeDeg in -180.0..180.0

    fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = Math.toRadians(lat2 - lat1)
        val dl = Math.toRadians(lon2 - lon1)
        val a = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
        return EARTH_RADIUS_M * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
