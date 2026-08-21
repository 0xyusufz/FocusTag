package com.focustag.app.domain

import com.focustag.app.data.model.EnforcementLedger
import com.focustag.app.data.model.EnforcementLedgerEntry
import com.focustag.app.data.model.EnforcementMechanism
import com.focustag.app.data.model.EnforcementResult
import com.focustag.app.data.model.EnforcementSnapshot
import com.focustag.app.data.model.FocusAction

class NoOpEnforcementStrategy : EnforcementStrategy {
    override suspend fun apply(snapshot: EnforcementSnapshot): EnforcementResult {
        // No actual blocking performed. 
        // We simulate a ledger by recording what we *would* have blocked.
        val entries = snapshot.policies
            .filter { it.action == FocusAction.BLOCK }
            .associate { policy ->
                policy.appInfo.packageName to EnforcementLedgerEntry(
                    packageName = policy.appInfo.packageName,
                    mechanism = EnforcementMechanism.NO_OP,
                    appliedAt = System.currentTimeMillis(),
                    sessionId = snapshot.sessionId
                )
            }
        
        return EnforcementResult.Simulated(EnforcementLedger(entries))
    }

    override suspend fun release(ledger: EnforcementLedger): EnforcementResult {
        // No actual restrictions to remove.
        return EnforcementResult.Success(EnforcementLedger())
    }

    override suspend fun reconcile(snapshot: EnforcementSnapshot, ledger: EnforcementLedger): EnforcementResult {
        // No actual state to restore.
        return apply(snapshot)
    }
}
