package com.focustag.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnforcementPolicyTest {

    @Test
    fun enforcementPolicyIsDerivedFromResolvedPolicyActions() {
        val blockedApp = AppInfo("com.example.blocked", "Blocked", AppCategory.ALLOWABLE)
        val protectedApp = AppInfo("com.focustag.app", "FocusTag", AppCategory.CORE)
        val allowedApp = AppInfo("com.example.allowed", "Allowed", AppCategory.ALLOWABLE)

        val policy = EnforcementPolicy.from(
            focusSessionState = FocusSessionState(
                focusState = FocusState.FOCUS_ACTIVE,
                activeTagId = "tag"
            ),
            resolvedPolicies = listOf(
                ResolvedPolicy(blockedApp, FocusAction.BLOCK),
                ResolvedPolicy(protectedApp, FocusAction.PROTECTED),
                ResolvedPolicy(allowedApp, FocusAction.ALLOW)
            )
        )

        assertTrue(policy.shouldEnforce)
        assertEquals(setOf(blockedApp.packageName), policy.blockedPackages)
        assertEquals(setOf(protectedApp.packageName), policy.protectedPackages)
        assertEquals(setOf(allowedApp.packageName), policy.allowedPackages)
    }
}
