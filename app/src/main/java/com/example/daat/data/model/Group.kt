package com.example.daat.data.model

data class Group(
    val id: String,
    val name: String,
    val description: String,
    val membersCount: Int,
    val isJoined: Boolean = false,
    val creatorId: String
)
