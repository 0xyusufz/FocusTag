package com.focustag.app.domain

import com.focustag.app.data.model.EnforcementLedger
import com.focustag.app.data.model.EnforcementLedgerEntry
import com.focustag.app.data.model.EnforcementMechanism
import com.focustag.app.data.model.EnforcementResult
import com.focustag.app.data.model.EnforcementSnapshot
import com.focustag.app.data.model.FocusAction
import com.focustag.app.data.service.AccessibilitySessionState
import com.focustag.app.data.service.FocusTagAccessibilityService

class AccessibilityEnforcementStrategy : EnforcementStrategy {

    override suspend fun apply(snapshot: EnforcementSnapshot): EnforcementResult {
        val blockedPackages = snapshot.policies
            .filter { it.action == FocusAction.BLOCK }
            .map { it.appInfo.packageName }
            .filter { it != "com.focustag.app" } // Defensive self-protection
            .toSet()

        val newState = AccessibilitySessionState(
            ownerUserId = snapshot.userId,
            sessionId = snapshot.sessionId,
            blockedPackages = blockedPackages,
            isArmed = true
        )

        FocusTagAccessibilityService.StateManager.update(newState)

        val ledgerEntries = blockedPackages.associateWith { pkg ->
            EnforcementLedgerEntry(
                packageName = pkg,
                mechanism = EnforcementMechanism.ACCESSIBILITY,
                appliedAt = System.currentTimeMillis(),
                sessionId = snapshot.sessionId
            )
        }

        return EnforcementResult.Success(EnforcementLedger(ledgerEntries))
    }

    override suspend fun release(ledger: EnforcementLedger): EnforcementResult {
        FocusTagAccessibilityService.StateManager.update(AccessibilitySessionState())
        return EnforcementResult.Success(EnforcementLedger())
    }

    override suspend fun reconcile(snapshot: EnforcementSnapshot, ledger: EnforcementLedger): EnforcementResult {
        // Reconcile simply re-publishes the state to the service
        return apply(snapshot)
    }
}
