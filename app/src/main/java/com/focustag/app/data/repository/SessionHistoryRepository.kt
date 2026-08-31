package com.focustag.app.data.repository

import android.content.Context
import android.util.Log
import com.focustag.app.data.model.FocusSessionRecord
import com.focustag.app.data.model.InterceptionEvent
import com.focustag.app.data.model.SessionStatus
import com.focustag.app.data.worker.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class SessionHistoryRepository(private val context: Context, private val userId: String) {

    companion object {
        private const val TAG = "SessionHistoryRepo"
        private const val PREFS_NAME_PREFIX = "focus_history_"
        private const val KEY_SESSIONS = "sessions"
        private const val KEY_EVENTS = "events"
        private const val MAX_SESSIONS = 100

        private val writeMutex = Mutex()
        
        // Process-lifetime scope for background persistence
        private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        
        // Shared flow for interception events to avoid disk I/O on hot path
        private val interceptionEvents = MutableSharedFlow<InterceptionEvent>(extraBufferCapacity = 100)

        private var isCollecting = false

        fun initCollector(applicationContext: Context) {
            synchronized(this) {
                if (isCollecting) return
                isCollecting = true
            }
            
            repositoryScope.launch {
                interceptionEvents.collect { event ->
                    try {
                        val repo = SessionHistoryRepository(applicationContext, event.userId)
                        repo.persistEvent(event)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in event collector: ${e.message}")
                    }
                }
            }
        }
    }

    private val prefs by lazy {
        context.getSharedPreferences("$PREFS_NAME_PREFIX$userId", Context.MODE_PRIVATE)
    }

    suspend fun createSession(record: FocusSessionRecord) = writeMutex.withLock {
        val sessions = getSessions().toMutableList()
        // Prevent duplicates
        if (sessions.any { it.sessionId == record.sessionId }) return@withLock
        
        sessions.add(0, record)
        if (sessions.size > MAX_SESSIONS) {
            sessions.removeAt(sessions.size - 1)
        }
        prefs.edit().putString(KEY_SESSIONS, Json.encodeToString(sessions)).apply()
        Log.d(TAG, "Session created in history: ${record.sessionId}")
        SyncScheduler.scheduleSync(context, userId)
    }

    suspend fun completeSession(sessionId: String, status: SessionStatus, endAt: Long) = writeMutex.withLock {
        val sessions = getSessions().map {
            if (it.sessionId == sessionId) {
                it.copy(status = status, endAt = endAt, syncDirty = true)
            } else it
        }
        prefs.edit().putString(KEY_SESSIONS, Json.encodeToString(sessions)).apply()
        Log.d(TAG, "Session completed in history: $sessionId with status $status")
        SyncScheduler.scheduleSync(context, userId)
    }

    suspend fun interruptSession(sessionId: String) = writeMutex.withLock {
        val sessions = getSessions().map {
            if (it.sessionId == sessionId && it.status == SessionStatus.IN_PROGRESS) {
                it.copy(status = SessionStatus.INTERRUPTED, syncDirty = true)
            } else it
        }
        prefs.edit().putString(KEY_SESSIONS, Json.encodeToString(sessions)).apply()
        Log.d(TAG, "Session interrupted in history: $sessionId")
        SyncScheduler.scheduleSync(context, userId)
    }

    fun getSessions(): List<FocusSessionRecord> {
        val json = prefs.getString(KEY_SESSIONS, null) ?: return emptyList()
        return try {
            Json.decodeFromString(json)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getDirtySessions(): List<FocusSessionRecord> {
        return getSessions().filter { it.syncDirty }
    }

    suspend fun markSessionSynced(sessionId: String) = writeMutex.withLock {
        val sessions = getSessions().map {
            if (it.sessionId == sessionId) {
                it.copy(syncDirty = false)
            } else it
        }
        prefs.edit().putString(KEY_SESSIONS, Json.encodeToString(sessions)).apply()
    }

    fun emitInterceptionEvent(sessionId: String, packageName: String) {
        val event = InterceptionEvent(
            eventId = UUID.randomUUID().toString(),
            sessionId = sessionId,
            userId = userId,
            packageName = packageName,
            timestamp = System.currentTimeMillis(),
            syncDirty = true
        )
        val success = interceptionEvents.tryEmit(event)
        if (!success) {
            Log.w(TAG, "Event buffer overflow, event lost for $packageName")
        }
    }

    private suspend fun persistEvent(event: InterceptionEvent) = writeMutex.withLock {
        val events = getEvents().toMutableList()
        events.add(event)
        // Simple cleanup: keep last 2000 events
        if (events.size > 2000) {
            events.removeAt(0)
        }
        prefs.edit().putString(KEY_EVENTS, Json.encodeToString(events)).apply()
        Log.d(TAG, "Event persisted: ${event.packageName} for session ${event.sessionId}")
        SyncScheduler.scheduleSync(context, userId)
    }

    fun getEvents(): List<InterceptionEvent> {
        val json = prefs.getString(KEY_EVENTS, null) ?: return emptyList()
        return try {
            Json.decodeFromString(json)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getDirtyEvents(): List<InterceptionEvent> {
        return getEvents().filter { it.syncDirty }
    }

    suspend fun markEventSynced(eventId: String) = writeMutex.withLock {
        val events = getEvents().map {
            if (it.eventId == eventId) {
                it.copy(syncDirty = false)
            } else it
        }
        prefs.edit().putString(KEY_EVENTS, Json.encodeToString(events)).apply()
    }
}
