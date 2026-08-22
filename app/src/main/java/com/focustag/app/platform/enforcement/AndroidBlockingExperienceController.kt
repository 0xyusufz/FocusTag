package com.focustag.app.platform.enforcement

import android.content.Context
import android.content.Intent
import com.focustag.app.domain.enforcement.BlockingExperienceController
import com.focustag.app.domain.enforcement.BlockingExperienceResult

class AndroidBlockingExperienceController(
    context: Context
) : BlockingExperienceController {

    private val appContext = context.applicationContext

    @Volatile
    private var activeBlockedPackage: String? = null

    override fun showBlockedApp(packageName: String): BlockingExperienceResult {
        if (packageName == activeBlockedPackage && BlockingActivity.isVisible) {
            return BlockingExperienceResult(
                succeeded = true,
                message = "Blocking experience is already visible for this package."
            )
        }

        return try {
            activeBlockedPackage = packageName
            appContext.startActivity(BlockingActivity.createIntent(appContext, packageName))
            BlockingExperienceResult(
                succeeded = true,
                message = "Blocking experience launched."
            )
        } catch (error: Exception) {
            activeBlockedPackage = null
            BlockingExperienceResult(
                succeeded = false,
                message = error.message ?: error::class.java.simpleName
            )
        }
    }

    override fun clear(): BlockingExperienceResult {
        activeBlockedPackage = null
        BlockingActivity.finishVisibleInstance()
        return BlockingExperienceResult(
            succeeded = true,
            message = "Blocking experience cleared."
        )
    }
}
