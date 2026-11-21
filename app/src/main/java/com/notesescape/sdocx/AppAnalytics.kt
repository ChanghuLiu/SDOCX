package com.notesescape.sdocx

import android.content.Context
import android.os.Bundle
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Sends only fixed workflow events. When the developer has not added the app's
 * Firebase configuration, analytics stays inactive and the app works offline.
 */
object AppAnalytics {
    fun log(context: Context, event: String, parameter: String? = null, value: String? = null) {
        val appContext = context.applicationContext
        val firebaseApp = runCatching {
            FirebaseApp.getApps(appContext).firstOrNull() ?: FirebaseApp.initializeApp(appContext)
        }.getOrNull() ?: return
        val analytics = runCatching { FirebaseAnalytics.getInstance(firebaseApp) }.getOrNull() ?: return
        val parameters = Bundle()
        if (parameter != null && value != null) parameters.putString(parameter, value)
        analytics.logEvent(event, parameters)
    }
}
