package com.notesescape.sdocx

internal enum class BillingConnectionState {
    NOT_STARTED,
    CONNECTING,
    READY,
    DISCONNECTED,
    CLOSED
}

internal object BillingConnectionPolicy {
    fun shouldStartConnection(state: BillingConnectionState): Boolean =
        state == BillingConnectionState.NOT_STARTED

    fun canIssueRefreshApiCall(state: BillingConnectionState): Boolean =
        state == BillingConnectionState.READY ||
            state == BillingConnectionState.DISCONNECTED

    fun afterSetup(
        state: BillingConnectionState,
        succeeded: Boolean
    ): BillingConnectionState = when {
        state == BillingConnectionState.CLOSED -> BillingConnectionState.CLOSED
        succeeded -> BillingConnectionState.READY
        else -> BillingConnectionState.NOT_STARTED
    }

    fun afterDisconnect(state: BillingConnectionState): BillingConnectionState =
        if (state == BillingConnectionState.CLOSED) {
            BillingConnectionState.CLOSED
        } else {
            BillingConnectionState.DISCONNECTED
        }
}
