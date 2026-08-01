package com.settleup.android.data.local.dao

import androidx.room.*
import com.settleup.android.data.local.entity.BalanceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BalanceDao {
    @Query("SELECT * FROM balances WHERE groupId = :groupId")
    fun observeByGroup(groupId: String): Flow<List<BalanceEntity>>

    @Upsert
    suspend fun upsertAll(balances: List<BalanceEntity>)

    @Query("DELETE FROM balances WHERE groupId = :groupId")
    suspend fun clearForGroup(groupId: String)
}
