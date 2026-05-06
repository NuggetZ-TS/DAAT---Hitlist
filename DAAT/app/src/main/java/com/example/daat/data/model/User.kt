package com.example.daat.data.model

data class User(
    val id: String = "",
    val name: String = "",
    val username: String = "",
    val profileImageUrl: String? = null,
    val totalScore: Int = 0,
    val currentTargetId: String? = null,
    val targetAssignedAt: Long? = null,
    val currentStreak: Int = 0,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val lastLocationUpdate: Long? = null,
    val groupIds: List<String> = emptyList(),
    /**
     * The groupId the player last selected on the Home screen.
     * Persisted so it survives app restarts.
     */
    val activeGroupId: String? = null
) {
    fun toPublicProfile() = copy(
        latitude = null,
        longitude = null,
        lastLocationUpdate = null,
        currentTargetId = null,
        targetAssignedAt = null
    )
}