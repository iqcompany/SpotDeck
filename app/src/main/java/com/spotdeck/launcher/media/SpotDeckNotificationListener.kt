package com.spotdeck.launcher.media

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class SpotDeckNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "SpotDeckMedia"
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // Required override, no-op for now
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Required override, no-op for now
    }

    override fun onListenerConnected() {
        Log.i(TAG, "NotificationListener connected")
    }

    override fun onListenerDisconnected() {
        Log.i(TAG, "NotificationListener disconnected")
    }
}
