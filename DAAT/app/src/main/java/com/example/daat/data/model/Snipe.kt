package com.example.daat.data.model

data class Snipe(
    val id: String = "",
    val hunterId: String = "",
    val targetId: String = "",
    val timestamp: Long = 0,
    val imageUrl: String = "",
    val status: SnipeStatus = SnipeStatus.PENDING,
    val pointsAwarded: Int = 0,
    val likes: Int = 0,
    val likedBy: List<String> = emptyList(), // Track who liked it to prevent duplicates
    val isLikedByMe: Boolean = false, // Client-side helper
    val verifiedBy: List<String> = emptyList(),
    val rejectedBy: List<String> = emptyList(),
    val groupId: String = "",
    val targetConfirmed: Boolean = false
)

enum class SnipeStatus {
    PENDING,
    VERIFIED,
    REJECTED,
    CHALLENGED
}
