package com.settleup.android.data.local.dao

import androidx.room.*
import com.settleup.android.data.local.entity.GroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    @Query("SELECT * FROM groups ORDER BY name ASC")
    fun observeAll(): Flow<List<GroupEntity>>

    @Query("SELECT groupId FROM groups")
    suspend fun getAllIds(): List<String>

    @Query("SELECT * FROM groups WHERE groupId = :groupId LIMIT 1")
    suspend fun getById(groupId: String): GroupEntity?

    @Upsert
    suspend fun upsertAll(groups: List<GroupEntity>)

    @Upsert
    suspend fun upsert(group: GroupEntity)

    @Query("DELETE FROM groups")
    suspend fun clearAll()
}
