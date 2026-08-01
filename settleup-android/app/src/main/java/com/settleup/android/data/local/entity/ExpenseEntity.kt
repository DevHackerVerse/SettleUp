package com.settleup.android.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** syncStatus values: PENDING | SYNCED | FAILED */
@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    /** null until this expense has been successfully synced to the backend */
    val remoteId: String? = null,
    val groupId: String,
    val description: String,
    val totalAmount: String,
    val paidBy: String,
    val splitType: String,
    val splitsJson: String? = null,   // JSON-serialised List<SplitEntry>, null for EQUAL
    val currency: String = "INR",
    val isReversal: Boolean = false,
    val syncStatus: String = SyncStatus.PENDING,  // "PENDING" | "SYNCED" | "FAILED"
    val createdAt: Long = System.currentTimeMillis()
)

object SyncStatus {
    const val PENDING = "PENDING"
    const val SYNCED  = "SYNCED"
    const val FAILED  = "FAILED"
}
