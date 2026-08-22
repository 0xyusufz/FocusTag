package com.focustag.app.data.model

data class EnforcementPolicy(
    val focusState: FocusState,
    val activeTagId: String?,
    val blockedPackages: Set<String>,
    val protectedPackages: Set<String>,
    val allowedPackages: Set<String>
) {
    val shouldEnforce: Boolean
        get() = focusState == FocusState.FOCUS_ACTIVE

    companion object {
        fun from(
            focusSessionState: FocusSessionState,
            resolvedPolicies: List<ResolvedPolicy>
        ): EnforcementPolicy {
            return EnforcementPolicy(
                focusState = focusSessionState.focusState,
                activeTagId = focusSessionState.activeTagId,
                blockedPackages = resolvedPolicies
                    .filter { it.action == FocusAction.BLOCK }
                    .map { it.appInfo.packageName }
                    .toSet(),
                protectedPackages = resolvedPolicies
                    .filter { it.action == FocusAction.PROTECTED }
                    .map { it.appInfo.packageName }
                    .toSet(),
                allowedPackages = resolvedPolicies
                    .filter { it.action == FocusAction.ALLOW }
                    .map { it.appInfo.packageName }
                    .toSet()
            )
        }
    }
}

enum class EnforcementLifecycleState {
    INACTIVE,
    STARTING,
    ACTIVE,
    STOPPING,
    RECOVERY_REQUIRED
}

data class EnforcementState(
    val lifecycleState: EnforcementLifecycleState = EnforcementLifecycleState.INACTIVE,
    val activePolicy: EnforcementPolicy? = null,
    val lastError: String? = null
)
