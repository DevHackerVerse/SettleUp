package com.settleup.android.data.local.entity

import androidx.room.Entity

@Entity(tableName = "balances", primaryKeys = ["groupId", "userId"])
data class BalanceEntity(
    val groupId: String,
    val userId: String,
    val name: String,
    val netBalance: String,
    val cachedAt: Long = System.currentTimeMillis()
)
