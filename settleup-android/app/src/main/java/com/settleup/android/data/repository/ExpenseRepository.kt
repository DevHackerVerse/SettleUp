package com.settleup.android.data.repository

import android.content.Context
import androidx.work.*
import com.settleup.android.data.local.dao.ExpenseDao
import com.settleup.android.data.local.entity.ExpenseEntity
import com.settleup.android.data.local.entity.SyncStatus
import com.settleup.android.data.remote.ApiService
import com.settleup.android.data.remote.CreateExpenseRequest
import com.settleup.android.data.remote.SplitEntry
import com.settleup.android.worker.SyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExpenseRepository @Inject constructor(
    private val api: ApiService,
    private val expenseDao: ExpenseDao,
    @ApplicationContext private val context: Context
) {
    /** Live stream of expenses for a group from Room (includes pending ones) */
    fun getExpenses(groupId: String): Flow<List<ExpenseEntity>> =
        expenseDao.observeByGroup(groupId)

    /**
     * Save expense locally (PENDING) and enqueue a WorkManager sync.
     * The UI badge shows ⏳ until SyncWorker marks it SYNCED.
     */
    suspend fun addExpense(
        groupId: String,
        description: String,
        totalAmount: String,
        paidBy: String,
        splitType: String,
        splits: List<SplitEntry>? = null,
        currency: String = "INR"
    ) {
        val splitsJson = splits?.joinToString(",", "[", "]") {
            """{"userId":"${it.userId}","value":"${it.value}"}"""
        }
        val entity = ExpenseEntity(
            groupId = groupId,
            description = description,
            totalAmount = totalAmount,
            paidBy = paidBy,
            splitType = splitType,
            splitsJson = splitsJson,
            currency = currency,
            syncStatus = SyncStatus.PENDING
        )
        expenseDao.insert(entity)
        enqueueSyncWorker()
    }

    /**
     * Pull remote expenses into Room (overwrites synced rows for the group).
     * Called by SyncWorker after pushing pending expenses.
     */
    suspend fun refreshFromRemote(groupId: String) {
        runCatching {
            val page = api.getExpenses(groupId, 0, 50)
            val entities = page.content.map { dto ->
                val ledgerJson = dto.ledgerEntries?.joinToString(",", "[", "]") {
                    """{"id":${it.id},"userId":"${it.userId}","entryType":"${it.entryType}","amount":"${it.amount}"}"""
                }
                ExpenseEntity(
                    remoteId = dto.transactionId,
                    groupId = dto.groupId,
                    description = dto.description,
                    totalAmount = dto.totalAmount,
                    paidBy = dto.paidBy,
                    splitType = dto.splitType,
                    currency = dto.currency,
                    isReversal = dto.isReversal,
                    syncStatus = SyncStatus.SYNCED,
                    ledgerEntriesJson = ledgerJson,
                    createdAt = dto.createdAt?.let { parseIso(it) } ?: System.currentTimeMillis()
                )
            }
            expenseDao.clearSyncedForGroup(groupId)
            expenseDao.upsertAll(entities)
        }
    }

    /** Push all PENDING expenses to the backend and mark SYNCED */
    suspend fun syncPending() {
        val pending = expenseDao.getPending()
        for (entity in pending) {
            runCatching {
                val splits = entity.splitsJson?.let { parseSimpleSplits(it) }
                val req = CreateExpenseRequest(
                    description = entity.description,
                    totalAmount = entity.totalAmount,
                    paidBy = entity.paidBy,
                    splitType = entity.splitType,
                    splits = splits
                )
                val dto = api.createExpense(entity.groupId, req)
                expenseDao.markSynced(entity.localId, dto.transactionId)
            }.onFailure {
                expenseDao.markFailed(entity.localId)
            }
        }
    }

    private fun parseSimpleSplits(json: String): List<SplitEntry> {
        // Simple regex-free parse of our own generated JSON
        return json.trim('[', ']').split("},{").mapNotNull { chunk ->
            val uid = Regex(""""userId":"([^"]+)"""").find(chunk)?.groupValues?.get(1)
            val value = Regex(""""value":"([^"]+)"""").find(chunk)?.groupValues?.get(1)
            if (uid != null && value != null) SplitEntry(uid, value) else null
        }
    }

    fun enqueueSyncWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, androidx.work.WorkRequest.MIN_BACKOFF_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("settleup_sync", ExistingWorkPolicy.KEEP, request)
    }

    private fun parseIso(dateStr: String): Long {
        return runCatching {
            java.time.Instant.parse(if (dateStr.endsWith("Z")) dateStr else "${dateStr}Z").toEpochMilli()
        }.getOrDefault(System.currentTimeMillis())
    }
}
