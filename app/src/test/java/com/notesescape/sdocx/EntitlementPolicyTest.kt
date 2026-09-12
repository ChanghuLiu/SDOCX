package com.notesescape.sdocx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntitlementPolicyTest {
    @Test
    fun lifetimeUnlockAllowsAnyExportShape() {
        assertEquals(
            ExportAccessDecision.ALLOWED,
            ExportAccessPolicy.decide(
                lifetimeUnlocked = true,
                trialUsed = true,
                sourceCount = 25,
                folderImport = true
            )
        )
    }

    @Test
    fun unusedTrialAllowsExactlyOneIndividuallySelectedNote() {
        assertEquals(
            ExportAccessDecision.ALLOWED,
            ExportAccessPolicy.decide(
                lifetimeUnlocked = false,
                trialUsed = false,
                sourceCount = 1,
                folderImport = false
            )
        )
    }

    @Test
    fun unusedTrialDoesNotAllowMultiFileBatch() {
        assertEquals(
            ExportAccessDecision.SINGLE_NOTE_TRIAL_ONLY,
            ExportAccessPolicy.decide(
                lifetimeUnlocked = false,
                trialUsed = false,
                sourceCount = 2,
                folderImport = false
            )
        )
    }

    @Test
    fun unusedTrialDoesNotAllowFolderExport() {
        assertEquals(
            ExportAccessDecision.SINGLE_NOTE_TRIAL_ONLY,
            ExportAccessPolicy.decide(
                lifetimeUnlocked = false,
                trialUsed = false,
                sourceCount = 1,
                folderImport = true
            )
        )
    }

    @Test
    fun usedTrialRequiresLifetimeUnlock() {
        assertEquals(
            ExportAccessDecision.LIFETIME_UNLOCK_REQUIRED,
            ExportAccessPolicy.decide(
                lifetimeUnlocked = false,
                trialUsed = true,
                sourceCount = 1,
                folderImport = false
            )
        )
    }

    @Test
    fun trialIsConsumedOnlyAfterSuccessfulMeaningfulOutput() {
        assertTrue(
            TrialConsumptionPolicy.shouldConsume(
                isTrialExport = true,
                outputWritten = true,
                notesConverted = 1
            )
        )
        assertFalse(
            TrialConsumptionPolicy.shouldConsume(
                isTrialExport = true,
                outputWritten = false,
                notesConverted = 1
            )
        )
        assertFalse(
            TrialConsumptionPolicy.shouldConsume(
                isTrialExport = true,
                outputWritten = true,
                notesConverted = 0
            )
        )
        assertFalse(
            TrialConsumptionPolicy.shouldConsume(
                isTrialExport = false,
                outputWritten = true,
                notesConverted = 1
            )
        )
    }
}
