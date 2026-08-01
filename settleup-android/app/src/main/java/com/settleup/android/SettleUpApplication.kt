package com.settleup.android

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.*
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SettleUpApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Enqueue a connectivity-gated SyncWorker on every app start
        // so any PENDING expenses created while offline are synced on next launch
        scheduleSyncOnConnectivity()
    }

    private fun scheduleSyncOnConnectivity() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<com.settleup.android.worker.SyncWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(this)
            .enqueueUniqueWork(
                "settleup_startup_sync",
                ExistingWorkPolicy.KEEP,
                request
            )
    }
}
