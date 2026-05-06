package com.example.daat.data.repository

import com.example.daat.data.model.GroupAssignment
import kotlinx.coroutines.flow.Flow

interface AssignmentRepository {

    /** Live stream of today's assignment for [hunterId] in [groupId]. Null = none yet. */
    fun getAssignment(groupId: String, hunterId: String): Flow<GroupAssignment?>

    /**
     * Checks if the group needs a daily target reset (lastAssignedDateKey != today).
     * If so, shuffles members into a target ring and writes new assignments.
     * Uses a Firestore transaction so only the first concurrent caller actually writes.
     * Returns true if a reset was performed.
     */
    suspend fun triggerDailyResetIfNeeded(groupId: String): Result<Boolean>

    /**
     * Admin activates a group session for [durationDays] (1–7).
     * Sets gameActive=true, sessionEndTime, clears lastAssignedDateKey so the
     * first triggerDailyResetIfNeeded call writes fresh assignments immediately.
     */
    suspend fun activateGroup(groupId: String, adminId: String, durationDays: Int): Result<Unit>

    /** Admin deactivates the session early. */
    suspend fun deactivateGroup(groupId: String, adminId: String): Result<Unit>

    /**
     * Admin forces a fresh assignment mid-session (e.g. after someone joins).
     * Clears lastAssignedDateKey then calls triggerDailyResetIfNeeded.
     */
    suspend fun forceReassign(groupId: String, adminId: String): Result<Unit>

    /**
     * Records a successful snipe in the assignment layer:
     * - Hunter's doc: isTargetEliminated = true
     * - Target's doc: isHunterEliminated = true  (they are out for the day)
     */
    suspend fun markEliminated(groupId: String, hunterId: String, targetId: String): Result<Unit>

    /** Persists the player's active group selection to their user document. */
    suspend fun setActiveGroup(userId: String, groupId: String?): Result<Unit>
}
