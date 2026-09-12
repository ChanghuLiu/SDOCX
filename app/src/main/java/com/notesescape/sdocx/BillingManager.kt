package com.notesescape.sdocx

import android.app.Activity
import android.content.Context
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

    private var started = false
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
        if (billingClient.isReady) {
            _state.value = _state.value.copy(connected = true)
            refreshProductDetails()
            queryActivePurchases()
            return
        }
        if (started) return
        started = true
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    _state.value = _state.value.copy(connected = true)
                    refreshProductDetails()
                    queryActivePurchases()
                } else {
                    started = false
                    _state.value = BillingUiState()
                }
            }

            override fun onBillingServiceDisconnected() {
                started = false
                _state.value = _state.value.copy(connected = false)
            }
        })
    }

    fun refreshPurchases() {
        if (!billingClient.isReady) {
            start()
            return
        }
        queryActivePurchases()
        refreshProductDetails()
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
        if (billingClient.isReady) billingClient.endConnection()
        started = false
    }

    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: List<Purchase>?
    ) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                processPurchases(purchases.orEmpty(), replaceOwnership = false)
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refreshPurchases()
        }
    }

    private fun refreshProductDetails() {
        if (!billingClient.isReady) return
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PRODUCT_LIFETIME_UNLOCK)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(product))
            .build()
        billingClient.queryProductDetailsAsync(params) { billingResult, queryResult ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                productDetails = null
                selectedOfferToken = null
                _state.value = _state.value.copy(
                    purchaseAvailable = false,
                    formattedPrice = null
                )
                return@queryProductDetailsAsync
            }

            val details = queryResult.productDetailsList.firstOrNull {
                it.productId == PRODUCT_LIFETIME_UNLOCK
            }
            val offer = details?.oneTimePurchaseOfferDetailsList.orEmpty().firstOrNull()
            productDetails = details
            selectedOfferToken = offer?.offerToken
            _state.value = _state.value.copy(
                connected = true,
                purchaseAvailable = details != null && offer != null,
                formattedPrice = offer?.formattedPrice
            )
        }
    }

    private fun queryActivePurchases() {
        if (!billingClient.isReady) return
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                processPurchases(purchases, replaceOwnership = true)
            }
        }
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
            entitlementStore.setLifetimeUnlocked(purchased.isNotEmpty())
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
        billingClient.acknowledgePurchase(params) { /* Retry on the next refresh if needed. */ }
    }

    internal companion object {
        const val PRODUCT_LIFETIME_UNLOCK = "lifetime_unlock"
    }
}
