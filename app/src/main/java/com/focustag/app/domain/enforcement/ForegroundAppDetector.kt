package com.focustag.app.domain.enforcement

data class ForegroundAppSnapshot(
    val packageName: String?,
    val detectedAtMillis: Long
)

interface ForegroundMonitoringHandle {
    fun stop()
}

interface ForegroundAppDetector {
    fun hasRequiredPermission(): Boolean

    fun currentForegroundApp(): ForegroundAppSnapshot?

    fun startMonitoring(
        onForegroundAppChanged: (ForegroundAppSnapshot) -> Unit,
        onFailure: (Throwable) -> Unit
    ): ForegroundMonitoringHandle
}
