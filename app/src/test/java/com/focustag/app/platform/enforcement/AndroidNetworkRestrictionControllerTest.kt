package com.focustag.app.platform.enforcement

import com.focustag.app.data.model.EnforcementPolicy
import com.focustag.app.data.model.FocusState
import com.focustag.app.domain.enforcement.EnforcementCapability
import com.focustag.app.domain.enforcement.EnforcementModuleStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidNetworkRestrictionControllerTest {

    @Test
    fun blockedPolicyReportsUnsupportedInsteadOfClaimingNetworkDenial() {
        val result = AndroidNetworkRestrictionController.start(
            policy(blocked = setOf("com.example.blocked"))
        )

        assertEquals(EnforcementCapability.NETWORK_RESTRICTION, result.capability)
        assertEquals(EnforcementModuleStatus.UNSUPPORTED, result.status)
        assertTrue(result.message.orEmpty().contains("packet processing"))
    }

    @Test
    fun protectedPackageTakesPrecedenceOverBlockedPackage() {
        val result = AndroidNetworkRestrictionController.start(
            policy(
                blocked = setOf("com.example.shared"),
                protected = setOf("com.example.shared")
            )
        )

        assertEquals(EnforcementModuleStatus.UNSUPPORTED, result.status)
        assertTrue(result.message.orEmpty().contains("protected=1"))
    }

    @Test
    fun allowedAndUnknownPoliciesRemainUnchanged() {
        val result = AndroidNetworkRestrictionController.start(
            policy(allowed = setOf("com.example.allowed"))
        )

        assertEquals(EnforcementModuleStatus.UNSUPPORTED, result.status)
        assertTrue(result.message.orEmpty().contains("App Blocking remains active"))
    }

    @Test
    fun stoppingReportsThatNoVpnStateRequiresRestoration() {
        val result = AndroidNetworkRestrictionController.stop()

        assertEquals(EnforcementModuleStatus.STOPPED, result.status)
        assertTrue(result.message.orEmpty().contains("No VPN was established"))
    }

    @Test
    fun repeatedStartsRemainUnsupportedWithoutCreatingDuplicateVpnState() {
        val firstResult = AndroidNetworkRestrictionController.start(policy())
        val secondResult = AndroidNetworkRestrictionController.start(policy())

        assertEquals(EnforcementModuleStatus.UNSUPPORTED, firstResult.status)
        assertEquals(EnforcementModuleStatus.UNSUPPORTED, secondResult.status)
    }

    private fun policy(
        blocked: Set<String> = emptySet(),
        protected: Set<String> = emptySet(),
        allowed: Set<String> = emptySet()
    ): EnforcementPolicy {
        return EnforcementPolicy(
            focusState = FocusState.FOCUS_ACTIVE,
            activeTagId = "tag",
            blockedPackages = blocked,
            protectedPackages = protected,
            allowedPackages = allowed
        )
    }
}
