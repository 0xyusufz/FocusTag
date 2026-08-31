package com.focustag.app.data.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.focustag.app.data.repository.SessionHistoryRepository
import com.focustag.app.data.repository.SupabaseHistoryRepository
import com.focustag.app.data.supabase.SupabaseModule
import io.github.jan.supabase.auth.auth

class SyncWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val userId = inputData.getString(KEY_USER_ID) ?: return Result.failure()
        
        Log.d(TAG, "SyncWorker started for user: $userId")

        // Verify current user
        val currentUserId = SupabaseModule.client.auth.currentSessionOrNull()?.user?.id
        if (currentUserId != userId) {
            Log.w(TAG, "SyncWorker: User mismatch or not authenticated. input=$userId, current=$currentUserId")
            return Result.failure()
        }

        val localRepo = SessionHistoryRepository(applicationContext, userId)
        val remoteRepo = SupabaseHistoryRepository()

        try {
            // 1. Sync Sessions first (Parent)
            val dirtySessions = localRepo.getDirtySessions()
            for (session in dirtySessions) {
                val result = remoteRepo.upsertSession(session)
                if (result.isSuccess) {
                    localRepo.markSessionSynced(session.sessionId)
                    Log.d(TAG, "Synced session: ${session.sessionId}")
                } else {
                    val error = result.exceptionOrNull()?.message
                    Log.e(TAG, "Failed to sync session ${session.sessionId}: $error")
                    // If it's a persistent error, we might want to return failure or retry
                }
            }

            // 2. Sync Events (Children)
            val dirtyEvents = localRepo.getDirtyEvents()
            for (event in dirtyEvents) {
                val result = remoteRepo.upsertEvent(event)
                if (result.isSuccess) {
                    localRepo.markEventSynced(event.eventId)
                    Log.d(TAG, "Synced event: ${event.eventId}")
                } else {
                    val error = result.exceptionOrNull()?.message
                    Log.e(TAG, "Failed to sync event ${event.eventId}: $error")
                }
            }

            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in SyncWorker: ${e.message}", e)
            return Result.retry()
        }
    }

    companion object {
        const val TAG = "SyncWorker"
        const val KEY_USER_ID = "user_id"
    }
}
