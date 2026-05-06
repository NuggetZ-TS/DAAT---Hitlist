package com.example.daat.data.model

data class SnipeChallenge(
    val id: String = "",
    val snipeId: String = "",
    val groupId: String = "",
    val challengerId: String = "",   // the targetId who is disputing
    val hunterId: String = "",       // the person who took the snipe
    val pointsAtStake: Int = 0,      // points to reverse if overturned
    val timestamp: Long = 0L,
    val status: ChallengeStatus = ChallengeStatus.PENDING,
    val imageUrl: String = "",       // copy of the snipe image for the admin popup
    val hunterName: String = "",
    val challengerName: String = ""
)

enum class ChallengeStatus {
    PENDING,    // waiting for admin decision
    UPHELD,     // admin ruled snipe was fair — points stay
    OVERTURNED  // admin ruled snipe was unfair — points reversed
}
