package com.sidebed.light.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Handles the notification's "Turn off" action and body tap. Both disarm the
 * sidebed light, per the product spec.
 */
class LightActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TURN_OFF) {
            ServiceController.disarm(context.applicationContext)
        }
    }

    companion object {
        const val ACTION_TURN_OFF = "com.sidebed.light.action.TURN_OFF"
    }
}
