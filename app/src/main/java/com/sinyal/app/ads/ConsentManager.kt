package com.sinyal.app.ads

import android.app.Activity
import android.content.Context
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.sinyal.app.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ConsentState(
    val canRequestAds: Boolean = false,
    val privacyOptionsRequired: Boolean = false,
    val error: String? = null,
)

/** UMP is the sole source of consent eligibility; no locally inferred consent. */
class ConsentManager(context: Context) {
    private val appContext = context.applicationContext
    private val resources = context.resources
    private val information = UserMessagingPlatform.getConsentInformation(appContext)
    private val mutableState = MutableStateFlow(ConsentState())
    val state = mutableState.asStateFlow()
    private var released = false

    fun gather(activity: Activity) {
        information.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                if (!released && !activity.isDestroyed && !activity.isFinishing) {
                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                        publish(error != null)
                    }
                }
            },
            { publish(true) },
        )
        // Previous-session consent is usable only after requesting this update.
        publish(false)
    }

    fun showPrivacyOptions(activity: Activity) {
        if (released || activity.isFinishing || activity.isDestroyed) return
        // Remove current ad views and cached creatives while choices can change.
        mutableState.value = mutableState.value.copy(canRequestAds = false, error = null)
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error -> publish(error != null) }
    }

    fun release() { released = true }

    private fun publish(failed: Boolean) {
        if (released) return
        mutableState.value = ConsentState(
            canRequestAds = information.canRequestAds(),
            privacyOptionsRequired = information.privacyOptionsRequirementStatus ==
                ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED,
            error = if (failed) resources.getString(R.string.privacy_options_failed) else null,
        )
    }
}
