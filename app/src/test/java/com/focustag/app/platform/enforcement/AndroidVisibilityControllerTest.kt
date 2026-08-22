package com.focustag.app.platform.enforcement

import com.focustag.app.data.model.EnforcementPolicy
import com.focustag.app.data.model.FocusState
import com.focustag.app.domain.enforcement.EnforcementCapability
import com.focustag.app.domain.enforcement.EnforcementModuleStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidVisibilityControllerTest {

    @Test
    fun blockedPolicyReportsUnsupportedWithoutHidingPackages() {
        val result = AndroidVisibilityController.start(policy(blocked = setOf("com.example.blocked")))

        assertEquals(EnforcementCapability.VISIBILITY_CONTROL, result.capability)
        assertEquals(EnforcementModuleStatus.UNSUPPORTED, result.status)
        assertTrue(result.message.orEmpty().contains("cannot hide", ignoreCase = true))
    }

    @Test
    fun protectedPolicyIsNeverHiddenByUnsupportedController() {
        val result = AndroidVisibilityController.start(
            policy(
                blocked = setOf("com.example.blocked"),
                protected = setOf("com.example.protected")
            )
        )

        assertEquals(EnforcementModuleStatus.UNSUPPORTED, result.status)
    }

    @Test
    fun allowedAndUnknownPoliciesDoNotRequestPlatformMutation() {
        val allowedResult = AndroidVisibilityController.start(
            policy(allowed = setOf("com.example.allowed"))
        )
        val unknownResult = AndroidVisibilityController.start(policy())

        assertEquals(EnforcementModuleStatus.UNSUPPORTED, allowedResult.status)
        assertEquals(EnforcementModuleStatus.UNSUPPORTED, unknownResult.status)
    }

    @Test
    fun stopReportsNoVisibilityStateToRestore() {
        val result = AndroidVisibilityController.stop()

        assertEquals(EnforcementModuleStatus.STOPPED, result.status)
        assertTrue(result.message.orEmpty().contains("nothing requires restoration"))
    }

    @Test
    fun repeatedFocusStartDoesNotAttemptRepeatedPlatformMutation() {
        val firstResult = AndroidVisibilityController.start(policy())
        val secondResult = AndroidVisibilityController.start(policy())

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
