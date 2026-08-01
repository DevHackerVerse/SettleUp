package com.settleup.android.data.repository

import com.settleup.android.data.local.dao.GroupDao
import com.settleup.android.data.local.dao.BalanceDao
import com.settleup.android.data.local.entity.BalanceEntity
import com.settleup.android.data.local.entity.GroupEntity
import com.settleup.android.data.remote.ApiService
import com.settleup.android.data.remote.GroupDto
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroupRepository @Inject constructor(
    private val api: ApiService,
    private val groupDao: GroupDao,
    private val balanceDao: BalanceDao
) {
    /** Emits cached groups from Room immediately; caller should also call [refresh] */
    val groups: Flow<List<GroupEntity>> = groupDao.observeAll()

    fun balances(groupId: String) = balanceDao.observeByGroup(groupId)

    /** Pull groups + balances from network and write into Room */
    suspend fun refresh() {
        runCatching {
            val dtos = api.getGroups()
            val entities = dtos.map { it.toEntity() }
            groupDao.upsertAll(entities)
        }
    }

    suspend fun refreshGroup(groupId: String) {
        runCatching {
            val dto = api.getGroup(groupId)
            groupDao.upsert(dto.toEntity())
        }
    }

    suspend fun refreshBalances(groupId: String) {
        runCatching {
            val resp = api.getBalances(groupId)
            val entities = resp.balances.map {
                BalanceEntity(groupId = groupId, userId = it.userId, name = it.name, netBalance = it.netBalance)
            }
            balanceDao.clearForGroup(groupId)
            balanceDao.upsertAll(entities)
        }
    }

    private fun GroupDto.toEntity() = GroupEntity(
        groupId = groupId,
        name = name,
        description = description,
        currency = currency,
        createdBy = createdBy,
        memberCount = members.size
    )
}
