package com.focustag.app.platform.enforcement

import com.focustag.app.domain.enforcement.ForegroundAppSnapshot
import java.util.concurrent.CopyOnWriteArraySet

enum class AccessibilityServiceState {
    CONNECTED,
    INTERRUPTED,
    DESTROYED
}

object AccessibilityForegroundAppEventBus {
    private val listeners = CopyOnWriteArraySet<(ForegroundAppSnapshot) -> Unit>()
    private val serviceStateListeners = CopyOnWriteArraySet<(AccessibilityServiceState) -> Unit>()

    @Volatile
    private var lastSnapshot: ForegroundAppSnapshot? = null

    fun emit(snapshot: ForegroundAppSnapshot) {
        lastSnapshot = snapshot
        listeners.forEach { listener ->
            listener(snapshot)
        }
    }

    fun lastSnapshot(): ForegroundAppSnapshot? {
        return lastSnapshot
    }

    fun clearLastSnapshot() {
        lastSnapshot = null
    }

    fun addListener(listener: (ForegroundAppSnapshot) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (ForegroundAppSnapshot) -> Unit) {
        listeners.remove(listener)
    }

    fun emitServiceState(state: AccessibilityServiceState) {
        serviceStateListeners.forEach { listener ->
            listener(state)
        }
    }

    fun addServiceStateListener(listener: (AccessibilityServiceState) -> Unit) {
        serviceStateListeners.add(listener)
    }

    fun removeServiceStateListener(listener: (AccessibilityServiceState) -> Unit) {
        serviceStateListeners.remove(listener)
    }
}
