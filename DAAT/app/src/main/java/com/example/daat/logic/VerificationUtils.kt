package com.example.daat.logic

import kotlin.math.*

object VerificationUtils {

    private const val MAX_DISTANCE_METERS = 50.0
    private const val MAX_BEARING_OFFSET_DEGREES = 20.0  // ±20° tolerance
    private const val MAX_PHOTO_AGE_MS = 5 * 60 * 1000 // 5 minutes

    /**
     * Returns true if the photo was captured within the last 5 minutes.
     */
    fun isPhotoFresh(capturedAt: Long): Boolean {
        return (System.currentTimeMillis() - capturedAt) < MAX_PHOTO_AGE_MS
    }

    // ── Distance ─────────────────────────────────────────────────

    /**
     * Haversine formula — returns distance in metres between two GPS coordinates.
     */
    fun calculateDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val earthRadius = 6_371_000.0 // metres
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }

    // ── Bearing ──────────────────────────────────────────────────

    /**
     * Returns the bearing (degrees, 0 = North, clockwise) from point 1 → point 2.
     */
    fun calculateBearing(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val dLon = Math.toRadians(lon2 - lon1)
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)
        val y = sin(dLon) * cos(rLat2)
        val x = cos(rLat1) * sin(rLat2) - sin(rLat1) * cos(rLat2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    /**
     * Returns true if [hunterHeading] (phone compass, degrees) is within
     * ±[MAX_BEARING_OFFSET_DEGREES] of the bearing towards the target.
     */
    fun isPointingAtTarget(hunterHeading: Double, targetBearing: Double): Boolean {
        val diff = abs(((hunterHeading - targetBearing + 540) % 360) - 180)
        return diff <= MAX_BEARING_OFFSET_DEGREES
    }

    // ── Full snipe verification ───────────────────────────────────

    data class VerificationResult(
        val success: Boolean,
        val reason: String,
        val distanceMeters: Double = 0.0,
        val bearingDiff: Double = 0.0
    )

    /**
     * Runs all checks and returns a [VerificationResult].
     *
     * @param hunterLat      shooter's latitude
     * @param hunterLon      shooter's longitude
     * @param hunterHeading  shooter's compass heading (degrees)
     * @param targetLat      target's latitude (fetched from Firebase)
     * @param targetLon      target's longitude (fetched from Firebase)
     */
    fun verifySnipe(
        hunterLat: Double,
        hunterLon: Double,
        hunterHeading: Double,
        targetLat: Double,
        targetLon: Double
    ): VerificationResult {

        // 1. Distance check
        val distance = calculateDistance(hunterLat, hunterLon, targetLat, targetLon)
        if (distance > MAX_DISTANCE_METERS) {
            return VerificationResult(
                success = false,
                reason = "Too far! You are ${distance.toInt()}m away (max ${MAX_DISTANCE_METERS.toInt()}m).",
                distanceMeters = distance
            )
        }

        // 2. Orientation check
        val bearing = calculateBearing(hunterLat, hunterLon, targetLat, targetLon)
        val diff = abs(((hunterHeading - bearing + 540) % 360) - 180)
        if (!isPointingAtTarget(hunterHeading, bearing)) {
            return VerificationResult(
                success = false,
                reason = "Phone not pointing at target. Off by ${diff.toInt()}° (max ${MAX_BEARING_OFFSET_DEGREES.toInt()}°).",
                distanceMeters = distance,
                bearingDiff = diff
            )
        }

        return VerificationResult(
            success = true,
            reason = "Snipe verified! 📸",
            distanceMeters = distance,
            bearingDiff = diff
        )
    }
}
