package com.example.daat.data.model

data class Snipe(
    val id: String,
    val hunterId: String,
    val targetId: String,
    val timestamp: Long,
    val imageUrl: String,
    val status: SnipeStatus = SnipeStatus.PENDING,
    val pointsAwarded: Int = 0,
    val likes: Int = 0,
    val isLikedByMe: Boolean = false
)

enum class SnipeStatus {
    PENDING,
    VERIFIED,
    REJECTED
}
