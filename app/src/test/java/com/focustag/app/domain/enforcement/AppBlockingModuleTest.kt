package com.focustag.app.domain.enforcement

import com.focustag.app.data.model.EnforcementLifecycleState
import com.focustag.app.data.model.EnforcementPolicy
import com.focustag.app.data.model.FocusState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppBlockingModuleTest {

    @Test
    fun startBeginsMonitoringAndEvaluatesForegroundChanges() {
        val detector = FakeForegroundAppDetector()
        val controller = FakeBlockingExperienceController()
        val module = AppBlockingModule(detector, controller, TestAppBlockingLogger())

        val result = module.start(activePolicy(blockedPackages = setOf("com.example.blocked")))
        detector.emit("com.example.blocked")

        assertEquals(EnforcementModuleStatus.STARTED, result.status)
        assertTrue(detector.isMonitoring)
        assertEquals(AppBlockingDecision.BLOCKED, module.currentDecision())
        assertEquals("com.example.blocked", controller.lastBlockedPackage)
    }

    @Test
    fun stopReleasesMonitoringHandle() {
        val detector = FakeForegroundAppDetector()
        val controller = FakeBlockingExperienceController()
        val module = AppBlockingModule(detector, controller, TestAppBlockingLogger())

        module.start(activePolicy(blockedPackages = setOf("com.example.blocked")))
        val result = module.stop()

        assertEquals(EnforcementModuleStatus.STOPPED, result.status)
        assertTrue(detector.wasStopped)
        assertTrue(controller.wasCleared)
        assertEquals(null, module.currentDecision())
    }

    @Test
    fun permissionUnavailableDoesNotStartMonitoring() {
        val detector = FakeForegroundAppDetector(hasPermission = false)
        val module = AppBlockingModule(detector, FakeBlockingExperienceController(), TestAppBlockingLogger())

        val result = module.start(activePolicy(blockedPackages = setOf("com.example.blocked")))

        assertEquals(EnforcementModuleStatus.FAILED, result.status)
        assertEquals(false, detector.isMonitoring)
    }

    @Test
    fun inactivePolicyClearsBlockingExperienceAndStopsMonitoring() {
        val detector = FakeForegroundAppDetector()
        val controller = FakeBlockingExperienceController()
        val module = AppBlockingModule(detector, controller, TestAppBlockingLogger())

        module.start(activePolicy(blockedPackages = setOf("com.example.blocked")))
        detector.emit("com.example.blocked")
        val result = module.start(inactivePolicy())

        assertEquals(EnforcementModuleStatus.STOPPED, result.status)
        assertEquals(false, detector.isMonitoring)
        assertEquals(null, module.currentDecision())
        assertEquals(null, controller.lastBlockedPackage)
        assertTrue(controller.clearCount >= 1)
    }

    @Test
    fun protectedPackageDoesNotShowBlockingExperience() {
        val detector = FakeForegroundAppDetector()
        val controller = FakeBlockingExperienceController()
        val module = AppBlockingModule(detector, controller, TestAppBlockingLogger())

        module.start(activePolicy(protectedPackages = setOf("com.focustag.app")))
        detector.emit("com.focustag.app")

        assertEquals(AppBlockingDecision.PROTECTED, module.currentDecision())
        assertEquals(null, controller.lastBlockedPackage)
    }

    @Test
    fun allowedPackageDoesNotShowBlockingExperience() {
        val detector = FakeForegroundAppDetector()
        val controller = FakeBlockingExperienceController()
        val module = AppBlockingModule(detector, controller, TestAppBlockingLogger())

        module.start(activePolicy(allowedPackages = setOf("com.example.allowed")))
        detector.emit("com.example.allowed")

        assertEquals(AppBlockingDecision.ALLOWED, module.currentDecision())
        assertEquals(null, controller.lastBlockedPackage)
    }

    @Test
    fun protectedPackageClearsAnExistingBlockingExperience() {
        val detector = FakeForegroundAppDetector()
        val controller = FakeBlockingExperienceController()
        val module = AppBlockingModule(detector, controller, TestAppBlockingLogger())

        module.start(
            activePolicy(
                blockedPackages = setOf("com.example.blocked"),
                protectedPackages = setOf("com.focustag.app")
            )
        )
        detector.emit("com.example.blocked")
        detector.emit("com.focustag.app")

        assertEquals(AppBlockingDecision.PROTECTED, module.currentDecision())
        assertEquals(null, controller.lastBlockedPackage)
        assertTrue(controller.clearCount >= 1)
    }

    @Test
    fun allowedPackageClearsAnExistingBlockingExperience() {
        val detector = FakeForegroundAppDetector()
        val controller = FakeBlockingExperienceController()
        val module = AppBlockingModule(detector, controller, TestAppBlockingLogger())

        module.start(
            activePolicy(
                blockedPackages = setOf("com.example.blocked"),
                allowedPackages = setOf("com.example.allowed")
            )
        )
        detector.emit("com.example.blocked")
        detector.emit("com.example.allowed")

        assertEquals(AppBlockingDecision.ALLOWED, module.currentDecision())
        assertEquals(null, controller.lastBlockedPackage)
        assertTrue(controller.clearCount >= 1)
    }

    @Test
    fun unknownPackageClearsAnExistingBlockingExperience() {
        val detector = FakeForegroundAppDetector()
        val controller = FakeBlockingExperienceController()
        val module = AppBlockingModule(detector, controller, TestAppBlockingLogger())

        module.start(activePolicy(blockedPackages = setOf("com.example.blocked")))
        detector.emit("com.example.blocked")
        detector.emit("com.example.unknown")

        assertEquals(AppBlockingDecision.UNKNOWN, module.currentDecision())
        assertEquals(null, controller.lastBlockedPackage)
        assertTrue(controller.clearCount >= 1)
    }

    @Test
    fun repeatedBlockedPackageDoesNotCreateDuplicateBlockingState() {
        val detector = FakeForegroundAppDetector()
        val controller = FakeBlockingExperienceController()
        val logger = TestAppBlockingLogger()
        val module = AppBlockingModule(detector, controller, logger)

        module.start(activePolicy(blockedPackages = setOf("com.example.blocked")))
        detector.emit("com.example.blocked")
        detector.emit("com.example.blocked")

        assertEquals(AppBlockingDecision.BLOCKED, module.currentDecision())
        assertEquals(1, controller.showCount)
        assertEquals("com.example.blocked", controller.lastBlockedPackage)
        assertEquals(1, logger.auditEvents.count { it.eventType == AuditEventType.APP_BLOCKED })
    }

    private fun activePolicy(
        blockedPackages: Set<String> = emptySet(),
        protectedPackages: Set<String> = emptySet(),
        allowedPackages: Set<String> = emptySet()
    ): EnforcementPolicy {
        return EnforcementPolicy(
            focusState = FocusState.FOCUS_ACTIVE,
            activeTagId = "tag",
            blockedPackages = blockedPackages,
            protectedPackages = protectedPackages,
            allowedPackages = allowedPackages
        )
    }

    private fun inactivePolicy(): EnforcementPolicy {
        return EnforcementPolicy(
            focusState = FocusState.NORMAL,
            activeTagId = null
        )
    }
}

