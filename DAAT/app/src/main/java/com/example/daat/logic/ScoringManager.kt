package com.example.daat.logic

import java.util.concurrent.TimeUnit

object ScoringManager {

    // Base point values
    const val BASE_SNIPE_POINTS = 100
    const val STREAK_BONUS_PER_LEVEL = 25
    const val CLOSE_QUARTERS_BONUS = 50 // Sniper was within 5 meters
    const val SPEED_BONUS = 75 // Snipe happened within 2 hours of assignment

    /**
     * Calculates the total points for a successful snipe.
     */
    fun calculatePoints(
        distanceMeters: Double,
        streak: Int,
        targetAssignedAt: Long?,
        capturedAt: Long = System.currentTimeMillis()
    ): Int {
        var totalPoints = BASE_SNIPE_POINTS

        // 1. Streak Multiplier (The more you hunt without being caught, the higher the bounty)
        totalPoints += (streak * STREAK_BONUS_PER_LEVEL)

        // 2. Proximity Bonus (High risk, high reward)
        if (distanceMeters < 5.0) {
            totalPoints += CLOSE_QUARTERS_BONUS
        }

        // 3. Speed Bonus (Fastest hunter)
        if (targetAssignedAt != null) {
            val timeElapsed = capturedAt - targetAssignedAt
            val twoHoursInMillis = TimeUnit.HOURS.toMillis(2)
            if (timeElapsed < twoHoursInMillis) {
                totalPoints += SPEED_BONUS
            }
        }

        return totalPoints
    }

    /**
     * Determines if a streak should be incremented or reset.
     * In the future, this could be tied to being "sniped" yourself.
     */
    fun getUpdatedStreak(currentStreak: Int, isSuccess: Boolean): Int {
        return if (isSuccess) currentStreak + 1 else 0
    }
}
