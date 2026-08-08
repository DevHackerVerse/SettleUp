package com.settleup.android.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.settleup.android.data.local.dao.BalanceDao
import com.settleup.android.data.local.dao.ExpenseDao
import com.settleup.android.data.local.dao.GroupDao
import com.settleup.android.data.local.entity.BalanceEntity
import com.settleup.android.data.local.entity.ExpenseEntity
import com.settleup.android.data.local.entity.GroupEntity

@Database(
    entities = [GroupEntity::class, ExpenseEntity::class, BalanceEntity::class],
    version = 2,
    exportSchema = false
)
abstract class SettleUpDatabase : RoomDatabase() {
    abstract fun groupDao(): GroupDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun balanceDao(): BalanceDao
}
