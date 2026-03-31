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
     * Layer 1: Check if the photo is fresh (taken within the last minute).
     * This is a simple logic check to prevent gallery uploads if the UI passes a timestamp.
     */
    fun isPhotoFresh(capturedAt: Long, currentTime: Long = System.currentTimeMillis()): Boolean {
        val oneMinuteInMillis = 60 * 1000
        return (currentTime - capturedAt) < oneMinuteInMillis
    }

    /**
     * Layer 3: Check if the distance is within the allowed radius.
     */
    fun isWithinRadius(
        hunterLat: Double, hunterLon: Double,
        targetLat: Double, targetLon: Double,
        radiusMeters: Double = 30.0
    ): Boolean {
        val distance = calculateDistance(hunterLat, hunterLon, targetLat, targetLon)
        return distance <= radiusMeters
    }
}
