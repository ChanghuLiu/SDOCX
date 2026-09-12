package com.notesescape.sdocx

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class EntitlementState(
    val trialUsed: Boolean,
    val lifetimeUnlocked: Boolean
)

internal enum class ExportAccessDecision {
    ALLOWED,
    SINGLE_NOTE_TRIAL_ONLY,
    LIFETIME_UNLOCK_REQUIRED
}

internal object ExportAccessPolicy {
    fun decide(
        lifetimeUnlocked: Boolean,
        trialUsed: Boolean,
        sourceCount: Int,
        folderImport: Boolean
    ): ExportAccessDecision {
        if (lifetimeUnlocked) return ExportAccessDecision.ALLOWED
        if (trialUsed) return ExportAccessDecision.LIFETIME_UNLOCK_REQUIRED
        return if (sourceCount == 1 && !folderImport) {
            ExportAccessDecision.ALLOWED
        } else {
            ExportAccessDecision.SINGLE_NOTE_TRIAL_ONLY
        }
    }
}

internal object TrialConsumptionPolicy {
    fun shouldConsume(
        isTrialExport: Boolean,
        outputWritten: Boolean,
        notesConverted: Int
    ): Boolean = isTrialExport && outputWritten && notesConverted > 0
}

internal class EntitlementStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val _state = MutableStateFlow(readState())
    val state: StateFlow<EntitlementState> = _state.asStateFlow()

    val current: EntitlementState
        get() = _state.value

    fun recordSuccessfulTrialExport() {
        if (_state.value.trialUsed) return
        preferences.edit().putBoolean(KEY_TRIAL_USED, true).apply()
        _state.value = _state.value.copy(trialUsed = true)
    }

    fun setLifetimeUnlocked(unlocked: Boolean) {
        if (_state.value.lifetimeUnlocked == unlocked) return
        preferences.edit().putBoolean(KEY_LIFETIME_UNLOCKED, unlocked).apply()
        _state.value = _state.value.copy(lifetimeUnlocked = unlocked)
    }

    private fun readState(): EntitlementState = EntitlementState(
        trialUsed = preferences.getBoolean(KEY_TRIAL_USED, false),
        lifetimeUnlocked = preferences.getBoolean(KEY_LIFETIME_UNLOCKED, false)
    )

    private companion object {
        const val PREFS_NAME = "notes_escape_entitlements"
        const val KEY_TRIAL_USED = "trial_used"
        const val KEY_LIFETIME_UNLOCKED = "lifetime_unlocked"
    }
}
