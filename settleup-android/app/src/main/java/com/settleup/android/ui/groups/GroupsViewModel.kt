package com.settleup.android.ui.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.settleup.android.data.local.entity.ExpenseEntity
import com.settleup.android.data.local.entity.GroupEntity
import com.settleup.android.data.local.entity.BalanceEntity
import com.settleup.android.data.remote.*
import com.settleup.android.data.repository.ExpenseRepository
import com.settleup.android.data.repository.GroupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GroupsViewModel @Inject constructor(
    private val api: ApiService,
    private val groupRepository: GroupRepository,
    private val expenseRepository: ExpenseRepository
) : ViewModel() {

    // ── Groups (Room → Flow, auto-updates when SyncWorker writes) ────

    val groups: StateFlow<List<GroupEntity>> = groupRepository.groups
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── Group detail (remote-only, small payload) ───────────────────

    private val _groupDetail = MutableStateFlow<GroupDto?>(null)
    val groupDetail = _groupDetail.asStateFlow()

    // ── Expenses (Room Flow — shows PENDING immediately) ────────────

    private val _groupId = MutableStateFlow<String?>(null)

    val expenses: StateFlow<List<ExpenseEntity>> = _groupId
        .filterNotNull()
        .flatMapLatest { expenseRepository.getExpenses(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── Balances (Room Flow) ─────────────────────────────────────────

    val balances: StateFlow<List<BalanceEntity>> = _groupId
        .filterNotNull()
        .flatMapLatest { groupRepository.balances(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── Simplified debts (remote only — lightweight calc) ───────────

    private val _debts = MutableStateFlow<SimplifiedDebtsResponse?>(null)
    val debts = _debts.asStateFlow()

    // ── Settlement (Phase 3) ─────────────────────────────────────────

    private val _settlement = MutableStateFlow<SettlementDto?>(null)
    val settlement = _settlement.asStateFlow()

    private var pollJob: Job? = null

    // ── Actions ──────────────────────────────────────────────────────

    fun loadGroups() = viewModelScope.launch {
        groupRepository.refresh()  // updates Room → Flow auto-notifies UI
    }

    fun loadGroup(id: String) = viewModelScope.launch {
        _groupId.value = id
        runCatching { _groupDetail.value = api.getGroup(id) }.onFailure { it.printStackTrace() }
    }

    fun loadBalances(id: String) = viewModelScope.launch {
        groupRepository.refreshBalances(id)  // Room Flow auto-notifies
    }

    fun loadDebts(id: String) = viewModelScope.launch {
        runCatching { _debts.value = api.getSimplifiedDebts(id) }.onFailure { it.printStackTrace() }
    }

    fun createGroup(req: CreateGroupRequest) = viewModelScope.launch {
        runCatching {
            api.createGroup(req)
            groupRepository.refresh()
        }.onFailure { it.printStackTrace() }
    }

    /**
     * Saves expense locally first (shows ⏳ immediately), then WorkManager
     * syncs to backend when network is available.
     */
    fun addExpense(groupId: String, req: CreateExpenseRequest) = viewModelScope.launch {
        runCatching {
            expenseRepository.addExpense(
                groupId = groupId,
                description = req.description,
                totalAmount = req.totalAmount,
                paidBy = req.paidBy,
                splitType = req.splitType,
                splits = req.splits,
                currency = _groupDetail.value?.currency ?: "INR"
            )
            // Also refresh balances from remote if online
            groupRepository.refreshBalances(groupId)
        }.onFailure { it.printStackTrace() }
    }

    fun reverseExpense(transactionId: String) = viewModelScope.launch {
        runCatching {
            api.reverseExpense(transactionId)
            _groupId.value?.let { groupId ->
                expenseRepository.refreshFromRemote(groupId)
                groupRepository.refreshBalances(groupId)
            }
        }.onFailure { it.printStackTrace() }
    }

    fun initiateSettlement(groupId: String, req: SettlementRequest) = viewModelScope.launch {
        runCatching {
            val dto = api.initiateSettlement(groupId, req)
            _settlement.value = dto
            pollSettlement(dto.settlementId)
        }.onFailure { it.printStackTrace() }
    }

    fun pollSettlement(settlementId: String) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            repeat(30) {
                delay(2_000)
                runCatching {
                    val dto = api.getSettlement(settlementId)
                    _settlement.value = dto
                    if (dto.status == "COMPLETED" || dto.status == "FAILED") {
                        _groupId.value?.let { groupId -> groupRepository.refreshBalances(groupId) }
                        return@launch
                    }
                }.onFailure { it.printStackTrace() }
            }
        }
    }

    fun resetSettlement() {
        pollJob?.cancel()
        _settlement.value = null
    }

    override fun onCleared() {
        super.onCleared()
        pollJob?.cancel()
    }
}
