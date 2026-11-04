package io.github.shishirip.aiubnotices

import android.app.Application
import io.github.shishirip.aiubnotices.notify.Notifier

class AiubNoticesApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // The channel must exist before any notification is posted.
        Notifier(this).ensureChannel()
    }
}
