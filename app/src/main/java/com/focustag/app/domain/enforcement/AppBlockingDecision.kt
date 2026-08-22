package com.focustag.app.domain.enforcement

import com.focustag.app.data.model.EnforcementPolicy

enum class AppBlockingDecision {
    BLOCKED,
    PROTECTED,
    ALLOWED,
    UNKNOWN,
    NOT_ENFORCED
}

object AppBlockingDecisionEvaluator {
    fun evaluate(
        policy: EnforcementPolicy,
        packageName: String?
    ): AppBlockingDecision {
        if (!policy.shouldEnforce) {
            return AppBlockingDecision.NOT_ENFORCED
        }

        if (packageName.isNullOrBlank()) {
            return AppBlockingDecision.UNKNOWN
        }

        return when {
            policy.protectedPackages.contains(packageName) -> AppBlockingDecision.PROTECTED
            policy.blockedPackages.contains(packageName) -> AppBlockingDecision.BLOCKED
            policy.allowedPackages.contains(packageName) -> AppBlockingDecision.ALLOWED
            else -> AppBlockingDecision.UNKNOWN
        }
    }
}
