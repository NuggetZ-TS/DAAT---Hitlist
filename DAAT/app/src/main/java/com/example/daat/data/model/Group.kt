package com.example.daat.data.model

data class Group(
    val id: String,
    val name: String,
    val description: String,
    val memberIds: List<String> = emptyList(),
    val adminId: String,
    val inviteCode: String
)
