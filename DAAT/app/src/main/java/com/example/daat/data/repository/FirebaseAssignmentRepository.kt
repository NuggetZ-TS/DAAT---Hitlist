package com.example.daat.data.repository

import com.example.daat.data.model.Group
import com.example.daat.data.model.GroupAssignment
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

class FirebaseAssignmentRepository : AssignmentRepository {

    private val db = FirebaseFirestore.getInstance()

    // ── Helpers ───────────────────────────────────────────────────

    private fun todayKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date())

    private fun docId(hunterId: String, dateKey: String) = "${hunterId}_${dateKey}"

    private fun assignmentsCol(groupId: String) =
        db.collection("groups").document(groupId).collection("assignments")

    // ── Interface ─────────────────────────────────────────────────

    override fun getAssignment(groupId: String, hunterId: String): Flow<GroupAssignment?> =
        callbackFlow {
            val today = todayKey()
            val listener = assignmentsCol(groupId).document(docId(hunterId, today))
                .addSnapshotListener { snap, _ ->
                    trySend(snap?.toObject(GroupAssignment::class.java))
                }
            awaitClose { listener.remove() }
        }

    override suspend fun triggerDailyResetIfNeeded(groupId: String): Result<Boolean> {
        return try {
            val today = todayKey()
            val groupRef = db.collection("groups").document(groupId)
            var didReset = false

            db.runTransaction { tx ->
                val group = tx.get(groupRef).toObject(Group::class.java)
                    ?: throw Exception("Group not found")

                // Auto-deactivate if session expired
                if (group.gameActive && System.currentTimeMillis() > group.sessionEndTime && group.sessionEndTime != 0L) {
                    tx.update(groupRef, "gameActive", false)
                    return@runTransaction
                }

                if (!group.gameActive) return@runTransaction
                if (group.lastAssignedDateKey == today) return@runTransaction

                val members = group.members
                if (members.size < 2) return@runTransaction

                // Build shuffled target ring: each person targets the next
                val shuffled = members.shuffled()
                shuffled.forEachIndexed { index, hunterId ->
                    val targetId = shuffled[(index + 1) % shuffled.size]
                    val assignment = GroupAssignment(
                        id = docId(hunterId, today),
                        groupId = groupId,
                        hunterId = hunterId,
                        targetId = targetId,
                        dateKey = today,
                        timestamp = System.currentTimeMillis()
                    )
                    tx.set(assignmentsCol(groupId).document(assignment.id), assignment)
                }

                tx.update(groupRef, "lastAssignedDateKey", today)
                didReset = true
            }.await()

            Result.success(didReset)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun activateGroup(
        groupId: String,
        adminId: String,
        durationDays: Int
    ): Result<Unit> {
        return try {
            val groupRef = db.collection("groups").document(groupId)
            val group = groupRef.get().await().toObject(Group::class.java)
                ?: throw Exception("Group not found")
            if (group.adminId != adminId) throw Exception("Not authorized")

            val clamped = durationDays.coerceIn(1, 7)
            val endTime = System.currentTimeMillis() + clamped * 24L * 60 * 60 * 1000

            groupRef.update(
                mapOf(
                    "gameActive" to true,
                    "sessionEndTime" to endTime,
                    "sessionDurationDays" to clamped,
                    "lastAssignedDateKey" to ""   // force first assignment on next call
                )
            ).await()

            triggerDailyResetIfNeeded(groupId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deactivateGroup(groupId: String, adminId: String): Result<Unit> {
        return try {
            val groupRef = db.collection("groups").document(groupId)
            val group = groupRef.get().await().toObject(Group::class.java)
                ?: throw Exception("Group not found")
            if (group.adminId != adminId) throw Exception("Not authorized")

            groupRef.update(mapOf("gameActive" to false, "sessionEndTime" to 0L)).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun forceReassign(groupId: String, adminId: String): Result<Unit> {
        return try {
            val groupRef = db.collection("groups").document(groupId)
            val group = groupRef.get().await().toObject(Group::class.java)
                ?: throw Exception("Group not found")
            if (group.adminId != adminId) throw Exception("Not authorized")

            groupRef.update("lastAssignedDateKey", "").await()
            triggerDailyResetIfNeeded(groupId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun markEliminated(
        groupId: String,
        hunterId: String,
        targetId: String
    ): Result<Unit> {
        return try {
            val today = todayKey()
            val batch = db.batch()
            batch.update(
                assignmentsCol(groupId).document(docId(hunterId, today)),
                "isTargetEliminated", true
            )
            batch.update(
                assignmentsCol(groupId).document(docId(targetId, today)),
                "isHunterEliminated", true
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun setActiveGroup(userId: String, groupId: String?): Result<Unit> {
        return try {
            db.collection("users").document(userId)
                .update("activeGroupId", groupId)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