private class FakeForegroundAppDetector(
    private val hasPermission: Boolean = true
) : ForegroundAppDetector {

    var isMonitoring = false
        private set
    var wasStopped = false
        private set

    private var onForegroundAppChanged: ((ForegroundAppSnapshot) -> Unit)? = null

    override fun hasRequiredPermission(): Boolean {
        return hasPermission
    }

    override fun currentForegroundApp(): ForegroundAppSnapshot? {
        return null
    }

    override fun startMonitoring(
        onForegroundAppChanged: (ForegroundAppSnapshot) -> Unit,
        onFailure: (Throwable) -> Unit
    ): ForegroundMonitoringHandle {
        isMonitoring = true
        this.onForegroundAppChanged = onForegroundAppChanged

        return object : ForegroundMonitoringHandle {
            override fun stop() {
                wasStopped = true
                isMonitoring = false
            }
        }
    }

    fun emit(packageName: String?) {
        onForegroundAppChanged?.invoke(
            ForegroundAppSnapshot(
                packageName = packageName,
                detectedAtMillis = 1L
            )
        )
    }
}

private class FakeBlockingExperienceController : BlockingExperienceController {
    var lastBlockedPackage: String? = null
        private set
    var wasCleared = false
        private set
    var clearCount = 0
        private set
    var showCount = 0
        private set

    override fun showBlockedApp(packageName: String): BlockingExperienceResult {
        if (lastBlockedPackage != packageName) {
            showCount += 1
        }
        lastBlockedPackage = packageName
        return BlockingExperienceResult(succeeded = true)
    }

    override fun clear(): BlockingExperienceResult {
        wasCleared = true
        clearCount += 1
        lastBlockedPackage = null
        return BlockingExperienceResult(succeeded = true)
    }
}

private class TestAppBlockingLogger : EnforcementLogger {
    val auditEvents = mutableListOf<AuditEvent>()

    override fun auditEvent(event: AuditEvent) {
        auditEvents += event
    }

    override fun transition(
        from: EnforcementLifecycleState,
        to: EnforcementLifecycleState,
        eventName: String
    ) = Unit

    override fun invalidTransition(
        from: EnforcementLifecycleState,
        eventName: String
    ) = Unit

    override fun moduleResult(result: EnforcementModuleResult) = Unit
}
