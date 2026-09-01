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

        // 1. Authentication Verification / Recovery
        var currentUserId = SupabaseModule.client.auth.currentSessionOrNull()?.user?.id
        
        if (currentUserId == null) {
            try {
                Log.d(TAG, "SyncWorker: Session missing or expired, attempting refresh...")
                SupabaseModule.client.auth.refreshCurrentSession()
                currentUserId = SupabaseModule.client.auth.currentSessionOrNull()?.user?.id
            } catch (e: Exception) {
                Log.w(TAG, "SyncWorker: Auth refresh failed: ${e.message}")
            }
        }

        if (currentUserId != userId) {
            Log.w(TAG, "SyncWorker: User mismatch or unauthenticated. input=$userId, current=$currentUserId")
            return Result.failure()
        }

        val localRepo = SessionHistoryRepository(applicationContext, userId)
        val remoteRepo = SupabaseHistoryRepository()

        try {
            var syncFailed = false

            // 2. DOWNSTREAM SYNC: Cloud -> Local
            // Fetch and merge sessions first to maintain parent-child order
            val sessionsResult = remoteRepo.fetchSessions(userId)
            if (sessionsResult.isSuccess) {
                localRepo.mergeCloudSessions(sessionsResult.getOrThrow())
                Log.d(TAG, "Cloud sessions merged locally")
            } else {
                Log.e(TAG, "Failed to fetch cloud sessions: ${sessionsResult.exceptionOrNull()?.message}")
                syncFailed = true
            }

            val eventsResult = remoteRepo.fetchEvents(userId)
            if (eventsResult.isSuccess) {
                localRepo.mergeCloudEvents(eventsResult.getOrThrow())
                Log.d(TAG, "Cloud events merged locally")
            } else {
                Log.e(TAG, "Failed to fetch cloud events: ${eventsResult.exceptionOrNull()?.message}")
                syncFailed = true
            }

            // 3. UPSTREAM SYNC: Local -> Cloud
            // Sessions first
            val dirtySessions = localRepo.getDirtySessions()
            for (session in dirtySessions) {
                val result = remoteRepo.upsertSession(session)
                if (result.isSuccess) {
                    localRepo.markSessionSynced(session.sessionId)
                    Log.d(TAG, "Synced session: ${session.sessionId}")
                } else {
                    Log.e(TAG, "Failed to sync session ${session.sessionId}: ${result.exceptionOrNull()?.message}")
                    syncFailed = true
                }
            }

            // Events second
            val dirtyEvents = localRepo.getDirtyEvents()
            for (event in dirtyEvents) {
                val result = remoteRepo.upsertEvent(event)
                if (result.isSuccess) {
                    localRepo.markEventSynced(event.eventId)
                    Log.d(TAG, "Synced event: ${event.eventId}")
                } else {
                    Log.e(TAG, "Failed to sync event ${event.eventId}: ${result.exceptionOrNull()?.message}")
                    syncFailed = true
                }
            }

            return if (syncFailed) {
                Log.w(TAG, "SyncWorker: Partial failure during sync. Retrying later.")
                Result.retry()
            } else {
                Result.success()
            }
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
