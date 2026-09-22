package com.phronesis.mobile

import android.app.Activity
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.getCustomerInfoWith
import com.revenuecat.purchases.ui.revenuecatui.activity.PaywallActivityLauncher
import com.revenuecat.purchases.ui.revenuecatui.activity.PaywallResultHandler

const val PRO_ENTITLEMENT_ID = "phronesis_pro"

fun checkProAccess(onResult: (Boolean) -> Unit) {
    if (!Purchases.isConfigured) {
        onResult(false)
        return
    }
    Purchases.sharedInstance.getCustomerInfoWith(
        onSuccess = { info ->
            onResult(info.entitlements[PRO_ENTITLEMENT_ID]?.isActive == true)
        },
        onError = { onResult(false) }
    )
}