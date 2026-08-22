package com.focustag.app.platform.enforcement

import android.content.Context
import com.focustag.app.domain.enforcement.AppBlockingModule
import com.focustag.app.domain.enforcement.EnforcementCoordinator
import com.focustag.app.domain.enforcement.EnforcementStateMachine
import com.focustag.app.domain.enforcement.DefaultHealthMonitor
import com.focustag.app.domain.enforcement.DefaultRecoveryManager

object AndroidEnforcementFactory {
    fun create(context: Context): AndroidEnforcementRuntime {
        val logger = AndroidEnforcementLogger()
        val accessibilityDetector = AccessibilityForegroundAppDetector(context)
        val appBlockingModule = AppBlockingModule(
            foregroundAppDetector = accessibilityDetector,
                blockingExperienceController = AndroidBlockingExperienceController(context),
                logger = logger
            )
        val coordinator = EnforcementCoordinator(
            appBlockingEnforcer = appBlockingModule,
            visibilityControlEnforcer = AndroidVisibilityController,
            networkRestrictionEnforcer = AndroidNetworkRestrictionController,
            escapeProtectionEnforcer = AndroidEscapeProtectionController(context, logger = logger),
            stateMachine = EnforcementStateMachine(logger = logger),
            logger = logger
        )
        val healthMonitor = DefaultHealthMonitor(logger)
        val recoveryManager = DefaultRecoveryManager(
            healthMonitor = healthMonitor,
            appBlockingEnforcer = appBlockingModule,
            logger = logger
        )
        return AndroidEnforcementRuntime(
            coordinator = coordinator,
            appBlockingModule = appBlockingModule,
            healthMonitor = healthMonitor,
            recoveryManager = recoveryManager,
            accessibilityDetector = accessibilityDetector,
            logger = logger
        )
    }
}
