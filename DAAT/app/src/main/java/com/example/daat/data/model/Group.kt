package com.example.daat.data.model

data class Group(
    val id: String = "",
    val name: String = "",
    val inviteCode: String = "",
    val adminId: String = "",
    val members: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),

    // ── Session fields ────────────────────────────────────────────
    /** True while a game session is running. */
    val gameActive: Boolean = false,
    /** Unix ms when the session expires. 0 = no active session. */
    val sessionEndTime: Long = 0L,
    /** 1–7 days chosen by admin at activation. */
    val sessionDurationDays: Int = 1,
    /**
     * UTC date key "yyyy-MM-dd" of the last time daily targets were
     * assigned. Used for lazy-reset: if this != today we reassign.
     */
    val lastAssignedDateKey: String = ""
)