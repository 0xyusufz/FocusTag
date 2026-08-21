package com.focustag.app.domain

import com.focustag.app.data.model.EnforcementLedger
import com.focustag.app.data.model.EnforcementResult
import com.focustag.app.data.model.EnforcementSnapshot

interface EnforcementStrategy {
    suspend fun apply(snapshot: EnforcementSnapshot): EnforcementResult
    suspend fun release(ledger: EnforcementLedger): EnforcementResult
    suspend fun reconcile(snapshot: EnforcementSnapshot, ledger: EnforcementLedger): EnforcementResult
}
