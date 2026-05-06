package com.example.daat.data.model

data class Snipe(
    val id: String = "",
    val hunterId: String = "",
    val targetId: String = "",
    val groupId: String = "",
    val timestamp: Long = 0,
    val imageUrl: String = "",
    val status: SnipeStatus = SnipeStatus.PENDING,
    val pointsAwarded: Int = 0,
    val likes: Int = 0,
    val isLikedByMe: Boolean = false,   // derived client-side, not stored
    /** Firestore stores the real list; the app reads this to compute isLikedByMe */
    val likedBy: List<String> = emptyList(),
    /** True once the target has submitted a challenge — prevents double-challenging */
    val hasBeenChallenged: Boolean = false
)

enum class SnipeStatus {
    PENDING,
    VERIFIED,
    REJECTED,
    OVERTURNED  // admin ruled the snipe invalid after a challenge
}