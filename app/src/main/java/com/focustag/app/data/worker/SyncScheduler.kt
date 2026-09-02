package com.focustag.app.data.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object SyncScheduler {

    fun scheduleSync(context: Context, userId: String) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val inputData = Data.Builder()
            .putString(SyncWorker.KEY_USER_ID, userId)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                30,
                TimeUnit.SECONDS
            )
            .addTag(userId) // User-scoped tag for cancellation
            .setInputData(inputData)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "sync_$userId",
            ExistingWorkPolicy.KEEP, // KEEP to prevent worker churn and refresh storms
            syncRequest
        )
    }

    fun cancelSync(context: Context, userId: String) {
        WorkManager.getInstance(context).cancelAllWorkByTag(userId)
        WorkManager.getInstance(context).cancelUniqueWork("sync_$userId")
    }
}
