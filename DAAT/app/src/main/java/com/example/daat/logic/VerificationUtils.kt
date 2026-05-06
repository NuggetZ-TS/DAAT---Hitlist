package com.example.daat.logic

import kotlin.math.*

object VerificationUtils {

    /**
     * Maximum allowed distance between hunter and target in metres.
     * 50 m is roughly half a city block — tight enough to require proximity,
     * loose enough for GPS drift (~5–15 m on a good fix).
     */
    const val MAX_DISTANCE_METERS = 50.0

    /**
     * ±30° bearing tolerance.
     *
     * Why 30° and not tighter:
     *  - Phone compass can drift ±10° from magnetic interference / cases.
     *  - GPS coordinates have ~5–15 m error on each device, which at 10 m
     *    separation introduces ~8° of bearing error on its own.
     *  - Camera field of view is typically ~70° wide, so ±30° still requires
     *    you to be roughly pointing the camera toward the target.
     *
     * This is stricter than the previous 45° but realistic for real hardware.
     */
    const val MAX_BEARING_OFFSET_DEGREES = 30.0

    /** Photo must have been taken within the last 30 seconds. */
    private const val MAX_PHOTO_AGE_MS = 30_000L

    fun isPhotoFresh(capturedAt: Long): Boolean =
        (System.currentTimeMillis() - capturedAt) < MAX_PHOTO_AGE_MS

    // ── Distance ─────────────────────────────────────────────────

    /**
     * Haversine formula — returns distance in metres.
     */
    fun calculateDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    // ── Bearing ──────────────────────────────────────────────────

    /**
     * Returns the forward bearing in degrees (0 = North, clockwise)
     * from point 1 to point 2.
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
     * Angular difference between two bearings, always in [0, 180].
     */
    fun bearingDiff(a: Double, b: Double): Double =
        abs(((a - b + 540) % 360) - 180)

    /**
     * True if [hunterHeading] is within ±[MAX_BEARING_OFFSET_DEGREES]
     * of [targetBearing].
     */
    fun isPointingAtTarget(hunterHeading: Double, targetBearing: Double): Boolean =
        bearingDiff(hunterHeading, targetBearing) <= MAX_BEARING_OFFSET_DEGREES

    // ── Full verification ─────────────────────────────────────────

    data class VerificationResult(
        val success: Boolean,
        val errorCode: String = "",
        val reason: String,
        val distanceMeters: Double = 0.0,
        val bearingDiff: Double = 0.0
    )

    /**
     * Runs every check in order and returns a detailed result.
     *
     * Call this from the UI layer to get human-readable failure reasons.
     * [FirebaseGameRepository.submitSnipe] re-runs the same logic server-side
     * using the freshest Firestore values as a second layer of verification.
     */
    fun verifySnipe(
        hunterLat: Double,
        hunterLon: Double,
        hunterHeading: Double,
        targetLat: Double?,
        targetLon: Double?,
        capturedAt: Long
    ): VerificationResult {

        // 0. Photo freshness
        if (!isPhotoFresh(capturedAt)) {
            return VerificationResult(
                success = false,
                errorCode = "TOO_OLD",
                reason = "Photo is too old — must be taken within 30 seconds."
            )
        }

        // 1. Target location availability
        if (targetLat == null || targetLon == null || (targetLat == 0.0 && targetLon == 0.0)) {
            return VerificationResult(
                success = false,
                errorCode = "TARGET_LOCATION_UNAVAILABLE",
                reason = "Target's location isn't available yet. Ask them to open the app."
            )
        }

        // 2. Distance
        val distance = calculateDistance(hunterLat, hunterLon, targetLat, targetLon)
        if (distance > MAX_DISTANCE_METERS) {
            return VerificationResult(
                success = false,
                errorCode = "TOO_FAR",
                reason = "Too far — you are ${distance.toInt()}m away (max ${MAX_DISTANCE_METERS.toInt()}m).",
                distanceMeters = distance
            )
        }

        // 3. Bearing / orientation
        val bearing = calculateBearing(hunterLat, hunterLon, targetLat, targetLon)
        val diff = bearingDiff(hunterHeading, bearing)
        if (diff > MAX_BEARING_OFFSET_DEGREES) {
            return VerificationResult(
                success = false,
                errorCode = "WRONG_ORIENTATION",
                reason = "Camera not aimed at target — off by ${diff.toInt()}° (max ${MAX_BEARING_OFFSET_DEGREES.toInt()}°).",
                distanceMeters = distance,
                bearingDiff = diff
            )
        }

        return VerificationResult(
            success = true,
            reason = "Verified ✓",
            distanceMeters = distance,
            bearingDiff = diff
        )
    }
}