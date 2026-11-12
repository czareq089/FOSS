package com.foss.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class RestTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            RestTimerNotificationManager.ACTION_MINUS_30 -> {
                RestTimerNotificationManager.adjustTimer(context, -30)
            }
            RestTimerNotificationManager.ACTION_PLUS_30 -> {
                RestTimerNotificationManager.adjustTimer(context, 30)
            }
        }
    }
}
