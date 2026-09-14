package com.notesescape.sdocx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BillingConnectionPolicyTest {
    @Test
    fun onlyNotStartedMayBeginConnection() {
        assertTrue(
            BillingConnectionPolicy.shouldStartConnection(
                BillingConnectionState.NOT_STARTED
            )
        )
        assertFalse(
            BillingConnectionPolicy.shouldStartConnection(
                BillingConnectionState.CONNECTING
            )
        )
        assertFalse(
            BillingConnectionPolicy.shouldStartConnection(
                BillingConnectionState.DISCONNECTED
            )
        )
        assertFalse(
            BillingConnectionPolicy.shouldStartConnection(
                BillingConnectionState.CLOSED
            )
        )
    }

    @Test
    fun disconnectedUsesApiCallForAutoReconnectWithoutStartingConnection() {
        assertFalse(
            BillingConnectionPolicy.shouldStartConnection(
                BillingConnectionPolicy.afterDisconnect(BillingConnectionState.READY)
            )
        )
        assertTrue(
            BillingConnectionPolicy.canIssueRefreshApiCall(
                BillingConnectionState.DISCONNECTED
            )
        )
    }

    @Test
    fun connectingDoesNotIssueRefreshApiCall() {
        assertFalse(
            BillingConnectionPolicy.canIssueRefreshApiCall(
                BillingConnectionState.CONNECTING
            )
        )
    }

    @Test
    fun setupTransitionsAndClosedStateAreTerminal() {
        assertEquals(
            BillingConnectionState.READY,
            BillingConnectionPolicy.afterSetup(
                BillingConnectionState.CONNECTING,
                succeeded = true
            )
        )
        assertEquals(
            BillingConnectionState.NOT_STARTED,
            BillingConnectionPolicy.afterSetup(
                BillingConnectionState.CONNECTING,
                succeeded = false
            )
        )
        assertEquals(
            BillingConnectionState.CLOSED,
            BillingConnectionPolicy.afterSetup(
                BillingConnectionState.CLOSED,
                succeeded = true
            )
        )
        assertEquals(
            BillingConnectionState.CLOSED,
            BillingConnectionPolicy.afterDisconnect(BillingConnectionState.CLOSED)
        )
    }

    @Test
    fun disconnectedRefreshStartsOneSerializedChain() {
        assertEquals(
            BillingRefreshDecision.START_SERIAL_REFRESH,
            BillingConnectionPolicy.refreshDecision(
                connectionState = BillingConnectionState.DISCONNECTED,
                refreshInFlight = false,
                closed = false
            )
        )
    }

    @Test
    fun secondRefreshWhileInFlightIsCoalesced() {
        assertEquals(
            BillingRefreshDecision.COALESCE,
            BillingConnectionPolicy.refreshDecision(
                connectionState = BillingConnectionState.DISCONNECTED,
                refreshInFlight = true,
                closed = false
            )
        )
    }

    @Test
    fun connectingRefreshWaitsForSetupWithoutStartingAnotherConnection() {
        assertEquals(
            BillingRefreshDecision.DEFER_UNTIL_CONNECTION,
            BillingConnectionPolicy.refreshDecision(
                connectionState = BillingConnectionState.CONNECTING,
                refreshInFlight = false,
                closed = false
            )
        )
    }

    @Test
    fun productDetailsBeginsOnlyAfterSuccessfulPurchaseQueryCallback() {
        assertEquals(
            BillingRefreshStep.QUERY_PRODUCT_DETAILS,
            BillingConnectionPolicy.nextAfterPurchaseQuery(succeeded = true)
        )
        assertEquals(
            BillingRefreshStep.IDLE,
            BillingConnectionPolicy.nextAfterPurchaseQuery(succeeded = false)
        )
    }

    @Test
    fun closedRefreshIsIgnored() {
        assertEquals(
            BillingRefreshDecision.IGNORE_CLOSED,
            BillingConnectionPolicy.refreshDecision(
                connectionState = BillingConnectionState.CLOSED,
                refreshInFlight = false,
                closed = true
            )
        )
    }

    @Test
    fun failedOwnershipQueryKeepsCachedEntitlement() {
        assertTrue(
            BillingOwnershipPolicy.resolveLifetimeUnlocked(
                querySucceeded = false,
                cachedLifetimeUnlocked = true,
                ownedLifetimePurchase = false
            )
        )
    }

    @Test
    fun successfulEmptyOwnershipQueryRevokesStaleEntitlement() {
        assertFalse(
            BillingOwnershipPolicy.resolveLifetimeUnlocked(
                querySucceeded = true,
                cachedLifetimeUnlocked = true,
                ownedLifetimePurchase = false
            )
        )
    }
}
