package com.example.daat.logic

import java.util.concurrent.TimeUnit

object ScoringManager {
    const val BASE_SNIPE_POINTS = 100
    
    // Survival Reward Configuration
    const val BASE_SURVIVAL_REWARD = 50
    val SURVIVAL_REWARD_INTERVAL = TimeUnit.HOURS.toMillis(24) // Daily rewards

    /**
     * Calculates points for a snipe.
     * The reward is 100 points multiplied by the number of days survived.
     * If you've survived 3 days, you get 100 * 3 = 300 points.
     */
    fun calculateSnipePoints(lastSnipedAt: Long?, currentTime: Long = System.currentTimeMillis()): Int {
        val daysSurvived = getDaysSurvived(lastSnipedAt, currentTime)
        val multiplier = maxOf(1, daysSurvived.toInt())
        return BASE_SNIPE_POINTS * multiplier
    }

    /**
     * Calculates the daily survival bonus.
     * Also uses the multiplier: 50 * days survived.
     */
    fun calculateSurvivalBonus(daysSurvived: Long): Int {
        val multiplier = maxOf(1, daysSurvived.toInt())
        return BASE_SURVIVAL_REWARD * multiplier
    }

    fun getDaysSurvived(lastSnipedAt: Long?, currentTime: Long = System.currentTimeMillis()): Long {
        if (lastSnipedAt == null) return 1 // Start at day 1
        val diff = currentTime - lastSnipedAt
        return TimeUnit.MILLISECONDS.toDays(diff) + 1 // +1 because you are currently on day X
    }

    fun shouldReceiveSurvivalReward(lastRewardAt: Long?, currentTime: Long = System.currentTimeMillis()): Boolean {
        val lastEvent = lastRewardAt ?: 0L
        return (currentTime - lastEvent) >= SURVIVAL_REWARD_INTERVAL
    }
}
