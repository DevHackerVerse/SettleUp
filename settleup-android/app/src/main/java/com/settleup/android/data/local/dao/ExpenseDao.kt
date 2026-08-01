package com.settleup.android.data.local.dao

import androidx.room.*
import com.settleup.android.data.local.entity.ExpenseEntity
import com.settleup.android.data.local.entity.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    /** Live stream of all expenses for a group, newest first */
    @Query("SELECT * FROM expenses WHERE groupId = :groupId ORDER BY createdAt DESC")
    fun observeByGroup(groupId: String): Flow<List<ExpenseEntity>>

    /** All rows that haven't been pushed to the backend yet */
    @Query("SELECT * FROM expenses WHERE syncStatus = '${SyncStatus.PENDING}' AND remoteId IS NULL")
    suspend fun getPending(): List<ExpenseEntity>

    /** Insert a new locally-created expense (PENDING by default) */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(expense: ExpenseEntity): Long

    /** Mark as SYNCED and store the backend-assigned remoteId */
    @Query("UPDATE expenses SET syncStatus = '${SyncStatus.SYNCED}', remoteId = :remoteId WHERE localId = :localId")
    suspend fun markSynced(localId: Long, remoteId: String)

    /** Mark as FAILED so the user knows the sync didn't complete */
    @Query("UPDATE expenses SET syncStatus = '${SyncStatus.FAILED}' WHERE localId = :localId")
    suspend fun markFailed(localId: Long)

    /** Upsert an expense coming from the server (always SYNCED) */
    @Upsert
    suspend fun upsertAll(expenses: List<ExpenseEntity>)

    @Query("DELETE FROM expenses WHERE groupId = :groupId AND syncStatus = '${SyncStatus.SYNCED}'")
    suspend fun clearSyncedForGroup(groupId: String)
}
