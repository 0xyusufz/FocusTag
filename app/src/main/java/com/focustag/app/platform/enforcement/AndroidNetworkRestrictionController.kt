package com.focustag.app.platform.enforcement

import com.focustag.app.data.model.EnforcementPolicy
import com.focustag.app.domain.enforcement.AppBlockingDecision
import com.focustag.app.domain.enforcement.AppBlockingDecisionEvaluator
import com.focustag.app.domain.enforcement.EnforcementCapability
import com.focustag.app.domain.enforcement.EnforcementModuleResult
import com.focustag.app.domain.enforcement.EnforcementModuleStatus
import com.focustag.app.domain.enforcement.NetworkRestrictionEnforcer

/**
 * Reports the boundary of Android per-app VPN routing without pretending it is app blocking.
 *
 * VpnService application allow/disallow lists select which applications use or bypass the VPN;
 * they cannot deny only the blocked set without processing packets in the TUN interface. Packet
 * processing and traffic inspection are intentionally outside this phase.
 */
object AndroidNetworkRestrictionController : NetworkRestrictionEnforcer {
    override fun start(policy: EnforcementPolicy): EnforcementModuleResult {
        val blockedCount = policy.blockedPackages.count { packageName ->
            AppBlockingDecisionEvaluator.evaluate(policy, packageName) == AppBlockingDecision.BLOCKED
        }
        val protectedCount = policy.protectedPackages.count { packageName ->
            AppBlockingDecisionEvaluator.evaluate(policy, packageName) == AppBlockingDecision.PROTECTED
        }

        return EnforcementModuleResult(
            capability = EnforcementCapability.NETWORK_RESTRICTION,
            status = EnforcementModuleStatus.UNSUPPORTED,
            message = "Per-app VPN routing cannot deny only blocked applications without packet " +
                "processing; blocked=$blockedCount, protected=$protectedCount. App Blocking remains active."
        )
    }

    override fun stop(): EnforcementModuleResult {
        return EnforcementModuleResult(
            capability = EnforcementCapability.NETWORK_RESTRICTION,
            status = EnforcementModuleStatus.STOPPED,
            message = "No VPN was established; normal network behavior is unchanged."
        )
    }
}
