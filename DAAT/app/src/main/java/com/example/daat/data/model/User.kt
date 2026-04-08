package com.example.daat.data.model

/**
 * User represents both the current player and other users.
 * In a real backend, location data would be private and not sent to the app.
 */
data class User(
    val id: String,
    val name: String,
    val username: String,
    val profileImageUrl: String? = null,
    val totalScore: Int = 0,
    val currentTargetId: String? = null,
    val targetAssignedAt: Long? = null,
    val currentStreak: Int = 0,
    
    // In a production app, these fields should be NULL for other users
    // when requested from the API to preserve privacy.
    val latitude: Double? = null,
    val longitude: Double? = null,
    val lastLocationUpdate: Long? = null
) {
    /**
     * Returns a version of this user with private data stripped.
     */
    fun toPublicProfile() = copy(
        latitude = null,
        longitude = null,
        lastLocationUpdate = null,
        currentTargetId = null,
        targetAssignedAt = null
    )
}
