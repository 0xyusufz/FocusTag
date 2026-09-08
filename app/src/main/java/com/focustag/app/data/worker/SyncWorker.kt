package com.focustag.app.data.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.focustag.app.data.repository.SessionHistoryRepository
import com.focustag.app.data.repository.SupabaseHistoryRepository
import com.focustag.app.data.repository.SyncError
import com.focustag.app.data.supabase.SupabaseModule
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

class SyncWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    @OptIn(kotlin.time.ExperimentalTime::class)
    override suspend fun doWork(): Result {
        val userId = inputData.getString(KEY_USER_ID) ?: return Result.failure()
        
        Log.d(TAG, "SyncWorker started.")

        // 0. Initialize client with persistence
        SupabaseModule.initialize(applicationContext)

        // 1. STATED-BASED AUTH WAIT: Wait for definitive Authenticated or NotAuthenticated state
        val auth = SupabaseModule.client.auth
        Log.d(TAG, "SyncWorker: Checking auth status...")
        
        val status = try {
            withTimeout(30000) { // 30s safety backstop
                auth.sessionStatus.first { 
                    it is SessionStatus.Authenticated || it is SessionStatus.NotAuthenticated 
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "SyncWorker: Timeout waiting for definitive auth state.")
            // Transient timeout (e.g. cold network) -> Retry
            return Result.retry()
        }

        // 2. AUTH GATE: Definitive outcome handling
        when (status) {
            is SessionStatus.NotAuthenticated -> {
                Log.w(TAG, "SyncWorker: User not authenticated. Aborting sync.")
                return Result.failure()
            }
            is SessionStatus.Authenticated -> {
                if (status.session.user?.id != userId) {
                    Log.w(TAG, "SyncWorker: User session mismatch. Aborting sync.")
                    return Result.failure()
                }
                
                // LAZY REFRESH: Only if stable and near expiry (60s buffer)
                val expiresAtMillis = status.session.expiresAt.toEpochMilliseconds()
                val nowMillis = System.currentTimeMillis()
                if (expiresAtMillis < nowMillis + 60000) {
                    try {
                        Log.d(TAG, "SyncWorker: Token near expiry, refreshing...")
                        auth.refreshCurrentSession()
                    } catch (e: Exception) {
                        Log.w(TAG, "SyncWorker: Auth refresh failed (transient): ${e.message}")
                        // HALT: Do not proceed to Postgrest if required refresh fails
                        return Result.retry()
                    }
                }
            }
            else -> {
                // Should be unreachable due to 'first' filter above, but be safe
                Log.w(TAG, "SyncWorker: Unexpected auth status: $status")
                return Result.retry()
            }
        }

        val localRepo = SessionHistoryRepository(applicationContext, userId)
        val remoteRepo = SupabaseHistoryRepository()

        try {
            var syncFailed = false
            var authRecovered = false

            // 3. DOWNSTREAM SYNC: Cloud -> Local
            val sessionsResult = remoteRepo.fetchSessions(userId)
            if (sessionsResult.isSuccess) {
                localRepo.mergeCloudSessions(sessionsResult.getOrThrow())
            } else {
                val error = sessionsResult.exceptionOrNull()
                if (error is SyncError.Retryable && error.message?.contains("Unauthorized") == true) {
                    Log.d(TAG, "SyncWorker: Postgrest 401 on sessions, attempting recovery...")
                    try {
                        auth.refreshCurrentSession()
                        authRecovered = true
                        val retryResult = remoteRepo.fetchSessions(userId)
                        if (retryResult.isSuccess) {
                            localRepo.mergeCloudSessions(retryResult.getOrThrow())
                        } else {
                            syncFailed = true
                        }
                    } catch (ex: Exception) {
                        Log.w(TAG, "SyncWorker: Recovery refresh failed: ${ex.message}")
                        return Result.retry() // Abort and retry worker
                    }
                } else {
                    syncFailed = true
                }
            }

            val eventsResult = remoteRepo.fetchEvents(userId)
            if (eventsResult.isSuccess) {
                localRepo.mergeCloudEvents(eventsResult.getOrThrow())
            } else {
                val error = eventsResult.exceptionOrNull()
                if (error is SyncError.Retryable && error.message?.contains("Unauthorized") == true && !authRecovered) {
                    Log.d(TAG, "SyncWorker: Postgrest 401 on events, attempting recovery...")
                    try {
                        auth.refreshCurrentSession()
                        authRecovered = true
                        val retryResult = remoteRepo.fetchEvents(userId)
                        if (retryResult.isSuccess) {
                            localRepo.mergeCloudEvents(retryResult.getOrThrow())
                        } else {
                            syncFailed = true
                        }
                    } catch (ex: Exception) {
                        Log.w(TAG, "SyncWorker: Recovery refresh failed: ${ex.message}")
                        return Result.retry()
                    }
                } else {
                    syncFailed = true
                }
            }

            // 4. UPSTREAM SYNC: Local -> Cloud
            // Sessions first
            val dirtySessions = localRepo.getDirtySessions()
            for (sessionRecord in dirtySessions) {
                var result = remoteRepo.upsertSession(sessionRecord)
                
                if (result.isFailure && result.exceptionOrNull() is SyncError.Retryable && 
                    result.exceptionOrNull()?.message?.contains("Unauthorized") == true && !authRecovered) {
                    try {
                        auth.refreshCurrentSession()
                        authRecovered = true
                        result = remoteRepo.upsertSession(sessionRecord)
                    } catch (e: Exception) {
                        Log.w(TAG, "SyncWorker: Recovery refresh failed: ${e.message}")
                        return Result.retry()
                    }
                }

                when {
                    result.isSuccess -> localRepo.markSessionSynced(sessionRecord)
                    else -> {
                        val error = result.exceptionOrNull() as? SyncError
                        if (error is SyncError.Permanent) {
                            localRepo.markSessionFailedPermanently(sessionRecord)
                        } else {
                            syncFailed = true
                        }
                    }
                }
            }

            // Events second
            val dirtyEvents = localRepo.getDirtyEvents()
            for (event in dirtyEvents) {
                var result = remoteRepo.insertEvent(event)
                
                if (result.isFailure && result.exceptionOrNull() is SyncError.Retryable && 
                    result.exceptionOrNull()?.message?.contains("Unauthorized") == true && !authRecovered) {
                    try {
                        auth.refreshCurrentSession()
                        authRecovered = true
                        result = remoteRepo.insertEvent(event)
                    } catch (e: Exception) {
                        Log.w(TAG, "SyncWorker: Recovery refresh failed: ${e.message}")
                        return Result.retry()
                    }
                }

                when {
                    result.isSuccess -> localRepo.markEventSynced(event)
                    else -> {
                        val error = result.exceptionOrNull() as? SyncError
                        if (error is SyncError.Permanent) {
                            localRepo.markEventFailedPermanently(event)
                        } else {
                            syncFailed = true
                        }
                    }
                }
            }

            // 5. FINAL CHECK: Prevent KEEP missed-sync race
            // Re-read disk queue to see if new events arrived while worker was processing
            val finalDirtySessions = localRepo.getDirtySessions().isNotEmpty()
            val finalDirtyEvents = localRepo.getDirtyEvents().isNotEmpty()
            if ((finalDirtySessions || finalDirtyEvents) && !syncFailed) {
                Log.d(TAG, "SyncWorker: New dirty records found after run. Forcing retry loop.")
                return Result.retry()
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
