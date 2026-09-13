package com.sinyal.app.core

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import com.google.android.play.core.review.ReviewManagerFactory
import com.sinyal.app.R

/**
 * Asks for a Play rating without leaving the app.
 *
 * Play decides whether the in-app sheet actually appears — it is quota limited
 * and silently does nothing when the user has rated recently. That is why the
 * flow completing tells us nothing about whether a review happened, and why no
 * reward may ever be tied to it. When the API is unavailable at all, the store
 * listing is opened instead so the button is never dead.
 */
object ReviewLauncher {

    fun request(activity: Activity) {
        val manager = ReviewManagerFactory.create(activity)
        manager.requestReviewFlow()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    manager.launchReviewFlow(activity, task.result)
                } else {
                    openStoreListing(activity)
                }
            }
    }

    fun openStoreListing(activity: Activity) {
        val packageName = activity.packageName
        val playIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=$packageName"),
        )
        try {
            activity.startActivity(playIntent)
        } catch (_: ActivityNotFoundException) {
            activity.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$packageName"),
                ),
            )
        }
    }

    /** Shares the store link, for when someone asks what the app was. */
    fun shareApp(activity: Activity) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_TEXT,
                activity.getString(
                    R.string.share_app_text,
                    "https://play.google.com/store/apps/details?id=${activity.packageName}",
                ),
            )
        }
        activity.startActivity(
            Intent.createChooser(intent, activity.getString(R.string.share_app_chooser)),
        )
    }
}
