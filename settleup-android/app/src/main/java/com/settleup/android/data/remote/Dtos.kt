package com.settleup.android.data.remote

import com.squareup.moshi.Json

// Using Moshi kotlin-reflect adapter — no @JsonClass annotation needed

data class RegisterRequest(val name: String, val email: String, val password: String)
data class LoginRequest(val email: String, val password: String)
data class RefreshRequest(val refreshToken: String)
data class AuthResponse(val userId: String, val token: String, val refreshToken: String)
data class GroupDto(
    val groupId: String,
    val name: String,
    val description: String?,
    val currency: String,
    val createdBy: String,
    val members: List<MemberDto> = emptyList()
)
data class MemberDto(val userId: String, val name: String, val email: String, val role: String)
data class CreateGroupRequest(val name: String, val description: String? = null, val currency: String = "INR", val budgetAmount: Double? = null)
data class AddMemberRequest(val email: String)
data class BalanceEntry(@Json(name = "userId") val userId: String, val name: String, val netBalance: String)
data class BalanceResponse(val balances: List<BalanceEntry>)
data class SettlementSuggestion(val fromUserId: String, val toUserId: String, val fromName: String, val toName: String, val amount: String)
data class SimplifiedDebtsResponse(val settlementsSuggested: List<SettlementSuggestion>)
data class SplitEntry(val userId: String, val value: String)
data class CreateExpenseRequest(val description: String, val totalAmount: String, val paidBy: String, val splitType: String, val splits: List<SplitEntry>? = null)
data class ExpenseDto(
    val transactionId: String,
    val groupId: String,
    val paidBy: String,
    val description: String,
    val totalAmount: String,
    val currency: String,
    val splitType: String,
    val isReversal: Boolean = false,
    val createdAt: String? = null
)
data class PageResponse<T>(val content: List<T>, val totalElements: Long, val totalPages: Int, val number: Int)
data class SettlementRequest(val payeeId: String, val amount: String, val idempotencyKey: String)
data class SettlementDto(val settlementId: String, val status: String, val mockUpiRef: String? = null, val amount: String, val payerId: String, val payeeId: String)
data class NotificationDto(val id: Long, val type: String, val read: Boolean)
