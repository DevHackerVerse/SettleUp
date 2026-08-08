package com.settleup.android.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.settleup.android.data.local.dao.GroupDao
import com.settleup.android.data.repository.ExpenseRepository
import com.settleup.android.data.repository.GroupRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * SyncWorker — runs when CONNECTED.
 *
 * Flow:
 *  1. Push all locally-created PENDING expenses to the backend (marking them SYNCED on success).
 *  2. Refresh groups list from remote into Room.
 *  3. For each known group, refresh remote expenses + balances into Room.
 *
 * Conflict-resolution (spec §9 Phase 4):
 *  - Expense creation is additive — each local expense is a new unique transaction.
 *  - Duplicate-safety: getPending() only returns rows with remoteId == null, so
 *    already-synced rows are never POSTed twice.
 *  - Last-write-wins: refreshFromRemote() clears SYNCED rows and re-inserts from server,
 *    so the server is always source-of-truth for confirmed expenses.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val expenseRepository: ExpenseRepository,
    private val groupRepository: GroupRepository,
    private val groupDao: GroupDao
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            // Step 1 — Push PENDING expenses to backend
            expenseRepository.syncPending()

            // Step 2 — Refresh group list
            groupRepository.refresh()

            // Step 3 — For every cached group, pull latest expenses + balances
            val groups = groupDao.observeAll().let { flow ->
                // Read current snapshot (non-blocking)
                val result = mutableListOf<com.settleup.android.data.local.entity.GroupEntity>()
                // Use direct DB query instead of Flow collect to avoid suspension issues
                result
            }

            // Pull all groups from DB directly for sync
            val allGroups = groupDao.getAllIds()
            for (groupId in allGroups) {
                expenseRepository.refreshFromRemote(groupId)
                groupRepository.refreshBalances(groupId)
            }

            Result.success()
        }.getOrElse {
            it.printStackTrace()
            // Retry on failure — WorkManager will honour exponential backoff
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
