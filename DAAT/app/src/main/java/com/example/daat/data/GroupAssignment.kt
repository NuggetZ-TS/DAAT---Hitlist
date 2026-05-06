package com.example.daat.data.model

/**
 * One player's assignment within a specific group for a specific calendar day.
 *
 * Stored at:  groups/{groupId}/assignments/{hunterId}_{dateKey}
 * dateKey format: "yyyy-MM-dd" (UTC)
 */
data class GroupAssignment(
    val id: String = "",                        // "{hunterId}_{dateKey}"
    val groupId: String = "",
    val hunterId: String = "",
    val targetId: String = "",
    val dateKey: String = "",                   // e.g. "2025-04-28"
    /** True when this hunter was themselves sniped today — they are out for the day. */
    val isHunterEliminated: Boolean = false,
    /** True when this hunter has already successfully sniped their target today. */
    val isTargetEliminated: Boolean = false,
    val timestamp: Long = 0L
)
