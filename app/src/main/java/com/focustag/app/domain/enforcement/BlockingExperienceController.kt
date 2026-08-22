package com.focustag.app.domain.enforcement

data class BlockingExperienceResult(
    val succeeded: Boolean,
    val message: String? = null
)

interface BlockingExperienceController {
    fun showBlockedApp(packageName: String): BlockingExperienceResult

    fun clear(): BlockingExperienceResult
}

object NoOpBlockingExperienceController : BlockingExperienceController {
    override fun showBlockedApp(packageName: String): BlockingExperienceResult {
        return BlockingExperienceResult(
            succeeded = true,
            message = "No blocking experience controller is installed."
        )
    }

    override fun clear(): BlockingExperienceResult {
        return BlockingExperienceResult(
            succeeded = true,
            message = "No blocking experience controller is installed."
        )
    }
}

object BlockingExperienceDecisionMapper {
    fun requiresBlockingExperience(decision: AppBlockingDecision): Boolean {
        return decision == AppBlockingDecision.BLOCKED
    }
}
