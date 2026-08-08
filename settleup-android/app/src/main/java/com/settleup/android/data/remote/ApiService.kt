package com.settleup.android.data.remote

import retrofit2.http.*

interface ApiService {
    // Auth
    @POST("auth/register")
    suspend fun register(@Body req: RegisterRequest): AuthResponse
    @POST("auth/login")
    suspend fun login(@Body req: LoginRequest): AuthResponse
    @POST("auth/refresh")
    suspend fun refresh(@Body req: RefreshRequest): AuthResponse
    // Groups
    @GET("groups")
    suspend fun getGroups(): List<GroupDto>
    @GET("groups/{groupId}")
    suspend fun getGroup(@Path("groupId") groupId: String): GroupDto
    @POST("groups")
    suspend fun createGroup(@Body req: CreateGroupRequest): GroupDto
    @POST("groups/{groupId}/members")
    suspend fun addMember(@Path("groupId") groupId: String, @Body req: AddMemberRequest): Map<String, Boolean>
    @GET("groups/{groupId}/balances")
    suspend fun getBalances(@Path("groupId") groupId: String): BalanceResponse
    @GET("groups/{groupId}/simplified-debts")
    suspend fun getSimplifiedDebts(@Path("groupId") groupId: String): SimplifiedDebtsResponse
    // Expenses
    @POST("groups/{groupId}/expenses")
    suspend fun createExpense(@Path("groupId") groupId: String, @Body req: CreateExpenseRequest): ExpenseDto
    @PUT("expenses/{transactionId}")
    suspend fun editExpense(@Path("transactionId") transactionId: String, @Body req: CreateExpenseRequest): ExpenseDto
    @GET("groups/{groupId}/expenses")
    suspend fun getExpenses(@Path("groupId") groupId: String, @Query("page") page: Int = 0, @Query("size") size: Int = 20): PageResponse<ExpenseDto>
    @DELETE("expenses/{transactionId}")
    suspend fun reverseExpense(@Path("transactionId") transactionId: String): Map<String, String>
    // Settlements
    @POST("groups/{groupId}/settlements")
    suspend fun initiateSettlement(@Path("groupId") groupId: String, @Body req: SettlementRequest): SettlementDto
    @GET("settlements/{settlementId}")
    suspend fun getSettlement(@Path("settlementId") settlementId: String): SettlementDto
    // Notifications
    @GET("notifications")
    suspend fun getNotifications(@Query("unreadOnly") unreadOnly: Boolean = false): PageResponse<NotificationDto>
    @POST("notifications/{id}/read")
    suspend fun markRead(@Path("id") id: Long): Map<String, Boolean>
}
