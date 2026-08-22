package com.focustag.app.domain.enforcement

import org.junit.Assert.assertEquals
import org.junit.Test

class EscapeProtectionCapabilityAssessmentTest {

    @Test
    fun accessibilityAndUsageAccessAvailableIsSafe() {
        val result = EscapeProtectionCapabilityAssessment.assess(
            accessibilityAvailable = true,
            usageAccessAvailable = true
        )

        assertEquals(EscapeProtectionStatus.SAFE, result.status)
    }

    @Test
    fun accessibilityUnavailableIsDetectedAsDegraded() {
        val result = EscapeProtectionCapabilityAssessment.assess(
            accessibilityAvailable = false,
            usageAccessAvailable = true
        )

        assertEquals(EscapeProtectionStatus.DETECTED, result.status)
        assertEquals(EscapeRoute.ACCESSIBILITY_DISABLED, result.route)
    }

    @Test
    fun usageAccessUnavailableIsDetectedAsDegraded() {
        val result = EscapeProtectionCapabilityAssessment.assess(
            accessibilityAvailable = true,
            usageAccessAvailable = false
        )

        assertEquals(EscapeProtectionStatus.DETECTED, result.status)
        assertEquals(EscapeRoute.USAGE_ACCESS_DISABLED, result.route)
    }

    @Test
    fun settingsForegroundIsRepresentedAsDetected() {
        val result = EscapeProtectionResult(
            status = EscapeProtectionStatus.DETECTED,
            route = EscapeRoute.SETTINGS_OPENED
        )

        assertEquals(EscapeProtectionStatus.DETECTED, result.status)
        assertEquals(EscapeRoute.SETTINGS_OPENED, result.route)
    }

    @Test
    fun uninstallAndDeviceLevelProtectionAreUnsupported() {
        val uninstall = EscapeProtectionCapabilityAssessment.unsupported(
            route = EscapeRoute.UNINSTALL_ATTEMPT,
            message = "Uninstall prevention requires managed-device privileges."
        )
        val deviceLevel = EscapeProtectionCapabilityAssessment.unsupported(
            route = EscapeRoute.UNSUPPORTED_ESCAPE_ROUTE,
            message = "Device-level protection requires Device Owner privileges."
        )

        assertEquals(EscapeProtectionStatus.UNSUPPORTED, uninstall.status)
        assertEquals(EscapeProtectionStatus.UNSUPPORTED, deviceLevel.status)
    }
}