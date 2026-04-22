package com.example.daat.logic

import kotlin.math.*

object VerificationUtils {

    /**
     * Calculates the distance between two points in meters using the Haversine formula.
     */
    fun calculateDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val r = 6371e3 // Earth's radius in meters
        val phi1 = lat1 * PI / 180
        val phi2 = lat2 * PI / 180
        val deltaPhi = (lat2 - lat1) * PI / 180
        val deltaLambda = (lon2 - lon1) * PI / 180

        val a = sin(deltaPhi / 2).pow(2) +
                cos(phi1) * cos(phi2) *
                sin(deltaLambda / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return r * c
    }

    /**
     * Calculates the initial bearing from point A to point B in degrees (0-360).
     */
    fun calculateBearing(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaLambda = Math.toRadians(lon2 - lon1)

        val y = sin(deltaLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)
        
        val bearing = Math.toDegrees(atan2(y, x))
        return (bearing + 360) % 360
    }

    /**
     * Check if the device's heading is pointing towards the target within a certain threshold.
     * @param deviceHeading The compass heading of the phone (0-360).
     * @param targetBearing The bearing from hunter to target (0-360).
     * @param thresholdDegrees The allowed margin of error (e.g. 30 degrees).
     */
    fun isPointingAtTarget(
        deviceHeading: Double,
        targetBearing: Double,
        thresholdDegrees: Double = 30.0
    ): Boolean {
        val diff = abs(deviceHeading - targetBearing)
        val wrappedDiff = if (diff > 180) 360 - diff else diff
        return wrappedDiff <= thresholdDegrees
    }

    fun isPhotoFresh(capturedAt: Long, currentTime: Long = System.currentTimeMillis()): Boolean {
        val oneMinuteInMillis = 60 * 1000
        return (currentTime - capturedAt) < oneMinuteInMillis
    }
}
