package com.focustag.app.domain

import android.content.Context
import android.util.Log
import com.focustag.app.data.repository.AppInventoryRepository
import com.focustag.app.data.repository.AppPolicyRepository
import com.focustag.app.data.repository.EnforcementRepository
import com.focustag.app.data.repository.SessionHistoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * A process-lifetime singleton that manages the EnforcementCoordinator graph.
 * This allows both the UI (FocusViewModel) and the background mechanism 
 * (AccessibilityService) to access the same logical authority.
 */
object EnforcementCoordinatorHub {
    private const val TAG = "EnforcementHub"
    private val hubScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Map of userId to Coordinator to ensure user isolation
    private val coordinators = mutableMapOf<String, EnforcementCoordinator>()

    /**
     * Gets or creates an EnforcementCoordinator for the specified userId.
     */
    fun getCoordinator(context: Context, userId: String): EnforcementCoordinator = synchronized(this) {
        return coordinators.getOrPut(userId) {
            Log.d(TAG, "Creating new EnforcementCoordinator for $userId")
            val appContext = context.applicationContext
            EnforcementCoordinator(
                userId = userId,
                inventoryRepository = AppInventoryRepository(appContext),
                policyRepository = AppPolicyRepository(appContext, userId),
                enforcementRepository = EnforcementRepository(appContext, userId),
                sessionHistoryRepository = SessionHistoryRepository(appContext, userId),
                strategy = AccessibilityEnforcementStrategy()
            )
        }
    }

    /**
     * Triggers a headless reconciliation by reading the device-scoped active owner.
     * This is called by the AccessibilityService after a reboot.
     */
    fun requestHeadlessReconcile(context: Context) {
        hubScope.launch {
            try {
                // Read the device-level owner from a temporary repository instance
                val enforcementRepo = EnforcementRepository(context.applicationContext, "")
                val activeOwnerId = enforcementRepo.getDeviceEnforcementOwnerId()

                if (activeOwnerId != null) {
                    Log.i(TAG, "Headless reconciliation requested for owner: $activeOwnerId")
                    val coordinator = getCoordinator(context, activeOwnerId)
                    coordinator.reconcile()
                } else {
                    Log.d(TAG, "Headless reconciliation ignored: No active enforcement owner found.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during headless reconciliation: ${e.message}")
            }
        }
    }
}
