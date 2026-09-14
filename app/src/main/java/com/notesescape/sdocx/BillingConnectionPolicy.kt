package com.notesescape.sdocx

internal enum class BillingConnectionState {
    NOT_STARTED,
    CONNECTING,
    READY,
    DISCONNECTED,
    CLOSED
}

internal enum class BillingRefreshDecision {
    IGNORE_CLOSED,
    DEFER_UNTIL_CONNECTION,
    COALESCE,
    START_CONNECTION,
    START_SERIAL_REFRESH
}

internal enum class BillingRefreshStep {
    IDLE,
    QUERY_PRODUCT_DETAILS
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

    fun refreshDecision(
        connectionState: BillingConnectionState,
        refreshInFlight: Boolean,
        closed: Boolean
    ): BillingRefreshDecision = when {
        closed || connectionState == BillingConnectionState.CLOSED ->
            BillingRefreshDecision.IGNORE_CLOSED
        refreshInFlight -> BillingRefreshDecision.COALESCE
        connectionState == BillingConnectionState.NOT_STARTED ->
            BillingRefreshDecision.START_CONNECTION
        connectionState == BillingConnectionState.CONNECTING ->
            BillingRefreshDecision.DEFER_UNTIL_CONNECTION
        connectionState == BillingConnectionState.READY ||
            connectionState == BillingConnectionState.DISCONNECTED ->
            BillingRefreshDecision.START_SERIAL_REFRESH
        else -> BillingRefreshDecision.IGNORE_CLOSED
    }

    fun nextAfterPurchaseQuery(succeeded: Boolean): BillingRefreshStep =
        if (succeeded) {
            BillingRefreshStep.QUERY_PRODUCT_DETAILS
        } else {
            // Do not immediately issue another reconnect-triggering call after
            // a transient Billing error. A later foreground refresh may retry.
            BillingRefreshStep.IDLE
        }
}

internal object BillingOwnershipPolicy {
    fun resolveLifetimeUnlocked(
        querySucceeded: Boolean,
        cachedLifetimeUnlocked: Boolean,
        ownedLifetimePurchase: Boolean
    ): Boolean = if (querySucceeded) ownedLifetimePurchase else cachedLifetimeUnlocked
}
