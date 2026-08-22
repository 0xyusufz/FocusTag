package com.focustag.app.platform.enforcement

import com.focustag.app.data.model.EnforcementPolicy
import com.focustag.app.domain.enforcement.EnforcementCapability
import com.focustag.app.domain.enforcement.EnforcementModuleResult
import com.focustag.app.domain.enforcement.EnforcementModuleStatus
import com.focustag.app.domain.enforcement.VisibilityControlEnforcer

/**
 * Reports the Android visibility boundary without attempting unsupported package mutations.
 *
 * A normal third-party application cannot hide or disable another installed application's launcher
 * activity. Device Owner, privileged OEM access, or a replacement/default launcher would be
 * required, and those mechanisms are outside this phase.
 */
object AndroidVisibilityController : VisibilityControlEnforcer {
    override fun start(policy: EnforcementPolicy): EnforcementModuleResult {
        return EnforcementModuleResult(
            capability = EnforcementCapability.VISIBILITY_CONTROL,
            status = EnforcementModuleStatus.UNSUPPORTED,
            message = "Android does not allow a normal app to hide another app's launcher surface. " +
                "Foreground App Blocking remains responsible for blocked applications."
        )
    }

    override fun stop(): EnforcementModuleResult {
        return EnforcementModuleResult(
            capability = EnforcementCapability.VISIBILITY_CONTROL,
            status = EnforcementModuleStatus.STOPPED,
            message = "No launcher visibility state was changed; nothing requires restoration."
        )
    }
}
