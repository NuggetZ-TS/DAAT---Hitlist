package com.example.daat.data.model

data class Group(
    val id: String = "",
    val name: String = "",
    val inviteCode: String = "",
    val adminId: String = "",
    val members: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)
