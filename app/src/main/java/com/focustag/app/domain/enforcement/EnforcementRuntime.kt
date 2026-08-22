package com.focustag.app.domain.enforcement

import com.focustag.app.data.model.EnforcementPolicy

interface EnforcementRuntime : EnforcementAbstraction {
    val healthMonitor: HealthMonitor
    val currentHealth: EnforcementHealthSnapshot

    fun checkHealth(): EnforcementHealthSnapshot

    fun recover(policy: EnforcementPolicy? = null): RecoveryResult

    fun addHealthListener(listener: (EnforcementHealthSnapshot) -> Unit)

    fun removeHealthListener(listener: (EnforcementHealthSnapshot) -> Unit)

    fun close()
}
