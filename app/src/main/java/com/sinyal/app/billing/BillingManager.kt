package com.sinyal.app.billing

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.sinyal.app.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BillingState(
    val adsRemoved: Boolean = false,
    val price: String? = null,
    val available: Boolean = false,
    val busy: Boolean = false,
    val restoring: Boolean = false,
    val pending: Boolean = false,
    val message: String? = null,
)

/** Successful Play responses reconcile the cache; outages never revoke paid access. */
class BillingManager(
    context: Context,
    cachedAdsRemoved: Boolean = false,
    onEntitlementChanged: (Boolean) -> Unit,
) {
    // Keep the callable property out of the primary constructor: AGP's current
    // experimental-API lint detector cannot resolve its Kotlin 2.4 owner there.
    private val onEntitlementChanged = onEntitlementChanged
    private val appContext = context.applicationContext
    private val resources = context.resources
    private val main = Handler(Looper.getMainLooper())
    private val mutableState = MutableStateFlow(BillingState(adsRemoved = cachedAdsRemoved))
    val state = mutableState.asStateFlow()
    private var productDetails: ProductDetails? = null
    private var selectedOffer: ProductDetails.OneTimePurchaseOfferDetails? = null
    private var connecting = false
    private var released = false
    private var entitlementRevision = 0
    private val acknowledging = mutableSetOf<String>()

    private val client = BillingClient.newBuilder(appContext)
        .enableAutoServiceReconnection()
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .setListener { result, purchases -> dispatch {
            mutableState.value = mutableState.value.copy(busy = false)
            when (result.responseCode) {
                BillingClient.BillingResponseCode.OK -> {
                    purchases.orEmpty().forEach(::handlePurchase)
                    if (purchases.isNullOrEmpty()) queryOwned()
                }
                BillingClient.BillingResponseCode.USER_CANCELED -> message(R.string.billing_cancelled)
                BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> restore()
                else -> message(R.string.billing_purchase_failed)
            }
        } }
        .build()

    fun connect() {
        if (released || connecting) return
        if (client.isReady) {
            refresh()
            return
        }
        connecting = true
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) = dispatch {
                connecting = false
                if (result.responseCode == BillingClient.BillingResponseCode.OK) refresh()
                else {
                    mutableState.value = mutableState.value.copy(available = false, busy = false, restoring = false)
                    message(R.string.billing_unavailable)
                }
            }
            override fun onBillingServiceDisconnected() = dispatch {
                connecting = false
                mutableState.value = mutableState.value.copy(available = false)
            }
        })
    }

    fun restore() {
        if (released) return
        mutableState.value = mutableState.value.copy(restoring = true)
        message(R.string.billing_restoring)
        connect()
    }

    fun release() {
        released = true
        main.removeCallbacksAndMessages(null)
        acknowledging.clear()
        client.endConnection()
    }

    /** Never invent a price or silently drop a click when Play is unavailable. */
    fun purchase(activity: Activity) {
        if (released || activity.isFinishing || activity.isDestroyed || mutableState.value.busy) return
        if (mutableState.value.adsRemoved) {
            message(R.string.billing_removed)
            return
        }
        if (mutableState.value.pending) {
            message(R.string.billing_pending)
            return
        }
        val details = productDetails
        val offer = selectedOffer
        if (!client.isReady || details == null || offer == null) {
            message(R.string.billing_unavailable)
            connect()
            return
        }
        mutableState.value = mutableState.value.copy(busy = true, message = null)
        val product = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            // Legacy single-option products may omit a token; modern offers must pass it.
            .apply { offer.offerToken?.let { setOfferToken(it) } }
            .build()
        val result = client.launchBillingFlow(activity,
            BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(product)).build())
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            mutableState.value = mutableState.value.copy(busy = false)
            if (result.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) restore()
            else {
                message(R.string.billing_purchase_failed)
                queryProduct()
            }
        }
    }

    private fun refresh() {
        queryProduct()
        queryOwned()
    }

    private fun queryProduct() {
        val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(REMOVE_ADS_PRODUCT_ID).setProductType(BillingClient.ProductType.INAPP).build(),
        )).build()
        client.queryProductDetailsAsync(params) { result, queryResult -> dispatch {
            val details = queryResult.productDetailsList.firstOrNull { it.productId == REMOVE_ADS_PRODUCT_ID }
                ?.takeIf { result.responseCode == BillingClient.BillingResponseCode.OK }
            // The Play product must be configured as a permanent Buy option, never a rental.
            val offer = details?.oneTimePurchaseOfferDetailsList?.firstOrNull { it.rentalDetails == null }
                ?: details?.oneTimePurchaseOfferDetails?.takeIf { it.rentalDetails == null }
            productDetails = details
            selectedOffer = offer
            mutableState.value = mutableState.value.copy(available = offer != null, price = offer?.formattedPrice)
            if (offer == null && mutableState.value.message == null) {
                message(if (result.responseCode == BillingClient.BillingResponseCode.OK)
                    R.string.billing_not_registered else R.string.billing_unavailable)
            }
        } }
    }

    private fun queryOwned() {
        val revisionAtQuery = entitlementRevision
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        client.queryPurchasesAsync(params) { result, purchases -> dispatch {
            val succeeded = result.responseCode == BillingClient.BillingResponseCode.OK
            val owned = purchases.any { REMOVE_ADS_PRODUCT_ID in it.products &&
                it.purchaseState == Purchase.PurchaseState.PURCHASED }
            val wasRestoring = mutableState.value.restoring
            val hadPurchaseStatus = mutableState.value.adsRemoved || mutableState.value.pending
            mutableState.value = mutableState.value.copy(restoring = false)
            // A purchase update newer than this query wins over its stale empty response.
            if (revisionAtQuery == entitlementRevision || owned) {
                applyEntitlement(reconcileEntitlement(mutableState.value.adsRemoved, succeeded, owned))
                if (succeeded) {
                    val pending = purchases.any { REMOVE_ADS_PRODUCT_ID in it.products &&
                        it.purchaseState == Purchase.PurchaseState.PENDING }
                    mutableState.value = mutableState.value.copy(pending = pending)
                    if (hadPurchaseStatus && !owned && !pending) {
                        mutableState.value = mutableState.value.copy(message = null)
                    }
                }
            }
            if (!succeeded) {
                message(R.string.billing_unavailable)
                return@dispatch
            }
            if (wasRestoring) message(if (owned) R.string.billing_restored else R.string.billing_nothing_to_restore)
            purchases.forEach(::handlePurchase)
        } }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (REMOVE_ADS_PRODUCT_ID !in purchase.products) return
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PENDING -> {
                entitlementRevision++
                mutableState.value = mutableState.value.copy(pending = true)
                message(R.string.billing_pending)
            }
            Purchase.PurchaseState.PURCHASED -> {
                entitlementRevision++
                mutableState.value = mutableState.value.copy(pending = false)
                applyEntitlement(true)
                if (!purchase.isAcknowledged) acknowledge(purchase.purchaseToken)
                else if (!mutableState.value.restoring) message(R.string.billing_removed)
            }
        }
    }

    private fun acknowledge(token: String, attempt: Int = 0) {
        if (released || (attempt == 0 && !acknowledging.add(token))) return
        val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build()
        client.acknowledgePurchase(params) { result -> dispatch {
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                acknowledging.remove(token)
                message(R.string.billing_removed)
            } else {
                message(R.string.billing_acknowledgment_retry)
                if (attempt < ACK_RETRY_DELAYS.size) {
                    main.postDelayed({ if (!released) acknowledge(token, attempt + 1) }, ACK_RETRY_DELAYS[attempt])
                } else acknowledging.remove(token) // A later foreground query retries unacknowledged ownership.
            }
        } }
    }

    private fun applyEntitlement(owned: Boolean) {
        mutableState.value = mutableState.value.copy(adsRemoved = owned)
        onEntitlementChanged(owned)
    }
    private fun message(resource: Int) {
        mutableState.value = mutableState.value.copy(message = resources.getString(resource))
    }
    private fun dispatch(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            if (!released) block()
        } else main.post { if (!released) block() }
    }
    companion object {
        const val REMOVE_ADS_PRODUCT_ID = "remove_ads"
        private val ACK_RETRY_DELAYS = longArrayOf(10_000, 30_000, 120_000)
    }
}
