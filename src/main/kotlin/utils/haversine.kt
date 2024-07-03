package utils

import kotlin.math.*

fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val radiusEarth = 6371000
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val originLat = Math.toRadians(lat1)
    val destinationLat = Math.toRadians(lat2)

    val a = sin(dLat / 2).pow(2) + sin(dLon / 2).pow(2) * cos(originLat) * cos(destinationLat)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return radiusEarth * c
}