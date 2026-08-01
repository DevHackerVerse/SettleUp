package com.settleup.android.data.local

import android.content.Context
import androidx.room.Room
import com.settleup.android.data.local.dao.BalanceDao
import com.settleup.android.data.local.dao.ExpenseDao
import com.settleup.android.data.local.dao.GroupDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SettleUpDatabase =
        Room.databaseBuilder(context, SettleUpDatabase::class.java, "settleup.db")
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
            .build()

    @Provides fun provideGroupDao(db: SettleUpDatabase): GroupDao = db.groupDao()
    @Provides fun provideExpenseDao(db: SettleUpDatabase): ExpenseDao = db.expenseDao()
    @Provides fun provideBalanceDao(db: SettleUpDatabase): BalanceDao = db.balanceDao()
}
