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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

open class SessionHistoryRepository(private val context: Context?, private val userId: String) {

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

        private val sessionFlows = mutableMapOf<String, MutableStateFlow<List<FocusSessionRecord>>>()
        private val eventFlows = mutableMapOf<String, MutableStateFlow<List<InterceptionEvent>>>()

        private fun getSessionFlow(userId: String) = synchronized(sessionFlows) {
            sessionFlows.getOrPut(userId) { MutableStateFlow(emptyList()) }
        }

        private fun getEventFlow(userId: String) = synchronized(eventFlows) {
            eventFlows.getOrPut(userId) { MutableStateFlow(emptyList()) }
        }

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
        context!!.getSharedPreferences("$PREFS_NAME_PREFIX$userId", Context.MODE_PRIVATE)
    }

    private val _sessions = getSessionFlow(userId)
    val sessions: StateFlow<List<FocusSessionRecord>> = _sessions.asStateFlow()

    private val _events = getEventFlow(userId)
    val events: StateFlow<List<InterceptionEvent>> = _events.asStateFlow()

    init {
        updateFlows()
    }

    private fun updateFlows() {
        _sessions.value = getSessions()
        _events.value = getEvents()
    }

    suspend fun createSession(record: FocusSessionRecord) = writeMutex.withLock {
        val sessions = getSessions().toMutableList()
        // Prevent duplicates
        if (sessions.any { it.sessionId == record.sessionId }) return@withLock
        
        sessions.add(0, record.copy(syncFailedPermanently = false))
        if (sessions.size > MAX_SESSIONS) {
            sessions.removeAt(sessions.size - 1)
        }
        prefs.edit().putString(KEY_SESSIONS, Json.encodeToString(sessions)).apply()
        Log.d(TAG, "Session created in history: ${record.sessionId}")
        updateFlows()
        SyncScheduler.scheduleSync(context!!, userId)
    }

    suspend fun completeSession(sessionId: String, status: SessionStatus, endAt: Long) = writeMutex.withLock {
        val sessions = getSessions().map {
            if (it.sessionId == sessionId) {
                it.copy(status = status, endAt = endAt, syncDirty = true, syncFailedPermanently = false)
            } else it
        }
        prefs.edit().putString(KEY_SESSIONS, Json.encodeToString(sessions)).apply()
        Log.d(TAG, "Session completed in history: $sessionId with status $status")
        updateFlows()
        SyncScheduler.scheduleSync(context!!, userId)
    }

    suspend fun interruptSession(sessionId: String, endAt: Long) = writeMutex.withLock {
        val sessions = getSessions().map {
            if (it.sessionId == sessionId && it.status == SessionStatus.IN_PROGRESS) {
                it.copy(status = SessionStatus.INTERRUPTED, endAt = endAt, syncDirty = true, syncFailedPermanently = false)
            } else it
        }
        prefs.edit().putString(KEY_SESSIONS, Json.encodeToString(sessions)).apply()
        Log.d(TAG, "Session interrupted in history: $sessionId")
        updateFlows()
        SyncScheduler.scheduleSync(context!!, userId)
    }

    open fun getSessions(): List<FocusSessionRecord> {
        val json = prefs.getString(KEY_SESSIONS, null) ?: return emptyList()
        return try {
            Json.decodeFromString(json)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getDirtySessions(): List<FocusSessionRecord> {
        // Inclusion of syncFailedPermanently allows records previously killed by the classification bug 
        // to be re-evaluated by the improved classifier.
        return getSessions().filter { it.syncDirty }
    }

    suspend fun markSessionSynced(record: FocusSessionRecord) = writeMutex.withLock {
        val sessions = getSessions().map { local ->
            if (local.sessionId == record.sessionId) {
                // Only clear dirty if the local data exactly matches what we just synced
                // (ignoring dirty flags)
                if (local.copy(syncDirty = true, syncFailedPermanently = false) == 
                    record.copy(syncDirty = true, syncFailedPermanently = false)) {
                    local.copy(syncDirty = false, syncFailedPermanently = false)
                } else local
            } else local
        }
        prefs.edit().putString(KEY_SESSIONS, Json.encodeToString(sessions)).apply()
        updateFlows()
    }

    suspend fun markSessionFailedPermanently(record: FocusSessionRecord) = writeMutex.withLock {
        val sessions = getSessions().map { local ->
            if (local.sessionId == record.sessionId) {
                // Value-aware: only mark failed if local is still the version that failed
                if (local.copy(syncDirty = true, syncFailedPermanently = false) == 
                    record.copy(syncDirty = true, syncFailedPermanently = false)) {
                    local.copy(syncDirty = true, syncFailedPermanently = true)
                } else local
            } else local
        }
        prefs.edit().putString(KEY_SESSIONS, Json.encodeToString(sessions)).apply()
        updateFlows()
    }

    fun emitInterceptionEvent(sessionId: String, packageName: String) {
        val event = InterceptionEvent(
            eventId = UUID.randomUUID().toString(),
            sessionId = sessionId,
            userId = userId,
            packageName = packageName,
            timestamp = System.currentTimeMillis(),
            syncDirty = true,
            syncFailedPermanently = false
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
        updateFlows()
        SyncScheduler.scheduleSync(context!!, userId)
    }

    open fun getEvents(): List<InterceptionEvent> {
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

    suspend fun markEventSynced(event: InterceptionEvent) = writeMutex.withLock {
        val events = getEvents().map { local ->
            if (local.eventId == event.eventId) {
                if (local.copy(syncDirty = true, syncFailedPermanently = false) == 
                    event.copy(syncDirty = true, syncFailedPermanently = false)) {
                    local.copy(syncDirty = false, syncFailedPermanently = false)
                } else local
            } else local
        }
        prefs.edit().putString(KEY_EVENTS, Json.encodeToString(events)).apply()
        updateFlows()
    }

    suspend fun markEventFailedPermanently(event: InterceptionEvent) = writeMutex.withLock {
        val events = getEvents().map { local ->
            if (local.eventId == event.eventId) {
                if (local.copy(syncDirty = true, syncFailedPermanently = false) == 
                    event.copy(syncDirty = true, syncFailedPermanently = false)) {
                    local.copy(syncDirty = true, syncFailedPermanently = true)
                } else local
            } else local
        }
        prefs.edit().putString(KEY_EVENTS, Json.encodeToString(events)).apply()
        updateFlows()
    }

    suspend fun mergeCloudSessions(cloudSessions: List<FocusSessionRecord>) = writeMutex.withLock {
        val localSessions = getSessions().toMutableList()
        var changed = false
        
        cloudSessions.forEach { cloud ->
            val localIndex = localSessions.indexOfFirst { it.sessionId == cloud.sessionId }
            if (localIndex == -1) {
                localSessions.add(cloud)
                changed = true
            } else {
                val local = localSessions[localIndex]
                if (!local.syncDirty && local != cloud) {
                    localSessions[localIndex] = cloud
                    changed = true
                }
            }
        }
        
        if (changed) {
            localSessions.sortByDescending { it.startAt }
            val finalSessions = if (localSessions.size > MAX_SESSIONS) localSessions.take(MAX_SESSIONS) else localSessions
            prefs.edit().putString(KEY_SESSIONS, Json.encodeToString(finalSessions)).apply()
            updateFlows()
        }
    }

    suspend fun mergeCloudEvents(cloudEvents: List<InterceptionEvent>) = writeMutex.withLock {
        val localEvents = getEvents().toMutableList()
        var changed = false
        
        cloudEvents.forEach { cloud ->
            val localIndex = localEvents.indexOfFirst { it.eventId == cloud.eventId }
            if (localIndex == -1) {
                localEvents.add(cloud)
                changed = true
            } else {
                val local = localEvents[localIndex]
                if (!local.syncDirty && local != cloud) {
                    localEvents[localIndex] = cloud
                    changed = true
                }
            }
        }
        
        if (changed) {
            localEvents.sortByDescending { it.timestamp }
            val finalEvents = if (localEvents.size > 2000) localEvents.take(2000) else localEvents
            prefs.edit().putString(KEY_EVENTS, Json.encodeToString(finalEvents)).apply()
            updateFlows()
        }
    }
}
