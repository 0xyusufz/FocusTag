package com.focustag.app.domain.enforcement

import com.focustag.app.data.model.EnforcementPolicy
import com.focustag.app.data.model.FocusState
import org.junit.Assert.assertEquals
import org.junit.Test

class AppBlockingDecisionEvaluatorTest {

    @Test
    fun blockedPackageReturnsBlocked() {
        val policy = activePolicy(blockedPackages = setOf("com.example.blocked"))

        val decision = AppBlockingDecisionEvaluator.evaluate(policy, "com.example.blocked")

        assertEquals(AppBlockingDecision.BLOCKED, decision)
    }

    @Test
    fun protectedPackageReturnsProtected() {
        val policy = activePolicy(protectedPackages = setOf("com.focustag.app"))

        val decision = AppBlockingDecisionEvaluator.evaluate(policy, "com.focustag.app")

        assertEquals(AppBlockingDecision.PROTECTED, decision)
    }

    @Test
    fun allowedPackageReturnsAllowed() {
        val policy = activePolicy(allowedPackages = setOf("com.example.allowed"))

        val decision = AppBlockingDecisionEvaluator.evaluate(policy, "com.example.allowed")

        assertEquals(AppBlockingDecision.ALLOWED, decision)
    }

    @Test
    fun protectedPackageTakesPrecedenceOverBlockedPackage() {
        val policy = activePolicy(
            blockedPackages = setOf("com.focustag.app"),
            protectedPackages = setOf("com.focustag.app")
        )

        val decision = AppBlockingDecisionEvaluator.evaluate(policy, "com.focustag.app")

        assertEquals(AppBlockingDecision.PROTECTED, decision)
    }

    @Test
    fun unknownPackageReturnsUnknown() {
        val policy = activePolicy()

        val decision = AppBlockingDecisionEvaluator.evaluate(policy, "com.example.unknown")

        assertEquals(AppBlockingDecision.UNKNOWN, decision)
    }

    @Test
    fun inactiveEnforcementReturnsNotEnforced() {
        val policy = activePolicy(
            focusState = FocusState.NORMAL,
            blockedPackages = setOf("com.example.blocked")
        )

        val decision = AppBlockingDecisionEvaluator.evaluate(policy, "com.example.blocked")

        assertEquals(AppBlockingDecision.NOT_ENFORCED, decision)
    }

    private fun activePolicy(
        focusState: FocusState = FocusState.FOCUS_ACTIVE,
        blockedPackages: Set<String> = emptySet(),
        protectedPackages: Set<String> = emptySet(),
        allowedPackages: Set<String> = emptySet()
    ): EnforcementPolicy {
        return EnforcementPolicy(
            focusState = focusState,
            activeTagId = "tag",
            blockedPackages = blockedPackages,
            protectedPackages = protectedPackages,
            allowedPackages = allowedPackages
        )
    }
}
