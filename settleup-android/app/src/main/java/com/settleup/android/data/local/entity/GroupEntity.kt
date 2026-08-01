package com.settleup.android.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey val groupId: String,
    val name: String,
    val description: String?,
    val currency: String,
    val createdBy: String,
    val memberCount: Int = 0,
    val memberJson: String = "[]",          // JSON-serialised member list
    val lastSyncedAt: Long = System.currentTimeMillis()
)
