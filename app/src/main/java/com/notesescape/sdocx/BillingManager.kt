package com.notesescape.sdocx

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class BillingUiState(
    val connected: Boolean = false,
    val purchaseAvailable: Boolean = false,
    val formattedPrice: String? = null,
    val pendingPurchase: Boolean = false
)

internal enum class PurchaseLaunchResult {
    LAUNCHED,
    ALREADY_UNLOCKED,
    BILLING_NOT_READY,
    PRODUCT_UNAVAILABLE,
    FAILED
}

internal class BillingManager(
    context: Context,
    private val entitlementStore: EntitlementStore
) : PurchasesUpdatedListener {

    private val _state = MutableStateFlow(BillingUiState())
    val state: StateFlow<BillingUiState> = _state.asStateFlow()

    private var connectionState = BillingConnectionState.NOT_STARTED
    private var closed = false
    private var refreshInFlight = false
    private var refreshRequested = false
    private var productDetails: ProductDetails? = null
    private var selectedOfferToken: String? = null

    private val billingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .enableAutoServiceReconnection()
        .build()

    fun start() {
        refreshBillingState()
    }

    private fun startInitialConnection() {
        if (closed || !BillingConnectionPolicy.shouldStartConnection(connectionState)) return
        connectionState = BillingConnectionState.CONNECTING
        refreshRequested = true
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (closed) return
                Log.d(
                    BILLING_TAG,
                    "Billing setup responseCode=${billingResult.responseCode} " +
                        "debugMessage=${billingResult.debugMessage}"
                )
                connectionState = BillingConnectionPolicy.afterSetup(
                    connectionState,
                    billingResult.responseCode == BillingClient.BillingResponseCode.OK
                )
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    _state.value = _state.value.copy(connected = true)
                    if (refreshRequested) {
                        refreshRequested = false
                        refreshBillingState()
                    }
                } else {
                    refreshRequested = false
                    _state.value = BillingUiState()
                }
            }

            override fun onBillingServiceDisconnected() {
                if (closed) return
                connectionState = BillingConnectionPolicy.afterDisconnect(connectionState)
                _state.value = _state.value.copy(connected = false)
            }
        })
    }

    fun refreshPurchases() {
        refreshBillingState()
    }

    private fun refreshBillingState() {
        if (billingClient.isReady && !closed) {
            connectionState = BillingConnectionState.READY
        }
        when (
            BillingConnectionPolicy.refreshDecision(
                connectionState = connectionState,
                refreshInFlight = refreshInFlight,
                closed = closed
            )
        ) {
            BillingRefreshDecision.IGNORE_CLOSED -> Unit
            BillingRefreshDecision.COALESCE,
            BillingRefreshDecision.DEFER_UNTIL_CONNECTION -> {
                refreshRequested = true
            }
            BillingRefreshDecision.START_CONNECTION -> startInitialConnection()
            BillingRefreshDecision.START_SERIAL_REFRESH -> beginSerializedRefresh()
        }
    }

    private fun beginSerializedRefresh() {
        if (closed || refreshInFlight) return
        refreshInFlight = true
        refreshRequested = false
        queryActivePurchases()
    }

    fun launchLifetimePurchase(activity: Activity): PurchaseLaunchResult {
        if (entitlementStore.current.lifetimeUnlocked) {
            return PurchaseLaunchResult.ALREADY_UNLOCKED
        }
        if (!billingClient.isReady) return PurchaseLaunchResult.BILLING_NOT_READY

        val details = productDetails ?: return PurchaseLaunchResult.PRODUCT_UNAVAILABLE
        val offerToken = selectedOfferToken ?: return PurchaseLaunchResult.PRODUCT_UNAVAILABLE
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(offerToken)
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()
        val result = billingClient.launchBillingFlow(activity, flowParams)
        return if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            PurchaseLaunchResult.LAUNCHED
        } else {
            PurchaseLaunchResult.FAILED
        }
    }

    fun close() {
        if (closed) return
        closed = true
        connectionState = BillingConnectionState.CLOSED
        billingClient.endConnection()
    }

    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: List<Purchase>?
    ) {
        if (closed) return
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                processPurchases(purchases.orEmpty(), replaceOwnership = false)
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refreshBillingState()
        }
    }

    private fun queryProductDetailsAfterPurchases() {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PRODUCT_LIFETIME_UNLOCK)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(product))
            .build()
        billingClient.queryProductDetailsAsync(params) { billingResult, queryResult ->
            if (closed) return@queryProductDetailsAsync
            Log.d(
                BILLING_TAG,
                "ProductDetails responseCode=${billingResult.responseCode} " +
                    "debugMessage=${billingResult.debugMessage}"
            )
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                productDetails = null
                selectedOfferToken = null
                _state.value = _state.value.copy(
                    purchaseAvailable = false,
                    formattedPrice = null
                )
                finishSerializedRefresh(retryRequested = false)
                return@queryProductDetailsAsync
            }

            val details = queryResult.productDetailsList.firstOrNull {
                it.productId == PRODUCT_LIFETIME_UNLOCK
            }
            val offer = details?.oneTimePurchaseOfferDetailsList.orEmpty().firstOrNull()
            productDetails = details
            selectedOfferToken = offer?.offerToken
            Log.d(
                BILLING_TAG,
                "ProductDetails lifetime_unlock found=${details != null} " +
                    "formattedPrice=${offer?.formattedPrice}"
            )
            _state.value = _state.value.copy(
                connected = true,
                purchaseAvailable = details != null && offer != null,
                formattedPrice = offer?.formattedPrice
            )
            finishSerializedRefresh(retryRequested = true)
        }
    }

    private fun queryActivePurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (closed) return@queryPurchasesAsync
            Log.d(
                BILLING_TAG,
                "queryPurchases responseCode=${billingResult.responseCode} " +
                    "debugMessage=${billingResult.debugMessage}"
            )
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                processPurchases(purchases, replaceOwnership = true)
                when (
                    BillingConnectionPolicy.nextAfterPurchaseQuery(succeeded = true)
                ) {
                    BillingRefreshStep.QUERY_PRODUCT_DETAILS ->
                        queryProductDetailsAfterPurchases()
                    BillingRefreshStep.IDLE -> finishSerializedRefresh(retryRequested = true)
                }
            } else {
                finishSerializedRefresh(retryRequested = false)
            }
        }
    }

    private fun finishSerializedRefresh(retryRequested: Boolean) {
        if (closed) {
            refreshInFlight = false
            refreshRequested = false
            return
        }
        refreshInFlight = false
        val shouldRefreshAgain = retryRequested && refreshRequested
        refreshRequested = false
        if (shouldRefreshAgain) refreshBillingState()
    }

    private fun processPurchases(
        purchases: List<Purchase>,
        replaceOwnership: Boolean
    ) {
        val relevant = purchases.filter { PRODUCT_LIFETIME_UNLOCK in it.products }
        val purchased = relevant.filter {
            it.purchaseState == Purchase.PurchaseState.PURCHASED
        }
        val pending = relevant.any {
            it.purchaseState == Purchase.PurchaseState.PENDING
        }

        if (replaceOwnership) {
            entitlementStore.setLifetimeUnlocked(
                BillingOwnershipPolicy.resolveLifetimeUnlocked(
                    querySucceeded = true,
                    cachedLifetimeUnlocked = entitlementStore.current.lifetimeUnlocked,
                    ownedLifetimePurchase = purchased.isNotEmpty()
                )
            )
        } else if (purchased.isNotEmpty()) {
            entitlementStore.setLifetimeUnlocked(true)
        }
        _state.value = _state.value.copy(pendingPurchase = pending)
        purchased.forEach(::acknowledgeIfNeeded)
    }

    private fun acknowledgeIfNeeded(purchase: Purchase) {
        if (purchase.isAcknowledged) return
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.acknowledgePurchase(params) { billingResult ->
            Log.d(
                BILLING_TAG,
                "acknowledgePurchase responseCode=${billingResult.responseCode} " +
                    "debugMessage=${billingResult.debugMessage}"
            )
        }
    }

    internal companion object {
        const val PRODUCT_LIFETIME_UNLOCK = "lifetime_unlock"
        private const val BILLING_TAG = "NotesEscapeBilling"
    }
}
