package com.focustag.app.domain.enforcement

import org.junit.Assert.assertEquals
import org.junit.Test

class BlockingExperienceDecisionMapperTest {

    @Test
    fun blockedRequiresBlockingExperience() {
        assertEquals(
            true,
            BlockingExperienceDecisionMapper.requiresBlockingExperience(AppBlockingDecision.BLOCKED)
        )
    }

    @Test
    fun protectedDoesNotRequireBlockingExperience() {
        assertEquals(
            false,
            BlockingExperienceDecisionMapper.requiresBlockingExperience(AppBlockingDecision.PROTECTED)
        )
    }

    @Test
    fun allowedDoesNotRequireBlockingExperience() {
        assertEquals(
            false,
            BlockingExperienceDecisionMapper.requiresBlockingExperience(AppBlockingDecision.ALLOWED)
        )
    }

    @Test
    fun unknownDoesNotRequireBlockingExperience() {
        assertEquals(
            false,
            BlockingExperienceDecisionMapper.requiresBlockingExperience(AppBlockingDecision.UNKNOWN)
        )
    }

    @Test
    fun notEnforcedDoesNotRequireBlockingExperience() {
        assertEquals(
            false,
            BlockingExperienceDecisionMapper.requiresBlockingExperience(AppBlockingDecision.NOT_ENFORCED)
        )
    }
}
