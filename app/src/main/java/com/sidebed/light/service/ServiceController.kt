package com.sidebed.light.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** Single entry point for arming / disarming the bedside light service. */
object ServiceController {

    const val ACTION_ARM = "com.sidebed.light.action.ARM"
    const val ACTION_DISARM = "com.sidebed.light.action.DISARM"
    const val EXTRA_FROM_SCHEDULE = "from_schedule"

    /** Start (or refresh) the foreground service and begin watching for motion. */
    fun arm(context: Context, fromSchedule: Boolean = false) {
        val intent = Intent(context, SidebedLightService::class.java).apply {
            action = ACTION_ARM
            putExtra(EXTRA_FROM_SCHEDULE, fromSchedule)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    /** Stop the service, which turns the light off and cleans up. */
    fun disarm(context: Context) {
        context.stopService(Intent(context, SidebedLightService::class.java))
    }
}
